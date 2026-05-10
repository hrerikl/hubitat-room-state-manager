/**
 * Simple Room State Child v2 - Child App
 *
 * Install as Apps Code:
 *   Name: Simple Room State Child v2
 *   Namespace: lundby
 *
 * Requires driver:
 *   Simple Room Meta Device
 *
 * Model:
 *   Room <Name>          = public room meta-device
 *     switch             = simple Alexa/dashboard control surface; locking preserves current switch state
 *     roomState          = Off | Occupied | Engaged | Locked
 *     lightingIntent     = Off | Courtesy | On; locking preserves current lightingIntent
 *
 *   <Name> Engaged       = user/app-facing child switch
 *   <Name> Locked        = user/app-facing child switch
 *
 * Internal only:
 *   state.occupied       = true/false
 *   state.courtesy       = true/false
 *   state.engaged        = true/false
 *   state.locked         = true/false
 *   state.lastActivityAt = updated even while Locked, but not exposed until unlock
 */

definition(
    name: "Simple Room State Child v2",
    namespace: "lundby",
    author: "Erik Lundby / ChatGPT",
    description: "Reusable room state child app with Room meta-device, Engaged, Locked, and LightingIntent output.",
    category: "Convenience",
    parent: "lundby:Simple Room State Manager v2",
    singleInstance: false,
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "childMainPage", title: "Room State", install: true, uninstall: true) {
        section("Room") {
            input "hubitatRoomId", "enum", title: "Hubitat Room", options: hubitatRoomOptions(), required: false, submitOnChange: true
            input "roomName", "text", title: "Room name override, optional", required: false, submitOnChange: true
            paragraph "Select the Hubitat Room explicitly. If the room name override is blank, the selected Hubitat Room name is used. Creates: Room <name>, plus optional custom labels for Engaged and Locked. Occupied and Courtesy are internal states, not child switches."
        }

        section("Optional custom labels") {
            input "engagedLabel", "text", title: "Engaged device label, optional. Example: Focus Mode", required: false
            input "lockedLabel", "text", title: "Locked device label, optional. Example: Recording", required: false
        }

        section("Inputs: direct activity") {
            input "motionSensors", "capability.motionSensor", title: "Motion sensors", multiple: true, required: false
            input "doorContactSensors", "capability.contactSensor", title: "Door contact sensors. Opening a door counts as occupancy evidence.", multiple: true, required: false
            input "activitySwitches", "capability.switch", title: "Switches that imply room activity when turned on", multiple: true, required: false
            input "engagementSwitches", "capability.switch", title: "Switches that imply engaged state when turned on", multiple: true, required: false
            input "engageOnMotionWithDoorsClosed", "bool", title: "Engage on motion with doors closed", defaultValue: false, required: true
            paragraph "When enabled, motion marks the room Engaged if every configured door contact is closed. Opening any configured door clears Engaged and still counts as occupancy evidence."
        }

        section("Inputs: neighbor/courtesy") {
            input "neighborRoomDevices", "capability.switch", title: "Neighbor Room devices that should trigger courtesy lighting", multiple: true, required: false
            paragraph "Select other Room meta-devices. Only devices exposing roomState or lightingIntent attributes are used internally. Neighbor rooms trigger Courtesy only when their roomState is Occupied or Engaged. Locked rooms do not propagate Courtesy."
            input "addThisRoomToSelectedNeighborsNow", "button", title: "Add this room back to selected neighbors"
            paragraph "One-time setup helper: for each selected neighbor room, attempts to add this room's meta-device to that room's neighbor list. Each child app still owns its own neighbor list after the shortcut runs."
        }

        section("Inputs: external lock") {
            input "externalLockedSwitches", "capability.switch", title: "External switches that should lock this room", multiple: true, required: false
        }

        section("Timeouts") {
            input "occupiedTimeoutMinutes", "number", title: "Occupied timeout after no motion", defaultValue: 5, required: true
            input "engagedTimeoutMinutes", "number", title: "Engaged timeout after no activity", defaultValue: 30, required: true
            input "lockAutoClearMinutes", "number", title: "Auto-clear Locked after X minutes. Blank or 0 disables.", required: false
            input "unlockImpliesActivity", "bool", title: "Treat unlock as occupancy activity", defaultValue: false, required: true
        }


        section("Child devices") {
            input "createDevicesNow", "bool", title: "Create/update child devices on save", defaultValue: true, required: true
        }

        section("Hubitat Room assignment") {
            paragraph "Optional helper. After child devices are created, use this button to try assigning app-created devices to the selected Hubitat Room. This uses Hubitat room APIs that may vary by platform version."
            input "assignToHubitatRoomNow", "button", title: "Assign app devices to Hubitat Room"
        }

        section("Debug") {
            input "debugLogging", "bool", title: "Enable debug logging", defaultValue: true, required: true
        }
    }
}

