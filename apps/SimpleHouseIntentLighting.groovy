/*****************************************************************************************
 * Simple House Intent Lighting - Child App
 *
 * Install as Apps Code:
 *   Name: Simple House Intent Lighting
 *   Namespace: lundby
 *
 * Controls a House Intent room reference with scene, level, and CT inputs.
 *****************************************************************************************/

definition(
    name: "Simple House Intent Lighting",
    namespace: "lundby",
    author: "Erik Lundby / ChatGPT",
    description: "House-level lighting intent controls with delayed commit and preview feedback.",
    category: "Convenience",
    parent: "lundby:Simple Home",
    singleInstance: false,
    iconUrl: "",
    iconX2Url: ""
)

#include lundby.SimpleHomeHelpers

preferences {
    page(name: "mainPage", title: "Simple House Intent Lighting", install: true, uninstall: true) {
        section("House Intent") {
            input "roomChildAppId", "enum", title: "House Intent room", options: roomOptions(), required: false, submitOnChange: true
            input "lightingName", "text", title: "Lighting app name override, optional", required: false
        }

        section("Preview") {
            input "previewDevice", "capability.colorTemperature", title: "Preview/feedback device", multiple: false, required: false
            input "commitDelaySeconds", "number", title: "Commit after inactivity seconds", defaultValue: 10, required: true
        }

        section("Controls") {
            input "picoRemotes", "capability.pushableButton", title: "House Intent Pico remotes", multiple: true, required: false
            input "levelStep", "number", title: "Level step", defaultValue: 10, required: true
            input "colorTemperatureStep", "number", title: "Color temperature step", defaultValue: 250, required: true
        }

        section("Speech") {
            input "speechDevices", "capability.speechSynthesis", title: "Speech devices for scene names", multiple: true, required: false
            input "announceScenes", "bool", title: "Announce scene names", defaultValue: true, required: true
            input "announceManualAdjustments", "bool", title: "Announce manual level and color temperature changes", defaultValue: true, required: true
        }

        section("Debug") {
            input "debugLogging", "bool", title: "Enable debug logging", defaultValue: true, required: true
            paragraph "Simple Home can suppress or force debug logging from the parent app."
        }
    }
}

// -------------------- Lifecycle --------------------

def installed() {
    log.info "Installed ${app.label}"
    initialize()
}

def updated() {
    log.info "Updated ${app.label}"
    unsubscribe()
    unschedule()
    initialize()
}

def reinitializeFromParent() {
    log.info "Reinitializing ${app.label} from parent"
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    updateAppLabel()
    if (!houseIntentLightingAllowed()) {
        markDuplicateForDelete("House Intent Lighting")
        log.error "${app.label}: Duplicate House Intent Lighting app. Delete this child app and keep the parent-created primary app."
        return
    }

    subscribe(picoRemotes, "pushed", "picoPushedHandler")
    subscribe(picoRemotes, "held", "picoHeldHandler")
    def recovery = recoveryDevice()
    if (recovery) {
        subscribe(recovery, "switch.on", recoverSimpleHomeHandler)
    }
    ensurePendingFromRoom()
}

def configureHouseIntentLightingFromParent(def houseIntentChildAppId) {
    try {
        app.updateSetting("roomChildAppId", [type: "enum", value: houseIntentChildAppId?.toString()])
        initialize()
        return true
    } catch (Exception e) {
        log.warn "${app.label}: Could not configure House Intent Lighting from parent: ${e.message}"
        return false
    }
}

def getManagedRoomDevice() {
    return roomDevice()
}

def getManagedRoomDeviceLabel() {
    def room = roomDevice()
    return room?.displayName ?: room?.label ?: ""
}

def getSimpleHomeAppType() {
    return "houseIntentLighting"
}

def getRoomProfile() {
    return null
}

def getConfiguredRoomName() {
    return null
}

def getConfiguredRoomChildAppId() {
    return roomChildAppId
}

