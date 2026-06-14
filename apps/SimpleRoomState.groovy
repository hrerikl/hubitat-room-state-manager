/**
 * Simple Room State - Child App
 *
 * Install as Apps Code:
 *   Name: Simple Room State
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
 *
 * Race-sensitive presence flags:
 *   occupied, engaged, asleep, and locked are dual-written through presenceFlag helpers.
 *   atomicState makes those decisions immediately visible to overlapping handlers; state
 *   stays in sync for existing reads, diagnostics, and migration. Do not write those
 *   four state fields directly.
 *
 *   Do not use singleThreaded: true here. It was tried (June 2026) and the synchronous
 *   room->parent->room call chains convoyed behind serialized handlers, backing events
 *   up by ~20 minutes. atomicState flags plus debounced lighting reassess is the model.
 */

definition(
    name: "Simple Room State",
    namespace: "lundby",
    author: "Erik Lundby / ChatGPT",
    description: "Reusable room state child app with Room meta-device, Engaged, Locked, and LightingIntent output.",
    category: "Convenience",
    parent: "lundby:Simple Home",
    singleInstance: false,
    iconUrl: "",
    iconX2Url: ""
)

#include lundby.SimpleHomeHelpers

preferences {
    page(name: "childMainPage", title: "Room State", install: true, uninstall: true) {
        section("Room") {
            input "hubitatRoomId", "enum", title: "Hubitat Room", options: hubitatRoomOptions(), required: false, submitOnChange: true
            input "roomProfile", "enum", title: "Room profile", options: roomProfileOptions(), defaultValue: "standard", required: true, submitOnChange: true
            paragraph "Select the Hubitat Room explicitly. If the room name override is blank, the selected Hubitat Room name is used. Creates: Room <name>, plus MetaLight, Courtesy, Engaged, Asleep, and Locked component devices."
        }

        if (houseIntentProfile()) {
            section("House intent") {
                paragraph "House Intent is a manually controlled reference room. It does not participate in neighbor Courtesy and is intended to publish the house-level MetaLight for other rooms to follow."
            }
        } else {
            section("Activity") {
                input "motionSensors", "capability.motionSensor", title: "Motion sensors", multiple: true, required: false
                input "doorContactSensors", "capability.contactSensor", title: "Door contact sensors. Opening a door counts as occupancy evidence.", multiple: true, required: false
            }

            section("Neighbor courtesy") {
                input "neighborChildAppIds", "enum", title: "Neighbor rooms that should trigger courtesy lighting", options: safeNeighborRoomOptions(), multiple: true, required: false
                paragraph "Select other Simple Room State rooms. Neighbor rooms trigger Courtesy only when their roomState is Occupied or Engaged. Locked rooms do not propagate Courtesy."
            }

            section("Timeouts") {
                input "occupiedTimeoutMinutes", "number", title: "Occupied timeout after no motion", defaultValue: 5, required: true
                input "engagedTimeoutMinutes", "number", title: "Engaged timeout after no activity", defaultValue: 30, required: true
            }
        }

        section("Advanced") {
            input "showAdvancedSettings", "bool", title: "Show advanced settings", defaultValue: false, required: true, submitOnChange: true
        }

        if (showAdvancedSettings) {
            section("Advanced room") {
                input "roomName", "text", title: "Room name override, optional", required: false, submitOnChange: true
                input "engagedLabel", "text", title: "Engaged device label, optional. Example: Focus Mode", required: false
                input "asleepLabel", "text", title: "Asleep device label, optional. Example: Sleeping", required: false
                input "lockedLabel", "text", title: "Locked device label, optional. Example: Recording", required: false
                input "customLightingOnText", "text", title: "Custom lighting on text override, optional", required: false
                input "customLightingOffText", "text", title: "Custom lighting off text override, optional", required: false
            }

            if (!houseIntentProfile()) {
                section("Advanced activity") {
                    input "activitySwitches", "capability.switch", title: "Switches that imply room activity when turned on", multiple: true, required: false
                    input "activitySwitchesPhysicalOnly", "bool", title: "Only physical activity switch events imply room activity", defaultValue: false, required: true
                    input "activityButtons", "capability.pushableButton", title: "Buttons that imply room activity when pushed", multiple: true, required: false
                    input "activityButtonNumbers", "text", title: "Activity button numbers, comma separated. Blank means any pushed button.", required: false
                    input "clearPresenceModeNames", "enum", title: "Modes that clear current room presence", options: locationModeOptions(), multiple: true, defaultValue: defaultClearPresenceModeNames(), required: false
                    input "lockHeldButtonNumber", "number", title: "Held button number that locks this room. Blank disables.", required: false
                    input "unlockHeldButtonNumber", "number", title: "Held button number that unlocks this room. Blank disables.", required: false
                    input "staleActiveMotionMinutes", "number", title: "Ignore active motion sensor after no motion/device activity for this many minutes. Blank or 0 disables.", defaultValue: 60, required: false
                }

                section("Advanced engagement") {
                    input "engagementSwitches", "capability.switch", title: "Switches that imply engaged state when turned on", multiple: true, required: false
                    input "engageOnMotionWithDoorsClosed", "bool", title: "Engage on activity with doors closed", defaultValue: false, required: true
                    paragraph "When enabled, activity marks the room Engaged if every configured door contact is closed. Door closing by itself is not activity. Opening any configured door clears Engaged and still counts as occupancy evidence."
                }

                section("Advanced courtesy") {
                    input "syncReciprocalNeighborsOnSave", "bool", title: "Add this room back to selected neighbors on save", defaultValue: false, required: true
                    paragraph "When enabled, saving this room attempts to add it to each selected neighbor's courtesy list. Each child app still owns its own neighbor list."
                }

                section("Advanced lock") {
                    input "externalLockedSwitches", "capability.switch", title: "External switches that should lock this room", multiple: true, required: false
                    input "lockAutoClearMinutes", "number", title: "Auto-clear Locked after X minutes. Blank or 0 disables.", required: false
                    input "unlockImpliesActivity", "bool", title: "Treat unlock as occupancy activity", defaultValue: false, required: true
                }
            }

            if (bedroomProfile()) {
                section("Bedroom sleep") {
                    input "nightMotionSensors", "capability.motionSensor", title: "Motion sensors that trigger Night lighting while Asleep", multiple: true, required: false
                    input "nightLightingTimeoutMinutes", "number", title: "Night lighting timeout minutes", defaultValue: 5, required: true
                    input "nightLightingLevel", "number", title: "Night lighting level", defaultValue: 10, required: true
                    input "wakeFromSleepOnBedExitInDayMode", "bool", title: "Wake from sleep on these sensors in Day mode after grace period", defaultValue: true, required: true
                    input "sleepWakeGraceMinutes", "number", title: "Minutes after entering sleep before bed-exit motion can wake room", defaultValue: 30, required: true
                }
            }

            section("Room lighting levels") {
                input "followCircadianReference", "bool", title: "Follow circadian reference for MetaLight level and CT", defaultValue: defaultFollowCircadianReference(), required: true, submitOnChange: true

                if (followCircadianReferenceSettingEnabled()) {
                    input "circadianReferenceBulb", "capability.colorTemperature", title: "Override circadian reference bulb. Blank uses parent default.", multiple: false, required: false
                    input "occupiedReferencePercent", "number", title: "Occupied reference multiplier percent", defaultValue: 100, required: true
                    input "occupiedReferenceOffset", "number", title: "Occupied reference offset. Example: +10 or -10", defaultValue: 0, required: true
                    if (!houseIntentProfile()) {
                        input "courtesyReferencePercent", "number", title: "Courtesy reference multiplier percent", defaultValue: 30, required: true
                        input "courtesyReferenceOffset", "number", title: "Courtesy reference offset. Example: +10 or -10", defaultValue: 0, required: true
                    }
                } else {
                    input "occupiedLightingLevel", "number", title: "Default occupied lighting level. Blank uses the last on level.", required: false
                    if (!houseIntentProfile()) {
                        input "courtesyLightingLevel", "number", title: "Courtesy/convenience lighting level", defaultValue: 20, required: true
                    }
                }

                input "useModeBasedLightingLevels", "bool", title: "Use Location Mode based lighting settings", defaultValue: false, required: true, submitOnChange: true

                if (useModeBasedLightingLevels) {
                    input "changeLightingLevelOnModeChange", "bool", title: "Change level on Location Mode change", defaultValue: false, required: true

                    locationModeNames().each { modeName ->
                        if (followCircadianReferenceSettingEnabled()) {
                            input modeLevelSettingName("occupiedReferencePercent", modeName), "number", title: "Occupied reference multiplier percent - ${modeName}. Blank uses default.", required: false
                            input modeLevelSettingName("occupiedReferenceOffset", modeName), "number", title: "Occupied reference offset - ${modeName}. Blank uses default.", required: false
                            if (!houseIntentProfile()) {
                                input modeLevelSettingName("courtesyReferencePercent", modeName), "number", title: "Courtesy reference multiplier percent - ${modeName}. Blank uses default.", required: false
                                input modeLevelSettingName("courtesyReferenceOffset", modeName), "number", title: "Courtesy reference offset - ${modeName}. Blank uses default.", required: false
                            }
                        } else {
                            input modeLevelSettingName("occupiedLightingLevel", modeName), "number", title: "Occupied level - ${modeName}. Blank uses default.", required: false
                            if (!houseIntentProfile()) {
                                input modeLevelSettingName("courtesyLightingLevel", modeName), "number", title: "Courtesy level - ${modeName}. Blank uses default.", required: false
                            }
                        }
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
                paragraph "Simple Home can suppress or force debug logging from the parent app."
            }
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
    cacheDebugEnabled(debugLogging)
    updateChildAppLabel()

    if (!roomStateChildAllowed()) {
        markDuplicateForDelete("House Intent Room")
        log.error "${app.label}: Duplicate House Intent room. Delete this child app and keep the parent-created House Intent room."
        return
    }

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
    subscribe(roomDevice(), "colorTemperature", roomColorTemperatureHandler)

    subscribe(roomDevice(), "courtesyEnabled", courtesyEnabledHandler)
    subscribe(roomDevice(), "engagedEnabled", engagedEnabledHandler)
    subscribe(roomDevice(), "asleepEnabled", asleepEnabledHandler)
    subscribe(roomDevice(), "lockedEnabled", lockedEnabledHandler)
    subscribe(roomDevice(), "customLighting", customLightingHandler)
    subscribe(location, "mode", locationModeHandler)
    subscribe(circadianReferenceDevice(), "level", circadianReferenceHandler)
    subscribe(circadianReferenceDevice(), "colorTemperature", circadianReferenceHandler)

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

    if (lockedFlag() || lockedEnabled() || anyExternalLockOn()) {
        setLockedFlag(true)
        debug "Recover Simple Home skipped recompute because room is locked"
        return
    }

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
    ensureRoomProfileSetting()
    ensureHouseIntentDefaults()
    seedPresenceFlag("occupied", false)
    if (state.courtesy == null) state.courtesy = false
    seedPresenceFlag("engaged", engagedEnabled())
    seedPresenceFlag("asleep", asleepEnabled())
    if (state.nightActive == null) state.nightActive = false
    seedPresenceFlag("locked", lockedEnabled())
    if (state.roomState == null) state.roomState = "Off"
    if (state.lightingIntent == null) state.lightingIntent = "Off"
    if (state.roomLevel == null) state.roomLevel = configuredOccupiedLightingLevel() ?: 100
    if (state.metaLightColorTemperature == null) state.metaLightColorTemperature = currentReferenceColorTemperature()
    if (state.circadianReferencePaused == null) state.circadianReferencePaused = false
    if (state.lastActivityAt == null) state.lastActivityAt = null
    if (state.asleepStartedAt == null && asleepFlag()) state.asleepStartedAt = now()
}

private void seedPresenceFlag(String name, Boolean fallback) {
    def atomic = atomicState[name]
    if (atomic != null) return

    Boolean value = state[name] != null ? state[name] == true : fallback == true
    setPresenceFlag(name, value)
}

private void setPresenceFlag(String name, Boolean value) {
    Boolean normalized = value == true
    atomicState[name] = normalized
    state[name] = normalized
}

private Boolean presenceFlag(String name) {
    def atomic = atomicState[name]
    if (atomic != null) return atomic == true
    return state[name] == true
}

private Boolean occupiedFlag() { presenceFlag("occupied") }
private Boolean engagedFlag() { presenceFlag("engaged") }
private Boolean asleepFlag() { presenceFlag("asleep") }
private Boolean lockedFlag() { presenceFlag("locked") }

private void setOccupiedFlag(Boolean value) { setPresenceFlag("occupied", value) }
private void setEngagedFlag(Boolean value) { setPresenceFlag("engaged", value) }
private void setAsleepFlag(Boolean value) { setPresenceFlag("asleep", value) }
private void setLockedFlag(Boolean value) { setPresenceFlag("locked", value) }

private Boolean activePresenceFlag() {
    return occupiedFlag() || engagedFlag() || asleepFlag() || lockedFlag()
}

private void ensureRoomProfileSetting() {
    if (settings?.roomProfile) return

    try {
        app.updateSetting("roomProfile", [type: "enum", value: "standard"])
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not default room profile: ${e.message}"
    }
}

private void ensureHouseIntentDefaults() {
    if (!houseIntentProfile()) return
    if (settings?.followCircadianReference != null) return

    try {
        app.updateSetting("followCircadianReference", [type: "bool", value: true])
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not default House Intent reference tracking: ${e.message}"
    }
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
    Map options = [
        standard: "Standard",
        bedroom : "Bedroom"
    ]
    if (houseIntentProfile()) options.houseIntent = "House Intent"
    return options
}

private Boolean bedroomProfile() {
    return getRoomProfile() == "bedroom"
}

private Boolean houseIntentProfile() {
    return getRoomProfile() == "houseIntent"
}

private Boolean defaultFollowCircadianReference() {
    return houseIntentProfile()
}

private Boolean followCircadianReferenceSettingEnabled() {
    return houseIntentProfile() || followCircadianReference == true
}

private List defaultClearPresenceModeNames() {
    return locationModeNames().findAll { modeName -> modeName in ["Away", "Vacation"] }
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
        name = houseIntentProfile() ? "House Intent" : (app.label ?: "Room")
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
    if (!debugEnabled(debugLogging)) return

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

def getSimpleHomeAppType() {
    return "roomState"
}

def getManagedMetaLightDevice() {
    def room = roomDevice()
    if (!room) return null

    try {
        return room.getChildDevice("${dniFor("Room")}-MetaLight")
    } catch (Throwable ignored) {
        return null
    }
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
    return settings?.roomProfile ?: "standard"
}

private Boolean roomStateChildAllowed() {
    if (!houseIntentProfile()) return true
    return validateChildUniqueness("House Intent room") { parent.houseIntentRoomAllowed(app.id) }
}

def getCustomLightingOnText() {
    return settings?.customLightingOnText?.toString()?.trim()
}

def getCustomLightingOffText() {
    return settings?.customLightingOffText?.toString()?.trim()
}

def getCircadianReferenceBulb() {
    return settings?.circadianReferenceBulb
}

def configureHouseIntentFromParent(def rawReferenceDevice) {
    try {
        app.updateSetting("roomProfile", [type: "enum", value: "houseIntent"])
        app.updateSetting("roomName", [type: "text", value: "House Intent"])
        app.updateSetting("followCircadianReference", [type: "bool", value: true])
        app.updateSetting("createDevicesNow", [type: "bool", value: true])

        if (rawReferenceDevice && !sameDevice(rawReferenceDevice, roomDevice())) {
            app.updateSetting("circadianReferenceBulb", [type: "capability.colorTemperature", value: rawReferenceDevice.id])
        }

        unsubscribe()
        unschedule()
        initializeChild()
        return true
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not configure House Intent from parent: ${e.message}"
        return false
    }
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
        debug "Added reciprocal neighbor child app id ${newNeighborChildAppId}; scheduling reinitialize"
        runIn(2, "initializeChild", [overwrite: true])
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

    if (lockedFlag()) {
        debug "Room is locked; routing room switch on to MetaLight only"
        publishMetaLightDevice(true, lockedMetaLightOnLevel(), effectiveMetaLightColorTemperature())
        return
    }

    if (asleepFlag()) {
        activateNightLighting(nightLightingTimeoutSeconds(), "room switch on while asleep")
        return
    }

    state.roomLevel = nextRoomControlLevel()
    setOccupied(eventReason("room switch on", evt))
}

def roomSwitchOffHandler(evt) {
    debug "Room switch OFF"

    if (!isDigitalEvent(evt)) {
        debug "Ignoring app-published room switch off event"
        return
    }

    if (lockedFlag()) {
        debug "Room is locked; routing room switch off to MetaLight only"
        publishMetaLightDevice(false, 0, effectiveMetaLightColorTemperature())
        return
    }

    if (asleepFlag()) {
        clearNightLighting("room switch off while asleep")
        return
    }

    if (houseIntentProfile()) {
        debug "House Intent room switch off clears custom lighting"
        clearCustomLightingFromRoomControl("house intent room switch off")
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
    pauseCircadianReference(eventReason("manual room level change", evt))
    activateCustomLightingFromRoomControl(eventReason("manual room level change", evt))

    if (lockedFlag()) {
        debug "Room is locked; routing room level to MetaLight only"
        publishMetaLightDevice(level > 0, level, effectiveMetaLightColorTemperature())
        return
    }

    if (asleepFlag()) {
        if (level > 0) {
            activateNightLighting(nightLightingTimeoutSeconds(), "room level set while asleep")
        } else {
            clearNightLighting("room level off while asleep")
        }
        return
    }

    if (level > 0) {
        state.roomLevel = level
        setOccupied(eventReason("room level set", evt))
    } else {
        clearRoomStateFromRoomSwitch()
    }
}

def roomColorTemperatureHandler(evt) {
    Integer colorTemperature = normalizedColorTemperature(evt.value, effectiveMetaLightColorTemperature())

    if (!isDigitalEvent(evt)) {
        debug "Ignoring app-published room color temperature event: ${colorTemperature}"
        return
    }

    debug "Room color temperature set to ${colorTemperature}"
    pauseCircadianReference(eventReason("manual room color temperature change", evt))
    activateCustomLightingFromRoomControl(eventReason("manual room color temperature change", evt))
    state.metaLightColorTemperature = colorTemperature
}

def customLightingHandler(evt) {
    if (evt.value == "on") {
        pauseCircadianReference("custom lighting")
        return
    }

    if (lockedFlag()) {
        debug "Ignoring custom lighting off because room is locked"
        return
    }

    if (state.circadianReferencePaused != true) {
        debug "Custom lighting cleared while reference tracking already active"
        return
    }

    resumeCircadianReference("custom lighting cleared")
    recomputeAndPublish()
}

private void activateCustomLightingFromRoomControl(String reason) {
    try {
        roomDevice()?.activateCustomLighting()
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not activate custom lighting for ${reason}: ${e.message}"
    }
}

private void clearCustomLightingFromRoomControl(String reason) {
    try {
        roomDevice()?.clearCustomLighting()
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not clear custom lighting for ${reason}: ${e.message}"
    }
}

// Session-end housekeeping: uses the raw untyped setter so the customLighting
// attribute goes truthful without firing the user-facing announcement.
private void clearCustomLightingSilently(String reason) {
    def dev = roomDevice()
    if (!dev) return
    if (dev.currentValue("customLighting") != "on") return

    try {
        dev.setCustomLightingState("off")
        debug "Cleared custom lighting silently: ${reason}"
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not silently clear custom lighting for ${reason}: ${e.message}"
    }
}

def courtesyEnabledHandler(evt) {
    debug "Courtesy enabled ${evt.value}"
    refreshCourtesyState()
    recomputeAndPublish()
}

def engagedEnabledHandler(evt) {
    debug "Engaged enabled ${evt.value}"

    // The meta driver tags child-switch toggles as digital; the app's own
    // engagedEnabled write-backs are untyped. Filtering them out keeps this
    // handler from re-entering setEngaged and clobbering the specific
    // activity reason with a generic one (08db864).
    if (!isDigitalEvent(evt)) {
        debug "Ignoring app-published Engaged switch ${evt.value} event"
        return
    }

    if (evt.value == "on") {
        setEngaged(eventReason("engaged switch on", evt))
    } else {
        setEngagedFlag(false)
        scheduleOccupiedTimeout(eventReason("engaged turned off", evt))
        recomputeAndPublish()
    }
}

def asleepEnabledHandler(evt) {
    debug "Asleep enabled ${evt.value}"
    setAsleep(evt.value == "on", eventReason(evt.value == "on" ? "asleep switch on" : "asleep switch off", evt))
}

def lockedEnabledHandler(evt) {
    debug "Locked enabled ${evt.value}"
    setLocked(evt.value == "on", eventReason(evt.value == "on" ? "locked switch on" : "locked switch off", evt))
}

def locationModeHandler(evt) {
    debug "Location Mode changed to ${evt.value}"

    if (presenceClearingMode(evt.value)) {
        clearPresenceFromLocationMode(evt.value?.toString())
        return
    }

    if (!useModeBasedLightingLevels || !changeLightingLevelOnModeChange) {
        debug "Ignoring Location Mode level update because mode-change level adjustment is disabled"
        return
    }

    state.forceModeLightingLevel = true
    try {
        recomputeAndPublish()
    } finally {
        state.forceModeLightingLevel = false
    }
}

private Boolean presenceClearingMode(def modeName) {
    if (houseIntentProfile()) return false
    String currentMode = modeName?.toString()
    if (!currentMode) return false
    def configuredModes = settings?.clearPresenceModeNames
    if (configuredModes == null) configuredModes = defaultClearPresenceModeNames()
    return asList(configuredModes).collect { it?.toString() }.contains(currentMode)
}

def circadianReferenceHandler(evt) {
    reconcileCircadianPauseWithRoomDevice("circadian reference ${evt?.name} event")
    if (!circadianReferenceTrackingActive()) {
        if (followCircadianReferenceEnabled()) {
            debug "Ignoring circadian reference ${evt.name} change because tracking is paused"
        }
        return
    }
    if (!metaLightIntentActive()) {
        debug "Ignoring circadian reference ${evt.name} change because MetaLight is inactive"
        return
    }

    debug "Circadian reference ${evt.name} changed to ${evt.value}"
    state.forceCircadianReferenceUpdate = true
    try {
        recomputeAndPublish()
    } finally {
        state.forceCircadianReferenceUpdate = false
    }
}

def reapplyCircadianReferenceFromParent(String reason = "parent reference changed", Integer referenceLevel = null, Integer referenceColorTemperature = null) {
    reconcileCircadianPauseWithRoomDevice(reason)
    if (!circadianReferenceTrackingActiveForRecompute()) {
        String detail = "tracking paused or unavailable"
        log.info "${roomDeviceLabel()}: Skipping parent reference reapply (${reason}): ${detail}"
        return [applied: false, reason: detail]
    }
    if (!metaLightIntentActive()) {
        String detail = "MetaLight inactive"
        log.info "${roomDeviceLabel()}: Skipping parent reference reapply (${reason}): ${detail}"
        return [applied: false, reason: detail]
    }

    Map before = roomReferenceTraceSnapshot()
    Map reference = referenceSnapshotForRecompute(parentReferenceSnapshot(referenceLevel, referenceColorTemperature))
    log.info "${roomDeviceLabel()}: Reapplying parent reference (${reason}); before=${formatReferenceTrace(before)}, reference=${formatReferenceTrace(reference)}"
    state.forceCircadianReferenceUpdate = true
    try {
        recomputeAndPublish(null, reference)
    } finally {
        state.forceCircadianReferenceUpdate = false
    }
    Map after = roomReferenceTraceSnapshot()
    log.info "${roomDeviceLabel()}: Parent reference reapplied (${reason}); after=${formatReferenceTrace(after)}"
    return [applied: true, before: before, reference: reference, after: after]
}

def motionActiveHandler(evt) {
    Map timing = startRoomStateTiming("motionActive")
    debug "Motion active: ${evt.displayName}"

    if (!eventDeviceStillMatches(evt, "motion", "active")) {
        markRoomStateTiming(timing, "staleCheck")
        logRoomStateTiming(timing, "ignored stale motion active: ${evt.displayName}")
        debug "Ignoring stale motion active event because sensor is no longer active: ${evt.displayName}"
        return
    }
    markRoomStateTiming(timing, "staleCheck")

    recordPresenceActivity("motion active: ${evt.displayName}", timing)
}

def recordPresenceActivity(String reason, Map timing = null) {
    reason = activityReason(reason, "presence activity")
    markRoomStateTiming(timing, "presenceStart")

    if (lockedFlag()) {
        markRoomStateTiming(timing, "lockedCheck")
        recordLatentActivity("${reason} while locked")
        logRoomStateTiming(timing, "latent activity while locked: ${reason}")
        return
    }
    markRoomStateTiming(timing, "lockedCheck")

    if (asleepFlag()) {
        markRoomStateTiming(timing, "asleepCheck")
        logRoomStateTiming(timing, "ignored activity while asleep: ${reason}")
        debug "Ignoring normal activity while asleep: ${reason}"
        return
    }
    markRoomStateTiming(timing, "asleepCheck")

    if (engageOnMotionWithDoorsClosed && allDoorsClosed()) {
        markRoomStateTiming(timing, "doorCheck")
        setEngaged("${reason} with all doors closed", timing)
    } else {
        markRoomStateTiming(timing, "doorCheck")
        setOccupied(reason, timing)
    }
}

def recordEngagementActivity(String reason) {
    reason = activityReason(reason, "engagement activity")

    if (lockedFlag()) {
        recordLatentActivity("${reason} while locked")
        return
    }

    if (asleepFlag()) {
        debug "Ignoring engagement activity while asleep: ${reason}"
        return
    }

    setEngaged(reason)
}

private String activityReason(String reason, String fallback) {
    String text = reason?.toString()?.trim()
    return text ?: fallback
}

private String eventReason(String reason, evt) {
    String source = evt?.displayName?.toString()?.trim()
    return source ? "${activityReason(reason, 'activity')}: ${source}" : activityReason(reason, "activity")
}

private Boolean eventDeviceStillMatches(evt, String attributeName, String expectedValue) {
    try {
        String currentValue = evt?.device?.currentValue(attributeName)?.toString()
        if (currentValue == null) return true
        return currentValue == expectedValue
    } catch (Throwable ignored) {
        return true
    }
}

def motionInactiveHandler(evt) {
    debug "Motion inactive: ${evt.displayName}"

    if (!eventDeviceStillMatches(evt, "motion", "inactive")) {
        debug "Ignoring stale motion inactive event because sensor is no longer inactive: ${evt.displayName}"
        return
    }

    if (lockedFlag()) {
        debug "Ignoring motion inactive timeout processing because room is locked"
        return
    }

    if (asleepFlag()) {
        debug "Ignoring normal motion inactive timeout processing while asleep"
        return
    }

    scheduleOccupiedTimeout("motion inactive")
}

def nightMotionActiveHandler(evt) {
    debug "Night motion active: ${evt.displayName}"

    if (!eventDeviceStillMatches(evt, "motion", "active")) {
        debug "Ignoring stale night motion active event because sensor is no longer active: ${evt.displayName}"
        return
    }

    if (lockedFlag()) {
        recordLatentActivity("night motion active while locked")
        return
    }

    if (!asleepFlag()) {
        debug "Ignoring night motion because room is not asleep"
        return
    }

    if (shouldWakeFromSleepOnBedExit()) {
        debug "Waking room from sleep on bed-exit motion in Day mode"
        wakeFromSleepOnBedExit("bed-exit motion after sleep grace")
        return
    }

    activateNightLighting(nightLightingTimeoutSeconds(), "night motion active")
}

def doorOpenHandler(evt) {
    debug "Door open: ${evt.displayName}"

    if (!eventDeviceStillMatches(evt, "contact", "open")) {
        debug "Ignoring stale door open event because contact is no longer open: ${evt.displayName}"
        return
    }

    if (lockedFlag()) {
        recordLatentActivity("door opened while locked: ${evt.displayName}")
        return
    }

    if (engageOnMotionWithDoorsClosed && engagedFlag()) {
        debug "Clearing engaged because a door opened"
        setEngagedFlag(false)
        componentSwitchOff("Engaged")
    }

    setOccupied("door opened: ${evt.displayName}")
}

def doorClosedHandler(evt) {
    debug "Door closed: ${evt.displayName}"

    if (!eventDeviceStillMatches(evt, "contact", "closed")) {
        debug "Ignoring stale door closed event because contact is no longer closed: ${evt.displayName}"
        return
    }

    if (lockedFlag()) {
        recordLatentActivity("door closed while locked: ${evt.displayName}")
        return
    }

    setOccupied("door closed: ${evt.displayName}")
}

def activitySwitchOnHandler(evt) {
    debug "Activity switch on: ${evt.displayName}"

    if (activitySwitchesPhysicalOnly && !isPhysicalEvent(evt)) {
        debug "Ignoring activity switch on because event is not physical: ${eventType(evt) ?: 'unknown'}"
        return
    }

    recordPresenceActivity("activity switch on: ${evt.displayName}")
}

def activityButtonPushedHandler(evt) {
    Integer buttonNumber = eventIntegerValue(evt)
    debug "Activity button pushed ${buttonNumber ?: 'unknown'}: ${evt.displayName}"

    if (!activityButtonAllowed(buttonNumber)) {
        debug "Ignoring pushed button ${buttonNumber ?: 'unknown'} because it is not configured as activity"
        return
    }

    recordPresenceActivity("activity button pushed ${buttonNumber ?: 'unknown'}: ${evt.displayName}")
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

    if (lockedFlag()) {
        recordLatentActivity(eventReason("engagement switch on while locked", evt))
        return
    }

    setEngaged(eventReason("engagement switch on", evt))
}

def neighborRoomHandler(evt) {
    debug "Neighbor room event ${evt.name}=${evt.value}: ${evt.displayName}"

    Boolean lockedNow = lockedFlag()
    Boolean activeNow = occupiedFlag() || engagedFlag() || asleepFlag() || lockedNow

    if (lockedNow) {
        debug "Ignoring neighbor/courtesy event because room is locked"
        return
    }

    String neighborState = evt.value?.toString()
    if (neighborState in ["Occupied", "Engaged"]) {
        if (activeNow) {
            debug "Ignoring active neighbor event because this room is already active"
            return
        }
        if (courtesyEligibleFromNeighborEvent()) {
            setCourtesyFromNeighborEvent(evt)
            recomputeAndPublish()
        } else {
            debug "Ignoring active neighbor event because this room is not courtesy-eligible"
        }
        return
    }

    if (state.courtesy) {
        refreshCourtesyState()
        recomputeAndPublish()
    }
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
    reason = activityReason(reason, "activity")
    Long timestamp = now()
    state.lastActivityAt = timestamp
    state.lastInactiveAt = timestamp
    state.lastActivityReason = reason
    publishPresenceActivity(timestamp, reason)
    debug "Activity recorded at ${timestamp}: ${reason}"
    scheduleOccupiedTimeout(reason)
}

private void recordLatentActivity(String reason) {
    reason = activityReason(reason, "latent activity")
    Long timestamp = now()
    state.lastActivityAt = timestamp
    state.lastInactiveAt = timestamp
    state.lastActivityReason = reason
    publishPresenceActivity(timestamp, reason)
    debug "Latent activity recorded at ${timestamp}: ${reason}"
}

private void publishPresenceActivity(Long timestamp, String reason = "") {
    def dev = roomDevice()
    if (!dev || !timestamp) return

    try {
        dev.recordPresenceActivityDetail("${timestamp}", reason)
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not publish presence activity (${reason}): ${e.message}"
    }
}

private void publishActivityDetail(Long timestamp, String reason) {
    def dev = roomDevice()
    if (!dev || !timestamp) return

    reason = activityReason(reason, "activity")
    state.lastActivityReason = reason

    try {
        dev.recordActivityDetail("${timestamp}", reason)
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not publish activity detail (${reason}): ${e.message}"
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

private void setOccupied(String reason, Map timing = null) {
    debug "Occupied true: ${reason}"
    setOccupiedFlag(true)
    markRoomStateTiming(timing, "occupiedFlag")
    state.offReason = null
    clearCourtesyState()
    markRoomStateTiming(timing, "clearCourtesy")
    recordActivity(reason)
    markRoomStateTiming(timing, "recordActivity")
    state.roomLevel = currentRoomControlLevel()
    markRoomStateTiming(timing, "roomControlLevel")
    recomputeAndPublish(timing)
    logRoomStateTiming(timing, "setOccupied: ${reason}")
}

private void setEngaged(String reason, Map timing = null) {
    reason = activityReason(reason, "engagement activity")
    state.offReason = null

    if (asleepFlag()) {
        logRoomStateTiming(timing, "ignored engaged while asleep: ${reason}")
        debug "Ignoring Engaged while asleep: ${reason}"
        setEngagedFlag(false)
        componentSwitchOff("Engaged")
        return
    }

    debug "Engaged true: ${reason}"
    setOccupiedFlag(true)
    setEngagedFlag(true)
    markRoomStateTiming(timing, "engagedFlags")
    clearCourtesyState()
    unschedule(clearEngagedIfStillInactive)
    state.lastEngagedInactiveAt = now()
    markRoomStateTiming(timing, "clearCourtesy")

    recordActivity(reason)
    markRoomStateTiming(timing, "recordActivity")

    componentSwitchOn("Engaged")
    markRoomStateTiming(timing, "componentSwitch")

    scheduleEngagedTimeout("engaged on")
    markRoomStateTiming(timing, "scheduleTimeout")
    recomputeAndPublish(timing)
    logRoomStateTiming(timing, "setEngaged: ${reason}")
}

private void setAsleep(Boolean asleep, String reason) {
    debug "Asleep ${asleep}: ${reason}"
    setAsleepFlag(asleep)

    if (asleep) {
        clearCourtesyState()
        unschedule(clearOccupiedIfStillInactive)
        unschedule(clearEngagedIfStillInactive)
        state.asleepStartedAt = now()
        setOccupiedFlag(false)
        setEngagedFlag(false)
        state.nightActive = false
        componentSwitchOff("Engaged")
        componentSwitchOn("Asleep")
        publishNightLighting(false, 0)
    } else {
        unschedule(clearNightLightingIfStillAsleep)
        state.asleepStartedAt = null
        state.nightActive = false
        componentSwitchOff("Asleep")
        publishNightLighting(false, 0)
        refreshCourtesyState()
    }

    recomputeAndPublish()
}

private Boolean shouldWakeFromSleepOnBedExit() {
    if (wakeFromSleepOnBedExitInDayMode == false) return false
    if (currentLocationModeName() != "Day") return false

    Long asleepStarted = safeLong(state.asleepStartedAt, 0L)
    if (!asleepStarted) return false

    Long graceMs = sleepWakeGraceSeconds() * 1000L
    Long elapsedMs = now() - asleepStarted
    if (elapsedMs < graceMs) {
        debug "Bed-exit motion is inside sleep wake grace period (${Math.round(elapsedMs / 60000G)} of ${sleepWakeGraceMinutesValue()} minutes)"
        return false
    }

    return true
}

private void wakeFromSleepOnBedExit(String reason) {
    unschedule(clearNightLightingIfStillAsleep)
    setAsleepFlag(false)
    state.asleepStartedAt = null
    state.nightActive = false
    componentSwitchOff("Asleep")
    publishNightLighting(false, 0)

    recordActivity(reason)
    state.roomLevel = currentRoomControlLevel()
    setOccupiedFlag(true)
    recomputeAndPublish()
}

private void setLocked(Boolean locked, String reason, Boolean clearAsleepOnUnlock = true) {
    debug "Locked ${locked}: ${reason}"

    setLockedFlag(locked)

    if (locked) {
        pauseCircadianReference("room locked")
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

        if (clearAsleepOnUnlock && asleepFlag()) {
            setAsleepFlag(false)
            state.asleepStartedAt = null
            state.nightActive = false
            componentSwitchOff("Asleep")
            publishNightLighting(false, 0)
        }

        if (!asleepFlag()) {
            if (hasRecentActivityWithinOccupiedTimeout()) {
                setOccupiedFlag(true)
                if (!engagedFlag()) {
                    scheduleOccupiedTimeout("locked cleared with recent activity")
                }
            } else {
                setOccupiedFlag(false)
                setEngagedFlag(false)
                componentSwitchOff("Engaged")
                state.lastInactiveAt = null
                state.lastActivityAt = null
                state.lastEngagedInactiveAt = null
            }
        }

        refreshCourtesyState()
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

    setOccupiedFlag(false)
    setEngagedFlag(false)
    setAsleepFlag(false)
    state.asleepStartedAt = null
    state.nightActive = false
    setLockedFlag(false)
    componentSwitchOff("Engaged")
    componentSwitchOff("Asleep")
    componentSwitchOff("Locked")
    publishNightLighting(false, 0)

    refreshCourtesyState()
    recomputeAndPublish()
}

private void clearPresenceFromLocationMode(String modeName) {
    String reason = "Location Mode ${modeName}"
    debug "Clearing room presence from ${reason}"

    unschedule(clearOccupiedIfStillInactive)
    unschedule(clearEngagedIfStillInactive)
    unschedule(clearNightLightingIfStillAsleep)

    setOccupiedFlag(false)
    setEngagedFlag(false)
    setAsleepFlag(false)
    state.asleepStartedAt = null
    state.nightActive = false
    state.courtesy = false
    state.courtesyReason = null
    state.offReason = "Off from ${reason}"
    state.lastInactiveAt = now()
    state.lastEngagedInactiveAt = null

    componentSwitchOff("Engaged")
    componentSwitchOff("Asleep")
    publishNightLighting(false, 0)

    recomputeAndPublish()
}

private void activateNightLighting(Integer seconds, String reason) {
    if (!bedroomProfile()) {
        debug "Ignoring Night lighting request because room profile is not Bedroom"
        publishNightLighting(false, 0)
        return
    }
    if (!asleepFlag()) {
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

private Integer sleepWakeGraceSeconds() {
    return Math.max(sleepWakeGraceMinutesValue() * 60, 0)
}

private Integer sleepWakeGraceMinutesValue() {
    Integer minutes = 30
    try {
        minutes = sleepWakeGraceMinutes == null ? 30 : sleepWakeGraceMinutes as Integer
    } catch (Exception ignored) {
        minutes = 30
    }
    return Math.max(minutes, 0)
}

private Integer positiveSeconds(value, Integer defaultSeconds) {
    Integer seconds = value ? value as Integer : defaultSeconds
    return seconds > 0 ? seconds : 1
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
    if (!lockedFlag()) {
        debug "Lock auto-clear skipped: already unlocked"
        return
    }

    debug "Lock auto-clear firing"
    componentSwitchOff("Locked")
    setLocked(false, "lock auto-cleared")
}

def clearNightLightingIfStillAsleep() {
    if (!asleepFlag()) {
        debug "Night lighting timeout skipped because room is not asleep"
        return
    }

    state.nightActive = false
    publishNightLighting(false, 0)
    recomputeAndPublish()
}

def clearOccupiedIfStillInactive() {
    if (houseIntentProfile()) {
        debug "Occupied timeout ignored for House Intent profile"
        return
    }

    if (lockedFlag()) {
        debug "Occupied timeout blocked: locked is true"
        return
    }

    if (engagedFlag()) {
        debug "Occupied timeout blocked: engaged is true"
        return
    }

    List activeMotion = activeMotionSensors()
    if (activeMotion) {
        Integer seconds = occupiedTimeoutSeconds()
        debug "Occupied timeout blocked: motion still active (${activeMotionLabels(activeMotion)}); rescheduling in ${seconds} seconds"
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
    setOccupiedFlag(false)
    refreshCourtesyState()
    recomputeAndPublish()
}

def clearEngagedIfStillInactive() {
    if (houseIntentProfile()) {
        debug "Engaged timeout ignored for House Intent profile"
        return
    }

    if (lockedFlag()) {
        debug "Engaged timeout blocked: locked is true"
        return
    }

    List activeMotion = activeMotionSensors()
    if (activeMotion) {
        debug "Engaged timeout blocked: motion still active (${activeMotionLabels(activeMotion)}); rescheduling"
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
    setEngagedFlag(false)
    componentSwitchOff("Engaged")
    scheduleOccupiedTimeout("engaged cleared")
    recomputeAndPublish()
}

private Boolean anyMotionActive() {
    return activeMotionSensors() as Boolean
}

private List activeMotionSensors() {
    return asList(motionSensors).findAll { dev ->
        dev?.currentMotion == "active" && !staleActiveMotion(dev)
    }
}

private Boolean staleActiveMotion(def dev) {
    Integer staleMinutes = staleActiveMotionTimeoutMinutes()
    if (staleMinutes <= 0) return false

    Long lastActivity = motionActivityTimestamp(dev)
    if (!lastActivity) return false

    Long elapsedMs = now() - lastActivity
    Long staleMs = staleMinutes * 60L * 1000L
    if (elapsedMs <= staleMs) return false

    Integer elapsedSeconds = Math.max(Math.ceil(elapsedMs / 1000.0) as Integer, 1)
    debug "Ignoring stale active motion: ${dev.displayName} active for ${minutesRoundedUp(elapsedSeconds)} minutes"
    return true
}

private Long motionActivityTimestamp(def dev) {
    try {
        def state = dev.currentState("motion")
        if (state?.date) return state.date.time as Long
    } catch (Exception ignored) {
    }

    try {
        def last = dev.getLastActivity()
        if (last) return last.time as Long
    } catch (Exception ignored) {
    }

    try {
        def last = dev.lastActivity
        if (last instanceof Date) return last.time as Long
    } catch (Exception ignored) {
    }

    return null
}

private Integer staleActiveMotionTimeoutMinutes() {
    try {
        return Math.max((staleActiveMotionMinutes ?: 0) as Integer, 0)
    } catch (Exception ignored) {
        return 0
    }
}

private String activeMotionLabels(List activeMotion) {
    return activeMotion.collect { it?.displayName ?: it?.name ?: it?.id }.join(", ")
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
    refreshAsleepState()
    refreshLockedState()
    refreshCourtesyState()
}

private void reconcileTimeoutsAfterInitialize() {
    if (lockedFlag()) {
        debug "Initialize reconciliation skipped timeout scheduling because room is locked"
        return
    }

    if (engagedFlag()) {
        scheduleEngagedTimeout("initialize reconciliation")
        return
    }

    if (occupiedFlag() || anyMotionActive()) {
        if (anyMotionActive()) {
            debug "Initialize reconciliation found active motion"
            if (!state.lastActivityAt) {
                state.lastActivityAt = now()
            }
            setOccupiedFlag(true)
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
        setLocked(lockedEnabled(), "refresh locked state", false)
    }
}

private void refreshAsleepState() {
    Boolean asleep = asleepEnabled()
    if (!asleep || asleepFlag() == asleep) return

    debug "Refreshing Asleep state to true"
    setAsleepFlag(true)
    unschedule(clearOccupiedIfStillInactive)
    unschedule(clearEngagedIfStillInactive)
    setOccupiedFlag(false)
    setEngagedFlag(false)
    state.nightActive = false
    componentSwitchOff("Engaged")
    componentSwitchOn("Asleep")
    publishNightLighting(false, 0)
}

private Boolean refreshCourtesyState() {
    if (houseIntentProfile()) {
        Boolean changed = clearCourtesyState()
        debug "Courtesy false because House Intent rooms do not participate in neighbor courtesy"
        return changed
    }

    if (!courtesyEnabled()) {
        Boolean changed = clearCourtesyState()
        debug "Courtesy false because Courtesy switch is off"
        return changed
    }

    if (!courtesyEligibleNow()) {
        Boolean changed = clearCourtesyState()
        debug "Courtesy false because this room is not courtesy-eligible"
        return changed
    }

    Map activeNeighbor = activeNeighborRoom()
    Boolean courtesy = activeNeighbor?.active == true
    String reason = courtesy ? courtesyReason(activeNeighbor) : null
    Boolean changed = state.courtesy != courtesy || state.courtesyReason != reason
    state.courtesy = courtesy
    state.courtesyReason = reason
    debug "Courtesy ${state.courtesy ? 'true' : 'false'} from neighbor Room devices"
    return changed
}

private void setCourtesyFromNeighborEvent(evt) {
    state.courtesy = true
    state.courtesyReason = courtesyReason([
        label    : evt.displayName,
        roomState: evt.value?.toString()
    ])
}

private Boolean clearCourtesyState() {
    Boolean changed = state.courtesy == true || state.courtesyReason != null
    state.courtesy = false
    state.courtesyReason = null
    return changed
}

private Map activeNeighborRoom() {
    if (!parent) return [active: false]
    try {
        return parent.activeNeighborRoomForChildIds(neighborChildAppIds) ?: [active: false]
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not resolve active neighbor room: ${e.message}"
        return [active: false]
    }
}

private String courtesyReason(Map activeNeighbor) {
    String label = activeNeighbor?.label ?: activeNeighbor?.name ?: activeNeighbor?.id
    return label ? "neighboring room ${stripRoomPrefix(label.toString())}" : "neighboring room"
}

private Boolean courtesyEligibleNow() {
    return !houseIntentProfile() &&
        courtesyEnabled() &&
        !lockedFlag() &&
        !asleepFlag() &&
        !engagedFlag() &&
        !occupiedFlag() &&
        !hasRecentActivityWithinOccupiedTimeout()
}

private Boolean courtesyEligibleFromNeighborEvent() {
    if (!courtesyEligibleNow()) return false

    String publishedRoomState = roomDevice()?.currentValue("roomState")?.toString()
    return !(publishedRoomState in ["Occupied", "Engaged", "Locked", "Asleep"])
}

private String stripRoomPrefix(String value) {
    return value?.startsWith("Room ") ? value.substring(5) : value
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

// -------------------- State Computation and Output --------------------

private String computeRoomState() {
    return computeRoomState(lockedFlag(), asleepFlag(), engagedFlag(), occupiedFlag())
}

private String computeRoomState(Boolean lockedNow, Boolean asleepNow, Boolean engagedNow, Boolean occupiedNow) {
    if (houseIntentProfile()) return "Occupied"
    if (lockedNow) return "Locked"
    if (asleepNow) return "Asleep"
    if (engagedNow) return "Engaged"
    if (occupiedNow) return "Occupied"
    return "Off"
}

private String computeLightingIntent(String roomState) {
    if (houseIntentProfile()) return "On"
    if (roomState in ["Locked", "Engaged", "Occupied"]) return "On"
    if (roomState == "Asleep") return state.nightActive ? "Night" : "Off"
    if (state.courtesy) return "Courtesy"
    return "Off"
}

private Integer computeLightingLevel(String lightingIntent) {
    return computeLightingLevel(lightingIntent, referenceSnapshotForRecompute())
}

private Integer computeLightingLevel(String lightingIntent, Map referenceSnapshot) {
    if (referenceSnapshot?.active == true && lightingIntent == "On") return adjustedReferenceLevel("occupied", 100, 0, referenceSnapshot.level as Integer)
    if (referenceSnapshot?.active == true && lightingIntent == "Courtesy") return adjustedReferenceLevel("courtesy", 30, 0, referenceSnapshot.level as Integer)
    if (lightingIntent == "On") return currentRoomControlLevel()
    if (lightingIntent == "Courtesy") return configuredCourtesyLightingLevel()
    if (lightingIntent == "Night") return configuredNightLightingLevel()
    return 0
}

private Integer lockedMetaLightOnLevel() {
    return lockedMetaLightOnLevel(referenceSnapshotForRecompute())
}

private Integer lockedMetaLightOnLevel(Map referenceSnapshot) {
    return referenceSnapshot?.active == true ? adjustedReferenceLevel("occupied", 100, 0, referenceSnapshot.level as Integer) : nextRoomControlLevel()
}

private Integer effectiveMetaLightColorTemperature() {
    return effectiveMetaLightColorTemperature(referenceSnapshotForRecompute())
}

private Integer effectiveMetaLightColorTemperature(Map referenceSnapshot) {
    if (referenceSnapshot?.active == true) return normalizedColorTemperature(referenceSnapshot.ct, 2700)
    return normalizedColorTemperature(state.metaLightColorTemperature, 2700)
}

private Integer adjustedReferenceLevel(String prefix, Integer fallbackPercent, Integer fallbackOffset) {
    return adjustedReferenceLevel(prefix, fallbackPercent, fallbackOffset, currentReferenceLevel())
}

private Integer adjustedReferenceLevel(String prefix, Integer fallbackPercent, Integer fallbackOffset, Integer referenceLevel) {
    Integer normalizedReferenceLevel = normalizedPercent(referenceLevel, 100)
    Integer percent = referencePercent(prefix, fallbackPercent)
    Integer offset = referenceOffset(prefix, fallbackOffset)
    BigDecimal adjusted = ((normalizedReferenceLevel as BigDecimal) * (percent as BigDecimal) / 100G) + (offset as BigDecimal)
    return normalizedPercent(adjusted.setScale(0, BigDecimal.ROUND_HALF_UP) as Integer, fallbackPercent)
}

private Integer referencePercent(String prefix, Integer fallback) {
    Integer modeValue = modeBasedReferenceInteger("${prefix}ReferencePercent")
    if (modeValue != null) return normalizedPercent(modeValue, fallback)

    def value = prefix == "occupied" ? occupiedReferencePercent : courtesyReferencePercent
    return normalizedPercent(value, fallback)
}

private Integer referenceOffset(String prefix, Integer fallback) {
    Integer modeValue = modeBasedReferenceInteger("${prefix}ReferenceOffset")
    if (modeValue != null) return normalizedOffset(modeValue, fallback)

    def value = prefix == "occupied" ? occupiedReferenceOffset : courtesyReferenceOffset
    return normalizedOffset(value, fallback)
}

private Integer modeBasedReferenceInteger(String prefix) {
    if (!useModeBasedLightingLevels) return null

    String modeName = currentLocationModeName()
    if (!modeName) return null

    def value = settings[modeLevelSettingName(prefix, modeName)]
    if (value == null || "${value}".trim() == "") return null
    return safeInteger(value, null)
}

private Integer currentReferenceLevel() {
    return normalizedPercent(circadianReferenceDevice()?.currentValue("level"), 100)
}

private Integer currentReferenceColorTemperature() {
    return normalizedColorTemperature(circadianReferenceDevice()?.currentValue("colorTemperature"), normalizedColorTemperature(state.metaLightColorTemperature, 2700))
}

private Map parentReferenceSnapshot(Integer referenceLevel, Integer referenceColorTemperature) {
    if (referenceLevel == null && referenceColorTemperature == null) return null
    return [
        active: true,
        level : normalizedPercent(referenceLevel, 100),
        ct    : normalizedColorTemperature(referenceColorTemperature, normalizedColorTemperature(state.metaLightColorTemperature, 2700)),
        source: "parent-push"
    ]
}

private Map referenceSnapshotForRecompute(Map preferredReference = null) {
    if (!circadianReferenceTrackingActiveForRecompute()) {
        return [active: false, level: null, ct: null, source: "inactive"]
    }

    if (preferredReference?.active == true) {
        return preferredReference
    }

    if (circadianReferenceBulb) {
        return [
            active: true,
            level : normalizedPercent(circadianReferenceBulb.currentValue("level"), 100),
            ct    : normalizedColorTemperature(circadianReferenceBulb.currentValue("colorTemperature"), normalizedColorTemperature(state.metaLightColorTemperature, 2700)),
            source: "local-device"
        ]
    }

    try {
        return [
            active: true,
            level : normalizedPercent(parent.cachedCircadianReferenceLevel(), 100),
            ct    : normalizedColorTemperature(parent.cachedCircadianReferenceColorTemperature(), normalizedColorTemperature(state.metaLightColorTemperature, 2700)),
            source: "parent-cache"
        ]
    } catch (Exception e) {
        debug "Could not read parent circadian reference cache; falling back to reference device: ${e.message}"
        return [
            active: true,
            level : currentReferenceLevel(),
            ct    : currentReferenceColorTemperature(),
            source: "fallback-device"
        ]
    }
}

private Map roomReferenceTraceSnapshot() {
    return [
        active: true,
        level : normalizedPercent(state.metaLightLevel, 0),
        ct    : normalizedColorTemperature(state.metaLightColorTemperature, 2700),
        intent: state.lightingIntent ?: "Off",
        sw    : state.metaLightSwitch ?: "off"
    ]
}

private String formatReferenceTrace(Map trace) {
    if (!trace) return "null"
    return "active=${trace.active}, source=${trace.source ?: 'n/a'}, sw=${trace.sw ?: 'n/a'}, intent=${trace.intent ?: 'n/a'}, level=${trace.level ?: 'null'}, ct=${trace.ct ?: 'null'}"
}

private Boolean followCircadianReferenceEnabled() {
    return followCircadianReferenceSettingEnabled() && circadianReferenceDevice()
}

private Boolean circadianReferenceTrackingActive() {
    return followCircadianReferenceEnabled() && state.circadianReferencePaused != true
}

private Boolean circadianReferenceTrackingActiveForRecompute() {
    if (!followCircadianReferenceSettingEnabled()) return false
    if (state.circadianReferencePaused == true) return false
    if (circadianReferenceBulb) return true

    try {
        if (parent.cachedCircadianReferenceAvailable() == true) return true
        return circadianReferenceDevice() != null
    } catch (Exception ignored) {
        return circadianReferenceDevice() != null
    }
}

private void reconcileCircadianPauseWithRoomDevice(String reason) {
    if (state.circadianReferencePaused != true) return

    try {
        if (roomDevice()?.currentValue("customLighting") == "on") return
    } catch (Throwable ignored) {
        return
    }

    debug "Clearing stale circadian reference pause because custom lighting is off: ${reason}"
    state.circadianReferencePaused = false
    state.keepCircadianReferencePausedForIntentChange = false
}

private void pauseCircadianReference(String reason) {
    if (!followCircadianReferenceEnabled()) return
    if (state.circadianReferencePaused == true) return
    debug "Pausing circadian reference tracking: ${reason}"
    state.circadianReferencePaused = true
    state.keepCircadianReferencePausedForIntentChange = true
}

private void resumeCircadianReference(String reason) {
    if (!followCircadianReferenceEnabled()) return
    if (state.circadianReferencePaused != true) return
    debug "Resuming circadian reference tracking: ${reason}"
    state.circadianReferencePaused = false
}

private def circadianReferenceDevice() {
    if (circadianReferenceBulb) return circadianReferenceBulb

    try {
        return parent.circadianReferenceBulb()
    } catch (Exception ignored) {
        return null
    }
}

private Boolean metaLightIntentActive() {
    if (lockedFlag()) return state.metaLightSwitch == "on"
    return (state.lightingIntent ?: "Off") in ["On", "Courtesy", "Night"]
}

private Integer currentRoomControlLevel() {
    if (state.forceModeLightingLevel) {
        Integer configuredLevel = configuredOccupiedLightingLevel()
        if (configuredLevel != null) return configuredLevel
    }

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

private void recomputeAndPublish(Map timing = null, Map preferredReference = null) {
    Long localStartedAt = timing ? null : now()
    ensureInitialState()
    markRoomStateTiming(timing, "ensureInitialState")

    Boolean lockedNow = lockedFlag()
    Boolean asleepNow = asleepFlag()
    Boolean engagedNow = engagedFlag()
    Boolean occupiedNow = occupiedFlag()
    String previousLightingIntent = state.lightingIntent ?: "Off"
    Boolean previousSwitchOn = roomDevice()?.currentSwitch == "on"
    Integer previousRoomLevel = normalizedPercent(roomDevice()?.currentValue("level"), 0)
    Integer previousMetaLightLevel = normalizedPercent(state.metaLightLevel, 0)
    Integer previousMetaLightColorTemperature = normalizedColorTemperature(state.metaLightColorTemperature, 2700)

    String newRoomState = computeRoomState(lockedNow, asleepNow, engagedNow, occupiedNow)
    String newLightingIntent = lockedNow ? previousLightingIntent : computeLightingIntent(newRoomState)

    if (previousLightingIntent != newLightingIntent && state.circadianReferencePaused == true) {
        if (state.keepCircadianReferencePausedForIntentChange == true) {
            debug "Keeping circadian reference tracking paused for current lighting intent change"
        } else {
            resumeCircadianReference("lighting intent changed from ${previousLightingIntent} to ${newLightingIntent}")
            clearCustomLightingSilently("lighting intent changed from ${previousLightingIntent} to ${newLightingIntent}")
        }
    }
    state.keepCircadianReferencePausedForIntentChange = false

    Map referenceSnapshot = referenceSnapshotForRecompute(preferredReference)
    markRoomStateTiming(timing, "reference")

    Boolean lockedReferenceUpdate = lockedNow && state.forceCircadianReferenceUpdate && state.metaLightSwitch == "on"
    Integer effectiveLightingLevel = lockedNow && !lockedReferenceUpdate ? previousMetaLightLevel : computeLightingLevel(newLightingIntent, referenceSnapshot)
    if (lockedReferenceUpdate && effectiveLightingLevel == 0) {
        effectiveLightingLevel = lockedMetaLightOnLevel(referenceSnapshot)
    }
    Integer effectiveColorTemperature = lockedNow && !lockedReferenceUpdate ? previousMetaLightColorTemperature : effectiveMetaLightColorTemperature(referenceSnapshot)
    Integer roomControlLevel = lockedNow ? previousRoomLevel : (newRoomState in ["Occupied", "Engaged"] ? effectiveLightingLevel : 0)

    // Locking should not change the public switch. It is a freeze/hold state, not an on/off request.
    Boolean roomSwitchShouldBeOn = lockedNow ? previousSwitchOn : (newRoomState in ["Occupied", "Engaged"])
    Boolean metaLightShouldBeOn = lockedNow ? (state.metaLightSwitch == "on") : (newLightingIntent in ["On", "Courtesy", "Night"])
    String stateReason = computeStateReason(newRoomState, newLightingIntent)
    markRoomStateTiming(timing, "compute")

    publishMetaLightDevice(metaLightShouldBeOn, effectiveLightingLevel, effectiveColorTemperature)
    markRoomStateTiming(timing, "publishMetaLight")
    publishRoomDevice(roomSwitchShouldBeOn, newRoomState, newLightingIntent, roomControlLevel, stateReason)
    markRoomStateTiming(timing, "publishRoom")

    if (state.roomState != newRoomState || state.lightingIntent != newLightingIntent) {
        log.info "${roomDeviceLabel()}: roomState=${newRoomState}, lightingIntent=${newLightingIntent}, stateReason=${stateReason}"
    }

    state.roomState = newRoomState
    state.lightingIntent = newLightingIntent
    state.stateReason = stateReason

    if (!timing && localStartedAt) {
        Long elapsed = now() - localStartedAt
        if (elapsed >= roomStateTimingWarnMs()) {
            log.warn "${roomDeviceLabel()}: recomputeAndPublish took ${elapsed}ms"
        }
    }
}

private String computeStateReason(String roomState, String lightingIntent) {
    if (houseIntentProfile()) return "House Intent reference active"
    if (roomState == "Locked") return "Locked"
    if (roomState == "Asleep") return lightingIntent == "Night" ? "Asleep with Night lighting" : "Asleep"
    if (roomState == "Engaged") return "Engaged from ${state.lastActivityReason ?: 'activity'}"
    if (roomState == "Occupied") return "Occupied from ${state.lastActivityReason ?: 'activity'}"
    if (lightingIntent == "Courtesy") return "Courtesy from ${state.courtesyReason ?: 'neighboring room'}"
    return state.offReason ?: "Off"
}

private void publishRoomDevice(Boolean switchOn, String roomState, String lightingIntent, Integer lightingLevel, String stateReason) {
    def dev = roomDevice()
    if (!dev) return

    try {
        if (dev.currentValue("roomState") != roomState) {
            dev.setRoomState(roomState)
        }
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not set roomState on room device: ${e.message}"
    }

    try {
        if (dev.currentValue("lightingIntent") != lightingIntent) {
            dev.setLightingIntent(lightingIntent)
        }
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not set lightingIntent on room device: ${e.message}"
    }

    try {
        String switchValue = switchOn ? "on" : "off"
        if (dev.currentValue("switch") != switchValue) {
            dev.setSwitchState(switchValue)
        }
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not set switch state on room device: ${e.message}"
    }

    try {
        if (normalizedPercent(dev.currentValue("level"), -1) != lightingLevel) {
            dev.setRoomLevel(lightingLevel)
        }
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not set lighting level on room device: ${e.message}"
    }

    try {
        if (dev.currentValue("stateReason") != stateReason) {
            dev.setStateReason(stateReason)
        }
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not set state reason on room device: ${e.message}"
    }
}

private void publishMetaLightDevice(Boolean switchOn, Integer lightingLevel, Integer colorTemperature) {
    def dev = roomDevice()
    if (!dev) return

    try {
        if (switchOn) {
            Integer level = lightingLevel
            if (normalizedPercent(dev.currentValue("metaLightLevel"), -1) != level) {
                dev.setMetaLightLevel(level)
            }
            state.metaLightLevel = level
        }
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not set level on meta-light device: ${e.message}"
    }

    try {
        Integer ct = colorTemperature
        if (normalizedColorTemperature(dev.currentValue("metaLightColorTemperature"), -1) != ct) {
            dev.setMetaLightColorTemperature(ct)
        }
        state.metaLightColorTemperature = ct
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not set color temperature on meta-light device: ${e.message}"
    }

    try {
        String switchValue = switchOn ? "on" : "off"
        if (dev.currentValue("metaLightSwitch") != switchValue) {
            dev.setMetaLightSwitchState(switchValue)
        }
        state.metaLightSwitch = switchValue
    } catch (Exception e) {
        log.warn "${roomDeviceLabel()}: Could not set switch state on meta-light device: ${e.message}"
    }
}


// -------------------- Logging --------------------

private Map startRoomStateTiming(String operation) {
    return [operation: operation, startedAt: now(), marks: []]
}

private void markRoomStateTiming(Map timing, String label) {
    if (!timing || !label) return
    Long startedAt = safeLong(timing.startedAt, 0L)
    if (!startedAt) return

    List marks = timing.marks instanceof List ? timing.marks : []
    marks << "${label}=${now() - startedAt}ms"
    timing.marks = marks
}

private void logRoomStateTiming(Map timing, String detail) {
    if (!timing) return

    Long startedAt = safeLong(timing.startedAt, 0L)
    if (!startedAt) return

    Long elapsed = now() - startedAt
    if (elapsed < roomStateTimingWarnMs()) return

    List marks = timing.marks instanceof List ? timing.marks : []
    String operation = timing.operation ?: "roomState"
    log.warn "${roomDeviceLabel()}: ${operation} took ${elapsed}ms (${detail}; ${marks.join(', ')})"
}

private Integer roomStateTimingWarnMs() {
    return 750
}

private void debug(String msg) {
    if (debugEnabled(debugLogging)) {
        log.debug "${roomDeviceLabel()}: ${msg}"
    }
}