// -------------------- App Buttons --------------------

def appButtonHandler(btn) {
    if (btn == "assignToHubitatRoomNow") {
        assignChildDevicesToHubitatRoom()
    }
    if (btn == "addThisRoomToSelectedNeighborsNow") {
        try {
            parent.addThisRoomToSelectedNeighbors(app.id)
        } catch (Exception e) {
            log.warn "${roomDeviceLabel()}: Reciprocal neighbor setup failed: ${e.message}"
        }
    }
}

// -------------------- Lifecycle --------------------

def installed() {
    log.info "Installed ${app.label}"
    initializeChild()
}

def updated() {
    log.info "Updated ${app.label}"
    unsubscribe()
    unschedule()
    initializeChild()
}

def initializeChild() {
    updateChildAppLabel()

    if (createDevicesNow == null || createDevicesNow) {
        createOrUpdateChildDevices()
    }

    ensureInitialState()

    subscribe(roomDevice(), "switch.on", roomSwitchOnHandler)
    subscribe(roomDevice(), "switch.off", roomSwitchOffHandler)

    subscribe(engagedDevice(), "switch.on", engagedOnHandler)
    subscribe(engagedDevice(), "switch.off", engagedOffHandler)

    subscribe(lockedDevice(), "switch.on", lockedOnHandler)
    subscribe(lockedDevice(), "switch.off", lockedOffHandler)

    subscribe(motionSensors, "motion.active", motionActiveHandler)
    subscribe(motionSensors, "motion.inactive", motionInactiveHandler)
    subscribe(doorContactSensors, "contact.open", doorOpenHandler)
    subscribe(doorContactSensors, "contact.closed", doorClosedHandler)

    subscribe(activitySwitches, "switch.on", activitySwitchOnHandler)
    subscribe(engagementSwitches, "switch.on", engagementSwitchOnHandler)

    subscribe(selectedNeighborRoomDevices(), "roomState", neighborRoomHandler)
    subscribe(selectedNeighborRoomDevices(), "switch", neighborRoomHandler)
    subscribe(externalLockedSwitches, "switch", externalLockHandler)

    refreshDerivedStates()
    recomputeAndPublish()
}

private void ensureInitialState() {
    if (state.occupied == null) state.occupied = false
    if (state.courtesy == null) state.courtesy = false
    if (state.engaged == null) state.engaged = isOn(engagedDevice())
    if (state.locked == null) state.locked = isOn(lockedDevice())
    if (state.roomState == null) state.roomState = "Off"
    if (state.lightingIntent == null) state.lightingIntent = "Off"
    if (state.lastActivityAt == null) state.lastActivityAt = null
}

// -------------------- Naming --------------------

private Map hubitatRoomOptions() {
    try {
        def rooms = getRooms()
        if (!rooms) return [:]
        return rooms.collectEntries { room ->
            [("${room.id}".toString()): room.name?.toString()]
        }
    } catch (Exception e) {
        log.warn "${app.label}: Could not load Hubitat Room list: ${e.message}"
        return [:]
    }
}

private String safeRoomName() {
    String name = roomName?.trim()

    if (!name && hubitatRoomId) {
        def selectedRoom = selectedHubitatRoom()
        name = selectedRoom?.name?.trim()
    }

    if (!name) {
        name = app.label ?: "Room"
    }

    name = name.trim()
    if (name.toLowerCase().startsWith("room ")) {
        name = name.substring(5).trim()
    }
    return name ?: "Room"
}