private Boolean houseIntentLightingAllowed() {
    try {
        return parent.houseIntentLightingAllowed(app.id) != false
    } catch (Throwable e) {
        log.warn "${app.label}: Could not validate House Intent Lighting uniqueness: ${e.message}"
        return true
    }
}

def recoverSimpleHomeHandler(evt) {
    state.commitReason = "Simple Home recovery"
    commitPendingIntent()
}

def picoPushedHandler(evt) {
    Integer button = eventIntegerValue(evt)
    Integer step = levelStepValue()
    Integer ctStepValue = colorTemperatureStepValue()
    debug "Pico pushed ${button}: ${evt?.displayName}"

    if (button == 1) {
        adjustColorTemperature(0 - ctStepValue)
    } else if (button == 2) {
        adjustLevel(step)
    } else if (button == 3) {
        cycleScene()
    } else if (button == 4) {
        adjustLevel(0 - step)
    } else if (button == 5) {
        adjustColorTemperature(ctStepValue)
    } else {
        debug "No House Intent Pico push action for button ${button}"
    }
}

def picoHeldHandler(evt) {
    Integer button = eventIntegerValue(evt)
    debug "Pico held ${button}: ${evt?.displayName}"

    if (button == 3) {
        returnToHouseReference()
    }
}

def commitPendingIntent() {
    String reason = state.commitReason ?: "scheduled"
    def room = roomDevice()
    if (!room) {
        log.warn "${app.label}: Cannot commit House Intent because no room is selected."
        return
    }

    Integer level = normalizedLevel(state.pendingLevel ?: currentRoomLevel())
    Integer ct = normalizedColorTemperature(state.pendingCt ?: currentRoomCt())

    try {
        room.activateCustomLighting()
        room.setMetaLightSwitchState(level > 0 ? "on" : "off")
        room.setMetaLightColorTemperature(ct)
        room.setMetaLightLevel(level)
        debug "Committed House Intent level=${level}, ct=${ct}: ${reason}"
        state.remove("commitReason")
    } catch (Exception e) {
        log.warn "${app.label}: Could not commit House Intent level=${level}, ct=${ct}: ${e.message}"
    }
}

// -------------------- Helpers --------------------

private void cycleScene() {
    List scenes = builtInScenes()
    Integer currentIndex = safeInteger(state.sceneIndex, -1)
    Integer index = currentIndex + 1
    if (index >= scenes.size()) index = 0

    Map scene = scenes[index]
    state.sceneIndex = index
    state.pendingLevel = safeInteger(scene.level, 50)
    state.pendingCt = safeInteger(scene.ct, 2700)
    state.pendingSceneName = scene.name
    state.pendingCustom = true

    String sceneName = scene.name as String
    applyPreview(sceneName)
    announceScene(sceneName)
    scheduleCommit("scene ${sceneName}")
}

private void adjustLevel(Integer delta) {
    ensurePendingFromRoom()
    Integer current = safeInteger(state.pendingLevel, 50)
    Integer level = normalizedLevel(current + delta)
    state.pendingLevel = level
    state.pendingCustom = true
    state.pendingSceneName = "Custom"

    applyPreview("Custom")
    announceManualAdjustment("House Lighting set to ${level}%.")
    scheduleCommit("level adjusted")
}

private void adjustColorTemperature(Integer delta) {
    ensurePendingFromRoom()
    Integer current = safeInteger(state.pendingCt, 2700)
    Integer ct = normalizedColorTemperature(current + delta)
    state.pendingCt = ct
    state.pendingCustom = true
    state.pendingSceneName = "Custom"

    applyPreview("Custom")
    announceManualAdjustment("House Color Temperature set to ${colorTemperatureName(ct)}.")
    scheduleCommit("color temperature adjusted")
}

private void returnToHouseReference() {
    state.pendingCustom = false
    state.pendingSceneName = "Follow House"
    try {
        roomDevice()?.clearCustomLighting()
    } catch (Exception e) {
        log.warn "${app.label}: Could not return House Intent to automatic reference: ${e.message}"
        return
    }
    announceScene("Follow House")
    debug "Returned House Intent to automatic reference"
}

