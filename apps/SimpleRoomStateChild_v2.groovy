/**
 * Simple Room State Child v2 - Child App
 *
 * Install as Apps Code:
 *   Name: Simple Room State Child v2
 *   Namespace: lundby
 *
 * Requires drivers:
 *   Simple Room Meta Device
 *   Simple Room Meta Light Device
 *
 * Model:
 *   Room <Name>          = public room meta-device
 *     switch             = simple Alexa/dashboard control surface; locking preserves current switch state
 *     level              = room-level virtual lighting level for dashboard/voice/control use
 *     roomState          = Off | Occupied | Engaged | Locked
 *     lightingIntent     = Off | Courtesy | On; locking preserves current lightingIntent
 *
 *   Room <Name> MetaLight = component child of the Room meta-device
 *     switch/level       = On at room level when occupied/engaged, On at courtesy level when courtesy is active
 *
 *   Room <Name> Courtesy = component child of the Room meta-device
 *     switch             = enables/disables courtesy lighting from neighboring rooms
 *   Room <Name> Engaged = component child of the Room meta-device
 *   Room <Name> Locked = component child of the Room meta-device
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
            input "roomProfile", "enum", title: "Room profile", options: roomProfileOptions(), defaultValue: "standard", required: true, submitOnChange: true
            paragraph "Select the Hubitat Room explicitly. If the room name override is blank, the selected Hubitat Room name is used. Creates: Room <name>, plus MetaLight, Courtesy, Engaged, Asleep, and Locked component devices."
        }

        section("Optional custom labels") {
            input "engagedLabel", "text", title: "Engaged device label, optional. Example: Focus Mode", required: false
            input "asleepLabel", "text", title: "Asleep device label, optional. Example: Sleeping", required: false
            input "lockedLabel", "text", title: "Locked device label, optional. Example: Recording", required: false
        }

        section("Inputs: direct activity") {
            input "motionSensors", "capability.motionSensor", title: "Motion sensors", multiple: true, required: false
            input "doorContactSensors", "capability.contactSensor", title: "Door contact sensors. Opening a door counts as occupancy evidence.", multiple: true, required: false
            input "activitySwitches", "capability.switch", title: "Switches that imply room activity when turned on", multiple: true, required: false
            input "activitySwitchesPhysicalOnly", "bool", title: "Only physical activity switch events imply room activity", defaultValue: false, required: true
            input "activityButtons", "capability.pushableButton", title: "Buttons that imply room activity when pushed", multiple: true, required: false
            input "activityButtonNumbers", "text", title: "Activity button numbers, comma separated. Blank means any pushed button.", required: false
            input "lockHeldButtonNumber", "number", title: "Held button number that locks this room. Blank disables.", required: false
            input "unlockHeldButtonNumber", "number", title: "Held button number that unlocks this room. Blank disables.", required: false
            input "engagementSwitches", "capability.switch", title: "Switches that imply engaged state when turned on", multiple: true, required: false
            input "engageOnMotionWithDoorsClosed", "bool", title: "Engage on motion with doors closed", defaultValue: false, required: true
            paragraph "When enabled, motion marks the room Engaged if every configured door contact is closed. Opening any configured door clears Engaged and still counts as occupancy evidence."
        }

        section("Inputs: neighbor/courtesy") {
            input "neighborChildAppIds", "enum", title: "Neighbor rooms that should trigger courtesy lighting", options: safeNeighborRoomOptions(), multiple: true, required: false
            paragraph "Select other Simple Room State rooms. Neighbor rooms trigger Courtesy only when their roomState is Occupied or Engaged. Locked rooms do not propagate Courtesy."
            input "syncReciprocalNeighborsOnSave", "bool", title: "Add this room back to selected neighbors on save", defaultValue: false, required: true
            paragraph "When enabled, saving this room attempts to add it to each selected neighbor's courtesy list. Each child app still owns its own neighbor list."
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

        if (bedroomProfile()) {
            section("Bedroom sleep") {
                input "nightMotionSensors", "capability.motionSensor", title: "Motion sensors that trigger Night lighting while Asleep", multiple: true, required: false
                input "nightLightingTimeoutMinutes", "number", title: "Night lighting timeout minutes", defaultValue: 5, required: true
                input "nightLightingLevel", "number", title: "Night lighting level", defaultValue: 10, required: true
            }
        }

        section("Room lighting levels") {
            input "occupiedLightingLevel", "number", title: "Default occupied lighting level. Blank uses the last on level.", required: false
            input "courtesyLightingLevel", "number", title: "Courtesy/convenience lighting level", defaultValue: 20, required: true
            input "useModeBasedLightingLevels", "bool", title: "Use Location Mode based lighting levels", defaultValue: false, required: true, submitOnChange: true

            if (useModeBasedLightingLevels) {
                input "changeLightingLevelOnModeChange", "bool", title: "Change level on Location Mode change", defaultValue: false, required: true

                locationModeNames().each { modeName ->
                    input modeLevelSettingName("occupiedLightingLevel", modeName), "number", title: "Occupied level - ${modeName}. Blank uses default.", required: false
                    input modeLevelSettingName("courtesyLightingLevel", modeName), "number", title: "Courtesy level - ${modeName}. Blank uses default.", required: false
                }
            }
        }


        section("Child devices") {
            input "createDevicesNow", "bool", title: "Create/update child devices on save", defaultValue: true, required: true
        }

        section("Hubitat Room assignment") {
            paragraph "Optional helper. When enabled, saving this room attempts to assign app-created devices to the selected Hubitat Room, then clears this option."
            input "assignToHubitatRoomOnSave", "bool", title: "Assign app devices to Hubitat Room on save", defaultValue: false, required: true
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
        syncReciprocalNeighbors()
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

def reinitializeFromParent() {
    log.info "Reinitializing ${app.label} from parent"
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

    if (syncReciprocalNeighborsOnSave) {
        syncReciprocalNeighbors()
        clearSyncReciprocalNeighborsOnSave()
    }

    if (assignToHubitatRoomOnSave) {
        assignChildDevicesToHubitatRoom()
        clearAssignToHubitatRoomOnSave()
    }

    subscribe(roomDevice(), "switch.on", roomSwitchOnHandler)
    subscribe(roomDevice(), "switch.off", roomSwitchOffHandler)
    subscribe(roomDevice(), "level", roomLevelHandler)

    subscribe(roomDevice(), "courtesyEnabled", courtesyEnabledHandler)
    subscribe(roomDevice(), "engagedEnabled", engagedEnabledHandler)
    subscribe(roomDevice(), "asleepEnabled", asleepEnabledHandler)
    subscribe(roomDevice(), "lockedEnabled", lockedEnabledHandler)
    subscribe(location, "mode", locationModeHandler)

    subscribe(motionSensors, "motion.active", motionActiveHandler)
    subscribe(motionSensors, "motion.inactive", motionInactiveHandler)
    subscribe(nightMotionSensors, "motion.active", nightMotionActiveHandler)
    subscribe(doorContactSensors, "contact.open", doorOpenHandler)
    subscribe(doorContactSensors, "contact.closed", doorClosedHandler)

    subscribe(activitySwitches, "switch.on", activitySwitchOnHandler)
    subscribe(activityButtons, "pushed", activityButtonPushedHandler)
    subscribe(activityButtons, "held", activityButtonHeldHandler)
    subscribe(engagementSwitches, "switch.on", engagementSwitchOnHandler)

    List neighborDevices = selectedNeighborRoomDevices()
    debugNeighborResolution(neighborDevices)
    subscribe(neighborDevices, "roomState", neighborRoomHandler)
    subscribe(externalLockedSwitches, "switch", externalLockHandler)
    subscribe(parent.recoveryDevice(), "switch.on", recoverSimpleHomeHandler)

    refreshDerivedStates()
    reconcileTimeoutsAfterInitialize()
    recomputeAndPublish()
}

def recoverSimpleHomeHandler(evt) {
    debug "Recover Simple Home requested"
    refreshDerivedStates()
    reconcileTimeoutsAfterInitialize()
    recomputeAndPublish()
}

def activateNightLightingFromDevice(Integer timeoutMinutes) {
    Integer seconds = timeoutMinutes && timeoutMinutes > 0 ? timeoutMinutes * 60 : nightLightingTimeoutSeconds()
    activateNightLighting(seconds, "room device command")
}

def clearNightLightingFromDevice() {
    clearNightLighting("room device command")
}

private void ensureInitialState() {
    if (state.occupied == null) state.occupied = false
    if (state.courtesy == null) state.courtesy = false
    if (state.engaged == null) state.engaged = engagedEnabled()
    if (state.asleep == null) state.asleep = asleepEnabled()
    if (state.nightActive == null) state.nightActive = false
    if (state.locked == null) state.locked = lockedEnabled()
    if (state.roomState == null) state.roomState = "Off"
    if (state.lightingIntent == null) state.lightingIntent = "Off"
    if (state.roomLevel == null) state.roomLevel = configuredOccupiedLightingLevel() ?: 100
    if (state.lastActivityAt == null) state.lastActivityAt = null
}

// -------------------- UI Option Helpers --------------------

private Map safeNeighborRoomOptions() {
    try {
        if (!parent) return [:]
        return parent.neighborRoomOptions(app.id) ?: [:]
    } catch (Exception e) {
        log.warn "${app.label}: Could not load neighbor room options: ${e.message}"
        return [:]
    }
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

private Map roomProfileOptions() {
    return [
        standard: "Standard",
        bedroom : "Bedroom"
    ]
}

private Boolean bedroomProfile() {
    return roomProfile == "bedroom"
}

private List locationModeNames() {
    try {
        return location?.modes?.collect { mode ->
            mode?.name?.toString() ?: mode?.toString()
        }?.findAll { it } ?: []
    } catch (Exception e) {
        log.warn "${app.label}: Could not load Location Modes: ${e.message}"
        return []
    }
}

private String modeLevelSettingName(String prefix, String modeName) {
    return "${prefix}ForMode_${modeName.replaceAll('[^A-Za-z0-9]', '_')}"
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
    if (deviceName == "MetaLight") {
        return "${safeRoomName()} MetaLight"
    }
    if (deviceName == "Engaged") {
        return engagedLabel?.trim() ? engagedLabel.trim() : "${safeRoomName()} ${bedroomProfile() ? 'Awake' : 'Engaged'}"
    }
    if (deviceName == "Asleep") {
        return asleepLabel?.trim() ? asleepLabel.trim() : "${safeRoomName()} Asleep"
    }
    if (deviceName == "Locked") {
        return lockedLabel?.trim() ? lockedLabel.trim() : "${safeRoomName()} ${bedroomProfile() ? 'Do Not Disturb' : 'Locked'}"
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

        [roomDevice()].findAll { it != null }.each { dev ->
            if (assignDeviceToHubitatRoom(dev, room)) {
                log.info "${roomDeviceLabel()}: Assigned ${dev.displayName} to Hubitat Room ${room.name}"
            } else {
                log.warn "${roomDeviceLabel()}: Could not assign ${dev.displayName} to Hubitat Room ${room.name}"
            }
        }
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Hubitat Room assignment failed: ${e.message}"
    }
}

private Boolean assignDeviceToHubitatRoom(def dev, def room) {
    List attempts = [
        { dev.roomId = room.id },
        { dev.roomId = "${room.id}" },
        { dev.updateDataValue("roomId", "${room.id}") }
    ]

    for (Closure attempt : attempts) {
        try {
            attempt.call()
            return true
        } catch (Throwable ignored) {
            // Hubitat room assignment APIs vary by platform and device wrapper.
        }
    }

    return false
}

private void clearAssignToHubitatRoomOnSave() {
    try {
        app.updateSetting("assignToHubitatRoomOnSave", [type: "bool", value: false])
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not clear Hubitat Room assignment option: ${e.message}"
    }
}

// -------------------- Child Devices --------------------

private String dniFor(String deviceName) {
    return "${app.id}-${deviceName}"
}

private void createOrUpdateChildDevices() {
    createOrUpdateRoomDevice()
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

    try {
        child.initialize()
        child.setEngagedSwitchLabel(labelFor("Engaged"))
        child.setAsleepSwitchLabel(labelFor("Asleep"))
        child.setLockedSwitchLabel(labelFor("Locked"))
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not initialize room meta-device components: ${e.message}"
    }
}

private def roomDevice()    { getChildDevice(dniFor("Room")) }

private List selectedNeighborRoomDevices() {
    if (!neighborChildAppIds) return []

    try {
        if (!parent) return []
        return parent.neighborRoomDevicesForChildIds(neighborChildAppIds) ?: []
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not resolve neighbor rooms from parent: ${e.message}"
        return []
    }
}

private void debugNeighborResolution(List neighborDevices) {
    if (!debugLogging) return

    List selectedIds = getSelectedNeighborChildAppIds()
    List resolvedLabels = neighborDevices.collect { dev ->
        "${dev.displayName ?: dev.label ?: dev.id} (${dev.id})"
    }

    debug "Selected neighbor child app IDs: ${selectedIds ? selectedIds.join(', ') : 'none'}"
    debug "Resolved neighbor Room devices: ${resolvedLabels ? resolvedLabels.join(', ') : 'none'}"
}

private void syncReciprocalNeighbors() {
    try {
        parent.addThisRoomToSelectedNeighbors(app.id)
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Reciprocal neighbor setup failed: ${e.message}"
    }
}

private void clearSyncReciprocalNeighborsOnSave() {
    try {
        app.updateSetting("syncReciprocalNeighborsOnSave", [type: "bool", value: false])
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not clear reciprocal neighbor sync option: ${e.message}"
    }
}


// -------------------- Parent Setup Helper API --------------------
// These methods are called by the parent app for the optional one-time
// "Add this room back to selected neighbors" setup shortcut.

def getChildAppId() {
    return app.id?.toString()
}

def getManagedRoomDevice() {
    return roomDevice()
}

def getManagedRoomDeviceId() {
    return roomDevice()?.id?.toString()
}

def getManagedRoomDeviceLabel() {
    return roomDevice()?.displayName ?: roomDeviceLabel()
}

def getHubitatRoomId() {
    return hubitatRoomId?.toString()
}

def getHubitatRoomName() {
    return selectedHubitatRoom()?.name?.toString()
}

def getConfiguredRoomName() {
    return safeRoomName()
}

def getRoomProfile() {
    return roomProfile ?: "standard"
}

def getSelectedNeighborChildAppIds() {
    if (!neighborChildAppIds) return []
    return neighborChildAppIds instanceof List ? neighborChildAppIds.collect { "${it}" } : ["${neighborChildAppIds}"]
}

def addNeighborChildAppId(String newNeighborChildAppId) {
    if (!newNeighborChildAppId) return false

    List existingIds = getSelectedNeighborChildAppIds()

    if (existingIds.contains(newNeighborChildAppId.toString())) {
        debug "Reciprocal neighbor already present: child app ${newNeighborChildAppId}"
        return false
    }

    existingIds << newNeighborChildAppId.toString()

    try {
        app.updateSetting("neighborChildAppIds", [type: "enum", value: existingIds])
        debug "Added reciprocal neighbor child app id ${newNeighborChildAppId}"
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

private void componentSwitchOn(String role) {
    setRoomComponentSwitch(role, true)
}

private void componentSwitchOff(String role) {
    setRoomComponentSwitch(role, false)
}

private void setRoomComponentSwitch(String role, Boolean switchOn) {
    def dev = roomDevice()
    if (!dev) return

    String value = switchOn ? "on" : "off"
    try {
        if (role == "Engaged") {
            dev.setEngagedSwitchState(value)
        } else if (role == "Asleep") {
            dev.setAsleepSwitchState(value)
        } else if (role == "Locked") {
            dev.setLockedSwitchState(value)
        }
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not set ${role} component switch ${value}: ${e.message}"
    }
}

// -------------------- Event Handlers --------------------

def roomSwitchOnHandler(evt) {
    debug "Room switch ON"

    if (!isDigitalEvent(evt)) {
        debug "Ignoring app-published room switch on event"
        return
    }

    if (state.locked) {
        debug "Room is locked; routing room switch on to MetaLight only"
        publishMetaLightDevice(true, nextRoomControlLevel())
        return
    }

    if (state.asleep) {
        activateNightLighting(nightLightingTimeoutSeconds(), "room switch on while asleep")
        return
    }

    state.roomLevel = nextRoomControlLevel()
    setOccupied("room switch on")
}

def roomSwitchOffHandler(evt) {
    debug "Room switch OFF"

    if (!isDigitalEvent(evt)) {
        debug "Ignoring app-published room switch off event"
        return
    }

    if (state.locked) {
        debug "Room is locked; routing room switch off to MetaLight only"
        publishMetaLightDevice(false, 0)
        return
    }

    if (state.asleep) {
        clearNightLighting("room switch off while asleep")
        return
    }

    clearRoomStateFromRoomSwitch()
}

def roomLevelHandler(evt) {
    Integer level = normalizedPercent(evt.value, 0)
    String eventType = ""
    try {
        eventType = evt.type?.toString()
    } catch (Throwable ignored) {
        eventType = ""
    }

    if (!isDigitalEvent(evt)) {
        debug "Ignoring app-published room level event: ${level}"
        return
    }

    debug "Room level set to ${level}"
    if (state.locked) {
        debug "Room is locked; routing room level to MetaLight only"
        publishMetaLightDevice(level > 0, level)
        return
    }

    if (state.asleep) {
        if (level > 0) {
            activateNightLighting(nightLightingTimeoutSeconds(), "room level set while asleep")
        } else {
            clearNightLighting("room level off while asleep")
        }
        return
    }

    if (level > 0) {
        state.roomLevel = level
        setOccupied("room level set")
    } else {
        clearRoomStateFromRoomSwitch()
    }
}

def courtesyEnabledHandler(evt) {
    debug "Courtesy enabled ${evt.value}"
    refreshCourtesyState()
    recomputeAndPublish()
}

def engagedEnabledHandler(evt) {
    debug "Engaged enabled ${evt.value}"
    if (evt.value == "on") {
        setEngaged("engaged switch on")
    } else {
        state.engaged = false
        scheduleOccupiedTimeout("engaged turned off")
        recomputeAndPublish()
    }
}

def asleepEnabledHandler(evt) {
    debug "Asleep enabled ${evt.value}"
    setAsleep(evt.value == "on", evt.value == "on" ? "asleep switch on" : "asleep switch off")
}

def lockedEnabledHandler(evt) {
    debug "Locked enabled ${evt.value}"
    setLocked(evt.value == "on", evt.value == "on" ? "locked switch on" : "locked switch off")
}

def locationModeHandler(evt) {
    debug "Location Mode changed to ${evt.value}"
    if (!useModeBasedLightingLevels || !changeLightingLevelOnModeChange) {
        debug "Ignoring Location Mode level update because mode-change level adjustment is disabled"
        return
    }

    recomputeAndPublish()
}

def motionActiveHandler(evt) {
    debug "Motion active: ${evt.displayName}"

    if (state.locked) {
        recordLatentActivity("motion active while locked")
        return
    }

    if (state.asleep) {
        debug "Ignoring normal motion activity while asleep"
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

    if (state.asleep) {
        debug "Ignoring normal motion inactive timeout processing while asleep"
        return
    }

    scheduleOccupiedTimeout("motion inactive")
}

def nightMotionActiveHandler(evt) {
    debug "Night motion active: ${evt.displayName}"

    if (state.locked) {
        recordLatentActivity("night motion active while locked")
        return
    }

    if (!state.asleep) {
        debug "Ignoring night motion because room is not asleep"
        return
    }

    activateNightLighting(nightLightingTimeoutSeconds(), "night motion active")
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
        componentSwitchOff("Engaged")
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

    if (activitySwitchesPhysicalOnly && !isPhysicalEvent(evt)) {
        debug "Ignoring activity switch on because event is not physical: ${eventType(evt) ?: 'unknown'}"
        return
    }

    if (state.locked) {
        recordLatentActivity("activity switch on while locked")
        return
    }

    setOccupied("activity switch on")
}

def activityButtonPushedHandler(evt) {
    Integer buttonNumber = eventIntegerValue(evt)
    debug "Activity button pushed ${buttonNumber ?: 'unknown'}: ${evt.displayName}"

    if (!activityButtonAllowed(buttonNumber)) {
        debug "Ignoring pushed button ${buttonNumber ?: 'unknown'} because it is not configured as activity"
        return
    }

    if (state.locked) {
        recordLatentActivity("activity button pushed while locked")
        return
    }

    setOccupied("activity button pushed")
}

def activityButtonHeldHandler(evt) {
    Integer buttonNumber = eventIntegerValue(evt)
    debug "Activity button held ${buttonNumber ?: 'unknown'}: ${evt.displayName}"

    if (buttonNumberMatches(buttonNumber, lockHeldButtonNumber)) {
        componentSwitchOn("Locked")
        setLocked(true, "activity button held lock")
        return
    }

    if (buttonNumberMatches(buttonNumber, unlockHeldButtonNumber)) {
        componentSwitchOff("Locked")
        setLocked(false, "activity button held unlock")
        return
    }
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
        componentSwitchOn("Locked")
        setLocked(true, "external lock on")
    } else {
        componentSwitchOff("Locked")
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

private void publishNightLighting(Boolean active, Integer timeoutMinutes) {
    def dev = roomDevice()
    if (!dev) return

    try {
        if (active) {
            dev.setNightLightingState("on")
            dev.setNightLightingTimeoutMinutes(timeoutMinutes)
        } else {
            dev.setNightLightingState("off")
            dev.setNightLightingTimeoutMinutes(0)
        }
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not publish Night lighting state: ${e.message}"
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
    state.roomLevel = currentRoomControlLevel()
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
    componentSwitchOn("Engaged")

    scheduleEngagedTimeout("engaged on")
    recomputeAndPublish()
}

private void setAsleep(Boolean asleep, String reason) {
    debug "Asleep ${asleep}: ${reason}"
    state.asleep = asleep

    if (asleep) {
        unschedule(clearOccupiedIfStillInactive)
        unschedule(clearEngagedIfStillInactive)
        state.occupied = false
        state.engaged = false
        state.nightActive = false
        componentSwitchOff("Engaged")
        componentSwitchOn("Asleep")
        publishNightLighting(false, 0)
    } else {
        unschedule(clearNightLightingIfStillAsleep)
        state.nightActive = false
        componentSwitchOff("Asleep")
        publishNightLighting(false, 0)
    }

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
        componentSwitchOn("Locked")
        scheduleLockAutoClear()
    } else {
        unschedule(autoClearLock)
        componentSwitchOff("Locked")

        // On unlock, optionally stamp activity now, then restore Occupied
        // only if last activity is still within the normal occupied timeout.
        if (unlockImpliesActivity) {
            recordLatentActivity("unlock implies activity")
        }

        if (state.asleep) {
            state.asleep = false
            state.nightActive = false
            componentSwitchOff("Asleep")
            publishNightLighting(false, 0)
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
            componentSwitchOff("Engaged")
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
    unschedule(clearNightLightingIfStillAsleep)
    unschedule(autoClearLock)
    state.lastInactiveAt = null
    state.lastActivityAt = null
    state.lastEngagedInactiveAt = null

    state.occupied = false
    state.engaged = false
    state.asleep = false
    state.nightActive = false
    state.locked = false
    componentSwitchOff("Engaged")
    componentSwitchOff("Asleep")
    componentSwitchOff("Locked")
    publishNightLighting(false, 0)

    refreshCourtesyState()
    recomputeAndPublish()
}

private void activateNightLighting(Integer seconds, String reason) {
    if (!bedroomProfile()) {
        debug "Ignoring Night lighting request because room profile is not Bedroom"
        publishNightLighting(false, 0)
        return
    }
    if (!state.asleep) {
        debug "Ignoring Night lighting request because room is not asleep"
        publishNightLighting(false, 0)
        return
    }

    Integer delay = positiveSeconds(seconds, nightLightingTimeoutSeconds())
    state.nightActive = true
    publishNightLighting(true, minutesRoundedUp(delay))
    debug "Night lighting true for ${delay} seconds: ${reason}"
    runIn(delay, "clearNightLightingIfStillAsleep", [overwrite: true])
    recomputeAndPublish()
}

private void clearNightLighting(String reason) {
    debug "Night lighting false: ${reason}"
    unschedule(clearNightLightingIfStillAsleep)
    state.nightActive = false
    publishNightLighting(false, 0)
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

private Integer nightLightingTimeoutSeconds() {
    Integer minutes = (nightLightingTimeoutMinutes ?: 5) as Integer
    return positiveSeconds(minutes * 60, 300)
}

private Integer positiveSeconds(value, Integer defaultSeconds) {
    Integer seconds = value ? value as Integer : defaultSeconds
    return seconds > 0 ? seconds : 1
}

private Integer minutesRoundedUp(Integer seconds) {
    Integer safeSeconds = positiveSeconds(seconds, 60)
    Integer wholeMinutes = (safeSeconds / 60) as Integer
    return safeSeconds % 60 == 0 ? wholeMinutes : wholeMinutes + 1
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
    componentSwitchOff("Locked")
    setLocked(false, "lock auto-cleared")
}

def clearNightLightingIfStillAsleep() {
    if (!state.asleep) {
        debug "Night lighting timeout skipped because room is not asleep"
        return
    }

    state.nightActive = false
    publishNightLighting(false, 0)
    recomputeAndPublish()
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
        Integer seconds = occupiedTimeoutSeconds()
        debug "Occupied timeout blocked: motion still active; rescheduling in ${seconds} seconds"
        state.lastInactiveAt = null
        runIn(seconds, clearOccupiedIfStillInactive, [overwrite: true])
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
    componentSwitchOff("Engaged")
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

private void reconcileTimeoutsAfterInitialize() {
    if (state.locked) {
        debug "Initialize reconciliation skipped timeout scheduling because room is locked"
        return
    }

    if (state.engaged) {
        scheduleEngagedTimeout("initialize reconciliation")
        return
    }

    if (state.occupied || anyMotionActive()) {
        if (anyMotionActive()) {
            debug "Initialize reconciliation found active motion"
            if (!state.lastActivityAt) {
                state.lastActivityAt = now()
            }
            state.occupied = true
        }

        scheduleOccupiedTimeout("initialize reconciliation")
    }
}

private Boolean anyExternalLockOn() {
    return externalLockedSwitches?.any { it.currentSwitch == "on" } ?: false
}

private void refreshLockedState() {
    if (anyExternalLockOn()) {
        componentSwitchOn("Locked")
        setLocked(true, "refresh locked state from external lock")
    } else {
        setLocked(lockedEnabled(), "refresh locked state")
    }
}

private void refreshCourtesyState() {
    if (!courtesyEnabled()) {
        state.courtesy = false
        debug "Courtesy false because Courtesy switch is off"
        return
    }

    Boolean anyNeighborActive = selectedNeighborRoomDevices().any { dev ->
        String neighborState = dev.currentValue("roomState")
        neighborState in ["Occupied", "Engaged"]
    }

    state.courtesy = anyNeighborActive
    debug "Courtesy ${state.courtesy ? 'true' : 'false'} from neighbor Room devices"
}

private Boolean courtesyEnabled() {
    return roomDevice()?.currentValue("courtesyEnabled") != "off"
}

private Boolean engagedEnabled() {
    return roomDevice()?.currentValue("engagedEnabled") == "on"
}

private Boolean asleepEnabled() {
    return roomDevice()?.currentValue("asleepEnabled") == "on"
}

private Boolean lockedEnabled() {
    return roomDevice()?.currentValue("lockedEnabled") == "on"
}

private Boolean activityButtonAllowed(Integer buttonNumber) {
    List allowedButtons = configuredButtonNumbers(activityButtonNumbers)
    if (!allowedButtons) return true
    return buttonNumber != null && allowedButtons.contains(buttonNumber)
}

private List configuredButtonNumbers(String rawValue) {
    if (!rawValue?.trim()) return []

    return rawValue
        .split(",")
        .collect { value ->
            try {
                return value.trim() as Integer
            } catch (Exception ignored) {
                return null
            }
        }
        .findAll { it != null }
}

private Boolean buttonNumberMatches(Integer actual, def expected) {
    if (actual == null || expected == null || "${expected}".trim() == "") return false

    try {
        return actual == (expected as Integer)
    } catch (Exception ignored) {
        return false
    }
}

private Integer eventIntegerValue(evt) {
    try {
        return evt.value as Integer
    } catch (Exception ignored) {
        return null
    }
}

private Boolean isPhysicalEvent(evt) {
    return eventType(evt) == "physical"
}

private Boolean isDigitalEvent(evt) {
    return eventType(evt) == "digital"
}

private String eventType(evt) {
    try {
        return evt.type?.toString()
    } catch (Throwable ignored) {
        return ""
    }
}

// -------------------- State Computation and Output --------------------

private String computeRoomState() {
    if (state.locked) return "Locked"
    if (state.asleep) return "Asleep"
    if (state.engaged) return "Engaged"
    if (state.occupied) return "Occupied"
    return "Off"
}

private String computeLightingIntent(String roomState) {
    if (roomState in ["Locked", "Engaged", "Occupied"]) return "On"
    if (roomState == "Asleep") return state.nightActive ? "Night" : "Off"
    if (state.courtesy) return "Courtesy"
    return "Off"
}

private Integer computeLightingLevel(String lightingIntent) {
    if (lightingIntent == "On") return currentRoomControlLevel()
    if (lightingIntent == "Courtesy") return configuredCourtesyLightingLevel()
    if (lightingIntent == "Night") return configuredNightLightingLevel()
    return 0
}

private Integer currentRoomControlLevel() {
    Integer currentDeviceLevel = normalizedPercent(roomDevice()?.currentValue("level"), 0)
    if (currentDeviceLevel > 0) return currentDeviceLevel
    return nextRoomControlLevel()
}

private Integer nextRoomControlLevel() {
    Integer configuredLevel = configuredOccupiedLightingLevel()
    if (configuredLevel != null) return configuredLevel
    if (state.roomLevel != null) return normalizedPercent(state.roomLevel, 100)
    return 100
}

private Integer configuredOccupiedLightingLevel() {
    Integer modeLevel = modeBasedLightingLevel("occupiedLightingLevel")
    if (modeLevel != null) return modeLevel
    if (occupiedLightingLevel == null || "${occupiedLightingLevel}".trim() == "") return null
    return normalizedPercent(occupiedLightingLevel, 100)
}

private Integer configuredCourtesyLightingLevel() {
    Integer modeLevel = modeBasedLightingLevel("courtesyLightingLevel")
    if (modeLevel != null) return modeLevel
    return normalizedPercent(courtesyLightingLevel, 20)
}

private Integer configuredNightLightingLevel() {
    return normalizedPercent(nightLightingLevel, 10)
}

private Integer modeBasedLightingLevel(String prefix) {
    if (!useModeBasedLightingLevels) return null

    String modeName = currentLocationModeName()
    if (!modeName) return null

    def value = settings[modeLevelSettingName(prefix, modeName)]
    if (value == null || "${value}".trim() == "") return null
    return normalizedPercent(value, prefix == "occupiedLightingLevel" ? 100 : 20)
}

private String currentLocationModeName() {
    try {
        return location?.mode?.toString()
    } catch (Exception ignored) {
        return null
    }
}

private Integer normalizedPercent(value, Integer fallback) {
    Integer percent = fallback
    try {
        percent = (value == null ? fallback : value) as Integer
    } catch (Exception ignored) {
        percent = fallback
    }
    return Math.max(Math.min(percent, 100), 0)
}

private void recomputeAndPublish() {
    ensureInitialState()

    String previousLightingIntent = state.lightingIntent ?: "Off"
    Boolean previousSwitchOn = roomDevice()?.currentSwitch == "on"
    Integer previousRoomLevel = normalizedPercent(roomDevice()?.currentValue("level"), 0)
    Integer previousMetaLightLevel = normalizedPercent(state.metaLightLevel, 0)

    String newRoomState = computeRoomState()
    String newLightingIntent = state.locked ? previousLightingIntent : computeLightingIntent(newRoomState)
    Integer effectiveLightingLevel = state.locked ? previousMetaLightLevel : computeLightingLevel(newLightingIntent)
    Integer roomControlLevel = state.locked ? previousRoomLevel : (newRoomState in ["Occupied", "Engaged"] ? currentRoomControlLevel() : 0)

    // Locking should not change the public switch. It is a freeze/hold state, not an on/off request.
    Boolean roomSwitchShouldBeOn = state.locked ? previousSwitchOn : (newRoomState in ["Occupied", "Engaged"])
    Boolean metaLightShouldBeOn = state.locked ? (state.metaLightSwitch == "on") : (newLightingIntent in ["On", "Courtesy", "Night"])

    publishMetaLightDevice(metaLightShouldBeOn, effectiveLightingLevel)
    publishRoomDevice(roomSwitchShouldBeOn, newRoomState, newLightingIntent, roomControlLevel)

    if (state.roomState != newRoomState || state.lightingIntent != newLightingIntent) {
        log.info "${roomDeviceLabel()}: roomState=${newRoomState}, lightingIntent=${newLightingIntent}"
    }

    state.roomState = newRoomState
    state.lightingIntent = newLightingIntent
}

private void publishRoomDevice(Boolean switchOn, String roomState, String lightingIntent, Integer lightingLevel) {
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

    try {
        dev.setRoomLevel(lightingLevel)
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not set lighting level on room device: ${e.message}"
    }
}

private void publishMetaLightDevice(Boolean switchOn, Integer lightingLevel) {
    def dev = roomDevice()
    if (!dev) return

    try {
        if (switchOn) {
            Integer level = lightingLevel
            dev.setMetaLightLevel(level)
            state.metaLightLevel = level
        }
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not set level on meta-light device: ${e.message}"
    }

    try {
        dev.setMetaLightSwitchState(switchOn ? "on" : "off")
        state.metaLightSwitch = switchOn ? "on" : "off"
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not set switch state on meta-light device: ${e.message}"
    }
}


// -------------------- Logging --------------------

private void debug(String msg) {
    if (debugLogging) {
        log.debug "${roomDeviceLabel()}: ${msg}"
    }
}