private def selectedHubitatRoom() {
    if (!hubitatRoomId) return null

    try {
        return getRooms()?.find { "${it.id}" == "${hubitatRoomId}" }
    } catch (Exception e) {
        log.warn "${app.label}: Could not look up selected Hubitat Room ${hubitatRoomId}: ${e.message}"
        return null
    }
}

private String roomDeviceLabel() {
    return "Room ${safeRoomName()}"
}

private String labelFor(String deviceName) {
    if (deviceName == "Room") {
        return roomDeviceLabel()
    }
    if (deviceName == "Engaged") {
        return engagedLabel?.trim() ? engagedLabel.trim() : "${safeRoomName()} Engaged"
    }
    if (deviceName == "Locked") {
        return lockedLabel?.trim() ? lockedLabel.trim() : "${safeRoomName()} Locked"
    }
    return "${safeRoomName()} ${deviceName}"
}

private void updateChildAppLabel() {
    String desired = roomDeviceLabel()
    if (app.label != desired) {
        app.updateLabel(desired)
    }
}

// -------------------- Hubitat Room Assignment --------------------

private void assignChildDevicesToHubitatRoom() {
    try {
        def room = selectedHubitatRoom()

        if (!room) {
            log.warn "${roomDeviceLabel()}: No Hubitat Room selected. Select a Hubitat Room in the app, then press the assignment button again."
            return
        }

        [roomDevice(), engagedDevice(), lockedDevice()].findAll { it != null }.each { dev ->
            try {
                dev.setRoom(room.id)
                log.info "${roomDeviceLabel()}: Assigned ${dev.displayName} to Hubitat Room ${room.name}"
            } catch (Exception deviceException) {
                log.warn "${roomDeviceLabel()}: Could not assign ${dev.displayName} to Hubitat Room ${room.name}: ${deviceException.message}"
            }
        }
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Hubitat Room assignment failed: ${e.message}"
    }
}

// -------------------- Child Devices --------------------

private String dniFor(String deviceName) {
    return "${app.id}-${deviceName}"
}

private void createOrUpdateChildDevices() {
    createOrUpdateRoomDevice()
    createOrUpdateVirtualSwitch("Engaged")
    createOrUpdateVirtualSwitch("Locked")
}

private void createOrUpdateRoomDevice() {
    String dni = dniFor("Room")
    def child = getChildDevice(dni)
    String desiredLabel = labelFor("Room")

    if (!child) {
        child = addChildDevice("lundby", "Simple Room Meta Device", dni, [
            name: desiredLabel,
            label: desiredLabel,
            isComponent: false
        ])
        log.info "Created room meta-device: ${desiredLabel}"
    } else if (child.label != desiredLabel) {
        child.setLabel(desiredLabel)
        log.info "Updated room meta-device label: ${desiredLabel}"
    }
}

private void createOrUpdateVirtualSwitch(String deviceName) {
    String dni = dniFor(deviceName)
    def child = getChildDevice(dni)
    String desiredLabel = labelFor(deviceName)

    if (!child) {
        child = addChildDevice("hubitat", "Virtual Switch", dni, [
            name: desiredLabel,
            label: desiredLabel,
            isComponent: false
        ])
        log.info "Created child switch: ${desiredLabel}"
    } else if (child.label != desiredLabel) {
        child.setLabel(desiredLabel)
        log.info "Updated child switch label: ${desiredLabel}"
    }
}

private def roomDevice()    { getChildDevice(dniFor("Room")) }
private def engagedDevice() { getChildDevice(dniFor("Engaged")) }
private def lockedDevice()  { getChildDevice(dniFor("Locked")) }

private List selectedNeighborRoomDevices() {
    if (!neighborRoomDevices) return []

    List devices = neighborRoomDevices instanceof List ? neighborRoomDevices : [neighborRoomDevices]

    return devices.findAll { dev ->
        try {
            dev?.currentValue("roomState") != null || dev?.currentValue("lightingIntent") != null
        } catch (Exception ignored) {
            false
        }
    }
}