private void ensurePendingFromRoom() {
    if (state.pendingLevel == null) state.pendingLevel = currentRoomLevel()
    if (state.pendingCt == null) state.pendingCt = currentRoomCt()
}

private void applyPreview(String reason) {
    def dev = previewDevice
    if (!dev) return

    Integer level = normalizedLevel(state.pendingLevel ?: currentRoomLevel())
    Integer ct = normalizedColorTemperature(state.pendingCt ?: currentRoomCt())

    try {
        dev.setColorTemperature(ct, level)
    } catch (Exception ignored) {
        try {
            dev.setColorTemperature(ct)
            dev.setLevel(level)
        } catch (Exception e) {
            log.warn "${app.label}: Could not apply preview for ${reason}: ${e.message}"
        }
    }
}

private void scheduleCommit(String reason) {
    Integer seconds = Math.max(safeInteger(commitDelaySeconds, 10), 1)
    state.commitReason = reason
    debug "Scheduling House Intent commit in ${seconds} seconds: ${reason}"
    runIn(seconds, "commitPendingIntent", [overwrite: true])
}

private List builtInScenes() {
    return [
        [name: "Calm", level: 35, ct: 2400],
        [name: "Reading", level: 65, ct: 3000],
        [name: "Bright", level: 85, ct: 4000],
        [name: "Cleaning", level: 100, ct: 5000],
        [name: "Party", level: 55, ct: 2700],
        [name: "Night", level: 10, ct: 2200]
    ]
}

private Integer currentRoomLevel() {
    return normalizedLevel(roomDevice()?.currentValue("metaLightLevel") ?: roomDevice()?.currentValue("level") ?: 50)
}

private Integer currentRoomCt() {
    return normalizedColorTemperature(roomDevice()?.currentValue("metaLightColorTemperature") ?: roomDevice()?.currentValue("colorTemperature") ?: 2700)
}

private def roomDevice() {
    try {
        def parentApp = parent
        if (!parentApp) return null
        if (!roomChildAppId) return null
        return parentApp.roomStateChildRoomDevice(roomChildAppId)
    } catch (Throwable ignored) {
        return null
    }
}

private def recoveryDevice() {
    try {
        def parentApp = parent
        if (!parentApp) return null
        return parentApp.recoveryDevice()
    } catch (Throwable ignored) {
        return null
    }
}

private void updateAppLabel() {
    String desired = lightingName?.trim()
    if (!desired) desired = "# House Intent Lighting"
    if (app.label != desired) {
        app.updateLabel(desired)
    }
}

private Integer levelStepValue() {
    return Math.max(safeInteger(levelStep, 10), 1)
}

private Integer colorTemperatureStepValue() {
    return Math.max(safeInteger(colorTemperatureStep, 250), 1)
}

private void announceScene(String sceneName) {
    if (announceScenes == false || !sceneName) return
    asList(speechDevices).each { dev ->
        try {
            dev.speak(sceneName)
        } catch (Exception e) {
            log.warn "${app.label}: Could not announce ${sceneName} on ${dev.displayName}: ${e.message}"
        }
    }
}

private void announceManualAdjustment(String message) {
    if (announceManualAdjustments == false || !message) return
    asList(speechDevices).each { dev ->
        try {
            dev.speak(message)
        } catch (Exception e) {
            log.warn "${app.label}: Could not announce manual House Intent adjustment on ${dev.displayName}: ${e.message}"
        }
    }
}

private String colorTemperatureName(Integer ct) {
    Integer value = normalizedColorTemperature(ct)
    if (value <= 2300) return "Warm Night"
    if (value <= 2700) return "Warm"
    if (value <= 3200) return "Soft White"
    if (value <= 4200) return "Neutral"
    if (value <= 5200) return "Daylight"
    if (value <= 6500) return "Cool Daylight"
    return "${value} kelvin"
}

private void debug(String message) {
    if (debugEnabled(debugLogging)) {
        log.debug "${app.label}: ${message}"
    }
}