// -------------------- Parent Setup Helper API --------------------
// These methods are called by the parent app for the optional one-time
// "Add this room back to selected neighbors" setup shortcut.

def getManagedRoomDeviceId() {
    return roomDevice()?.id?.toString()
}

def getManagedRoomDeviceLabel() {
    return roomDevice()?.displayName ?: roomDeviceLabel()
}

def getSelectedNeighborRoomDeviceIds() {
    return selectedNeighborRoomDevices().collect { it.id?.toString() }.findAll { it }
}

def addNeighborRoomDeviceById(String newNeighborDeviceId) {
    if (!newNeighborDeviceId) return false

    List existingIds = []
    if (neighborRoomDevices) {
        List devices = neighborRoomDevices instanceof List ? neighborRoomDevices : [neighborRoomDevices]
        existingIds = devices.collect { it.id?.toString() }.findAll { it }
    }

    if (existingIds.contains(newNeighborDeviceId.toString())) {
        debug "Reciprocal neighbor already present: ${newNeighborDeviceId}"
        return false
    }

    existingIds << newNeighborDeviceId.toString()

    try {
        app.updateSetting("neighborRoomDevices", [type: "capability.switch", value: existingIds])
        debug "Added reciprocal neighbor device id ${newNeighborDeviceId}"
        unsubscribe()
        unschedule()
        initializeChild()
        return true
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not update reciprocal neighbor list: ${e.message}"
        return false
    }
}


private Boolean isOn(def dev) {
    return dev?.currentSwitch == "on"
}

private void childOn(def dev) {
    if (dev && dev.currentSwitch != "on") {
        dev.on()
    }
}

private void childOff(def dev) {
    if (dev && dev.currentSwitch != "off") {
        dev.off()
    }
}

// -------------------- Event Handlers --------------------

def roomSwitchOnHandler(evt) {
    debug "Room switch ON"
    setOccupied("room switch on")
}

def roomSwitchOffHandler(evt) {
    debug "Room switch OFF"
    clearRoomStateFromRoomSwitch()
}

def engagedOnHandler(evt) {
    debug "Engaged ON"
    setEngaged("engaged switch on")
}

def engagedOffHandler(evt) {
    debug "Engaged OFF"
    state.engaged = false
    scheduleOccupiedTimeout("engaged turned off")
    recomputeAndPublish()
}

def lockedOnHandler(evt) {
    debug "Locked ON"
    setLocked(true, "locked switch on")
}

def lockedOffHandler(evt) {
    debug "Locked OFF"
    setLocked(false, "locked switch off")
}

def motionActiveHandler(evt) {
    debug "Motion active: ${evt.displayName}"

    if (state.locked) {
        recordLatentActivity("motion active while locked")
        return
    }

    if (engageOnMotionWithDoorsClosed && allDoorsClosed()) {
        setEngaged("motion active with all doors closed")
    } else {
        setOccupied("motion active")
    }
}

def motionInactiveHandler(evt) {
    debug "Motion inactive: ${evt.displayName}"

    if (state.locked) {
        debug "Ignoring motion inactive timeout processing because room is locked"
        return
    }

    scheduleOccupiedTimeout("motion inactive")
}

def doorOpenHandler(evt) {
    debug "Door open: ${evt.displayName}"

    if (state.locked) {
        recordLatentActivity("door opened while locked")
        return
    }

    if (engageOnMotionWithDoorsClosed && state.engaged) {
        debug "Clearing engaged because a door opened"
        state.engaged = false
        childOff(engagedDevice())
    }

    setOccupied("door opened")
}

def doorClosedHandler(evt) {
    debug "Door closed: ${evt.displayName}"

    if (state.locked) {
        debug "Ignoring door closed because room is locked"
        return
    }

    // Door closing is not treated as occupancy by itself.
}

def activitySwitchOnHandler(evt) {
    debug "Activity switch on: ${evt.displayName}"

    if (state.locked) {
        recordLatentActivity("activity switch on while locked")
        return
    }

    setOccupied("activity switch on")
}

def engagementSwitchOnHandler(evt) {
    debug "Engagement switch on: ${evt.displayName}"

    if (state.locked) {
        recordLatentActivity("engagement switch on while locked")
        return
    }

    setEngaged("engagement switch on")
}

def neighborRoomHandler(evt) {
    debug "Neighbor room event ${evt.name}=${evt.value}: ${evt.displayName}"

    if (state.locked) {
        debug "Ignoring neighbor/courtesy event because room is locked"
        return
    }

    refreshCourtesyState()
    recomputeAndPublish()
}

def externalLockHandler(evt) {
    debug "External lock switch ${evt.value}: ${evt.displayName}"

    if (anyExternalLockOn()) {
        childOn(lockedDevice())
        setLocked(true, "external lock on")
    } else {
        childOff(lockedDevice())
        setLocked(false, "external lock off")
    }
}

// -------------------- State Actions --------------------

private void recordActivity(String reason) {
    Long timestamp = now()
    state.lastActivityAt = timestamp
    state.lastInactiveAt = timestamp
    publishPresenceActivity(timestamp, reason)
    debug "Activity recorded at ${timestamp}: ${reason}"
    scheduleOccupiedTimeout(reason)
}

private void recordLatentActivity(String reason) {
    Long timestamp = now()
    state.lastActivityAt = timestamp
    state.lastInactiveAt = timestamp
    publishPresenceActivity(timestamp, reason)
    debug "Latent activity recorded at ${timestamp}: ${reason}"
}

private void publishPresenceActivity(Long timestamp, String reason = "") {
    def dev = roomDevice()
    if (!dev || !timestamp) return

    try {
        dev.recordPresenceActivity("${timestamp}")
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not publish presence activity (${reason}): ${e.message}"
    }
}

private Boolean hasRecentActivityWithinOccupiedTimeout() {
    Long lastActivity = (state.lastActivityAt ?: state.lastInactiveAt) as Long
    if (!lastActivity) return false
    Long elapsedMs = now() - lastActivity
    Long requiredMs = occupiedTimeoutSeconds() * 1000L
    return elapsedMs <= requiredMs
}

private void setOccupied(String reason) {
    debug "Occupied true: ${reason}"
    recordActivity(reason)
    state.occupied = true
    recomputeAndPublish()
}

private void setEngaged(String reason) {
    debug "Engaged true: ${reason}"
    unschedule(clearEngagedIfStillInactive)
    state.lastEngagedInactiveAt = now()

    recordActivity(reason)

    state.occupied = true
    state.engaged = true
    childOn(engagedDevice())

    scheduleEngagedTimeout("engaged on")
    recomputeAndPublish()
}

private void setLocked(Boolean locked, String reason) {
    debug "Locked ${locked}: ${reason}"

    state.locked = locked

    if (locked) {
        // Locked means: preserve the room as-is and stop automation from changing it.
        // Keep recording latent activity, but do not expose that as roomState/lightingIntent until unlock.
        unschedule(clearOccupiedIfStillInactive)
        unschedule(clearEngagedIfStillInactive)
        unschedule(autoClearLock)
        childOn(lockedDevice())
        scheduleLockAutoClear()
    } else {
        unschedule(autoClearLock)
        childOff(lockedDevice())

        // On unlock, optionally stamp activity now, then restore Occupied
        // only if last activity is still within the normal occupied timeout.
        if (unlockImpliesActivity) {
            recordLatentActivity("unlock implies activity")
        }

        refreshCourtesyState()

        if (hasRecentActivityWithinOccupiedTimeout()) {
            state.occupied = true
            if (!state.engaged) {
                scheduleOccupiedTimeout("locked cleared with recent activity")
            }
        } else {
            state.occupied = false
            state.engaged = false
            childOff(engagedDevice())
            state.lastInactiveAt = null
            state.lastActivityAt = null
            state.lastEngagedInactiveAt = null
        }
    }

    recomputeAndPublish()
}

private void clearRoomStateFromRoomSwitch() {
    debug "Clearing room state from room switch"

    unschedule(clearOccupiedIfStillInactive)
    unschedule(clearEngagedIfStillInactive)
    unschedule(autoClearLock)
    state.lastInactiveAt = null
    state.lastActivityAt = null
    state.lastEngagedInactiveAt = null

    state.occupied = false
    state.engaged = false
    state.locked = false
    childOff(engagedDevice())
    childOff(lockedDevice())

    refreshCourtesyState()
    recomputeAndPublish()
}

// -------------------- Timeout Logic --------------------

private Integer occupiedTimeoutSeconds() {
    return Math.max(((occupiedTimeoutMinutes ?: 5) as Integer) * 60, 1)
}

private Integer engagedTimeoutSeconds() {
    return Math.max(((engagedTimeoutMinutes ?: 30) as Integer) * 60, 1)
}

private Integer lockAutoClearSeconds() {
    Integer minutes = (lockAutoClearMinutes ?: 0) as Integer
    return minutes > 0 ? minutes * 60 : 0
}

private void scheduleOccupiedTimeout(String reason) {
    Long lastActivity = (state.lastActivityAt ?: state.lastInactiveAt ?: now()) as Long
    Long targetTime = lastActivity + (occupiedTimeoutSeconds() * 1000L)
    Integer seconds = Math.max(Math.ceil((targetTime - now()) / 1000.0) as Integer, 1)

    debug "Scheduling occupied timeout in ${seconds} seconds based on last activity: ${reason}"
    runIn(seconds, clearOccupiedIfStillInactive, [overwrite: true])
}

private void scheduleEngagedTimeout(String reason) {
    state.lastEngagedInactiveAt = now()
    Integer seconds = engagedTimeoutSeconds()
    debug "Scheduling engaged timeout in ${seconds} seconds: ${reason}"
    runIn(seconds, clearEngagedIfStillInactive, [overwrite: true])
}

private void scheduleLockAutoClear() {
    Integer seconds = lockAutoClearSeconds()
    if (seconds > 0) {
        debug "Scheduling lock auto-clear in ${seconds} seconds"
        runIn(seconds, autoClearLock, [overwrite: true])
    }
}

def autoClearLock() {
    if (!state.locked) {
        debug "Lock auto-clear skipped: already unlocked"
        return
    }

    debug "Lock auto-clear firing"
    childOff(lockedDevice())
    setLocked(false, "lock auto-cleared")
}

def clearOccupiedIfStillInactive() {
    if (state.locked) {
        debug "Occupied timeout blocked: locked is true"
        return
    }

    if (state.engaged) {
        debug "Occupied timeout blocked: engaged is true"
        return
    }

    if (anyMotionActive()) {
        debug "Occupied timeout blocked: motion still active"
        state.lastInactiveAt = null
        return
    }

    Long lastActivity = (state.lastActivityAt ?: state.lastInactiveAt) as Long
    if (!lastActivity) {
        debug "Occupied timeout blocked: no last activity timestamp"
        return
    }

    Long elapsedMs = now() - lastActivity
    Long requiredMs = occupiedTimeoutSeconds() * 1000L

    if (elapsedMs < requiredMs) {
        Integer remainingSeconds = Math.max(Math.ceil((requiredMs - elapsedMs) / 1000.0) as Integer, 1)
        debug "Occupied timeout early. Rescheduling for ${remainingSeconds} seconds based on last activity"
        runIn(remainingSeconds, clearOccupiedIfStillInactive, [overwrite: true])
        return
    }

    state.lastInactiveAt = null
    state.lastActivityAt = null
    state.occupied = false
    recomputeAndPublish()
}

def clearEngagedIfStillInactive() {
    if (state.locked) {
        debug "Engaged timeout blocked: locked is true"
        return
    }

    if (anyMotionActive()) {
        debug "Engaged timeout blocked: motion still active; rescheduling"
        scheduleEngagedTimeout("motion still active")
        return
    }

    Long lastInactive = state.lastEngagedInactiveAt as Long
    if (!lastInactive) {
        debug "Engaged timeout blocked: no inactive timestamp"
        return
    }

    Long elapsedMs = now() - lastInactive
    Long requiredMs = engagedTimeoutSeconds() * 1000L

    if (elapsedMs < requiredMs) {
        Integer remainingSeconds = Math.max(Math.ceil((requiredMs - elapsedMs) / 1000.0) as Integer, 1)
        debug "Engaged timeout stale/early. Rescheduling for ${remainingSeconds} seconds"
        runIn(remainingSeconds, clearEngagedIfStillInactive, [overwrite: true])
        return
    }

    state.lastEngagedInactiveAt = null
    state.engaged = false
    childOff(engagedDevice())
    scheduleOccupiedTimeout("engaged cleared")
    recomputeAndPublish()
}

private Boolean anyMotionActive() {
    return motionSensors?.any { it.currentMotion == "active" } ?: false
}

private Boolean allDoorsClosed() {
    if (!doorContactSensors) {
        debug "allDoorsClosed is false because no door contact sensors are configured"
        return false
    }

    return doorContactSensors.every { it.currentContact == "closed" }
}

// -------------------- Derived States --------------------

private void refreshDerivedStates() {
    refreshLockedState()
    refreshCourtesyState()
}

private Boolean anyExternalLockOn() {
    return externalLockedSwitches?.any { it.currentSwitch == "on" } ?: false
}

private void refreshLockedState() {
    if (anyExternalLockOn()) {
        childOn(lockedDevice())
        setLocked(true, "refresh locked state from external lock")
    } else {
        setLocked(isOn(lockedDevice()), "refresh locked state")
    }
}

private void refreshCourtesyState() {
    Boolean anyNeighborActive = selectedNeighborRoomDevices().any { dev ->
        String neighborState = dev.currentValue("roomState")
        neighborState in ["Occupied", "Engaged"]
    }

    state.courtesy = anyNeighborActive
    debug "Courtesy ${state.courtesy ? 'true' : 'false'} from neighbor Room devices"
}

// -------------------- State Computation and Output --------------------

private String computeRoomState() {
    if (state.locked) return "Locked"
    if (state.engaged) return "Engaged"
    if (state.occupied) return "Occupied"
    return "Off"
}

private String computeLightingIntent(String roomState) {
    if (roomState in ["Locked", "Engaged", "Occupied"]) return "On"
    if (state.courtesy) return "Courtesy"
    return "Off"
}

private void recomputeAndPublish() {
    ensureInitialState()

    String previousLightingIntent = state.lightingIntent ?: "Off"
    Boolean previousSwitchOn = roomDevice()?.currentSwitch == "on"

    String newRoomState = computeRoomState()
    String newLightingIntent = state.locked ? previousLightingIntent : computeLightingIntent(newRoomState)

    // Locking should not change the public switch. It is a freeze/hold state, not an on/off request.
    Boolean roomSwitchShouldBeOn = state.locked ? previousSwitchOn : (newRoomState in ["Occupied", "Engaged"])

    publishRoomDevice(roomSwitchShouldBeOn, newRoomState, newLightingIntent)

    if (state.roomState != newRoomState || state.lightingIntent != newLightingIntent) {
        log.info "${roomDeviceLabel()}: roomState=${newRoomState}, lightingIntent=${newLightingIntent}"
    }

    state.roomState = newRoomState
    state.lightingIntent = newLightingIntent
}

private void publishRoomDevice(Boolean switchOn, String roomState, String lightingIntent) {
    def dev = roomDevice()
    if (!dev) return

    try {
        dev.setRoomState(roomState)
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not set roomState on room device: ${e.message}"
    }

    try {
        dev.setLightingIntent(lightingIntent)
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not set lightingIntent on room device: ${e.message}"
    }

    try {
        dev.setSwitchState(switchOn ? "on" : "off")
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not set switch state on room device: ${e.message}"
    }
}


// -------------------- Logging --------------------

private void debug(String msg) {
    if (debugLogging) {
        log.debug "${roomDeviceLabel()}: ${msg}"
    }
}