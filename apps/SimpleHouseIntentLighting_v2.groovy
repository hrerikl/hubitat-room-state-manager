/**
 * Simple House Intent Lighting - Child App
 *
 * Install as Apps Code:
 *   Name: Simple House Intent Lighting
 *   Namespace: lundby
 *
 * Controls a House Intent room reference with scene, level, and CT inputs.
 */

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

preferences {
    page(name: "mainPage", title: "Simple House Intent Lighting", install: true, uninstall: true) {
        section("House Intent") {
            input "roomChildAppId", "enum", title: "House Intent room", options: roomOptions(), required: true, submitOnChange: true
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
    subscribe(picoRemotes, "pushed", picoPushedHandler)
    subscribe(picoRemotes, "held", picoHeldHandler)
    def recovery = recoveryDevice()
    if (recovery) {
        subscribe(recovery, "switch.on", recoverSimpleHomeHandler)
    }
    ensurePendingFromRoom()
}

def configureHouseIntentLightingFromParent(def houseIntentChildAppId) {
    try {
        app.updateSetting("roomChildAppId", [type: "enum", value: houseIntentChildAppId?.toString()])
        app.updateSetting("commitDelaySeconds", [type: "number", value: 10])
        app.updateSetting("levelStep", [type: "number", value: 10])
        app.updateSetting("colorTemperatureStep", [type: "number", value: 250])
        unsubscribe()
        unschedule()
        initialize()
        return true
    } catch (Exception e) {
        log.warn "${app.label}: Could not configure House Intent Lighting from parent: ${e.message}"
        return false
    }
}

def recoverSimpleHomeHandler(evt) {
    commitPendingIntent("Simple Home recovery")
}

// -------------------- Pico Controls --------------------

def picoPushedHandler(evt) {
    Integer button = eventIntegerValue(evt)
    debug "Pico pushed ${button}: ${evt.displayName}"

    if (button == 1) {
        adjustColorTemperature(-ctStep())
    } else if (button == 2) {
        adjustLevel(levelStepValue())
    } else if (button == 3) {
        cycleScene()
    } else if (button == 4) {
        adjustLevel(-levelStepValue())
    } else if (button == 5) {
        adjustColorTemperature(ctStep())
    } else {
        debug "No House Intent Pico action for button ${button}"
    }
}

def picoHeldHandler(evt) {
    Integer button = eventIntegerValue(evt)
    debug "Pico held ${button}: ${evt.displayName}"

    if (button == 3) {
        returnToHouseReference()
    }
}

// -------------------- Scene Logic --------------------

private void cycleScene() {
    List scenes = builtInScenes()
    Integer index = ((state.sceneIndex ?: -1) as Integer) + 1
    if (index >= scenes.size()) index = 0

    Map scene = scenes[index]
    state.sceneIndex = index
    state.pendingLevel = scene.level as Integer
    state.pendingCt = scene.ct as Integer
    state.pendingSceneName = scene.name
    state.pendingCustom = true

    applyPreview(scene.name as String)
    announceScene(scene.name as String)
    scheduleCommit("scene ${scene.name}")
}

private void adjustLevel(Integer delta) {
    ensurePendingFromRoom()
    Integer level = normalizedLevel((state.pendingLevel ?: 50) as Integer + delta)
    state.pendingLevel = level
    state.pendingCustom = true
    state.pendingSceneName = "Custom"

    applyPreview("Custom")
    announceManualAdjustment("House Lighting set to ${level}%.")
    scheduleCommit("level adjusted")
}

private void adjustColorTemperature(Integer delta) {
    ensurePendingFromRoom()
    Integer ct = normalizedColorTemperature((state.pendingCt ?: 2700) as Integer + delta)
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
    roomDevice()?.clearCustomLighting()
    announceScene("Follow House")
    debug "Returned House Intent to automatic reference"
}

private void applyPreview(String sceneName) {
    Integer level = normalizedLevel(state.pendingLevel ?: currentRoomLevel())
    Integer ct = normalizedColorTemperature(state.pendingCt ?: currentRoomCt())

    def dev = previewDevice
    if (!dev) return

    try {
        dev.setColorTemperature(ct, level)
    } catch (Exception ignored) {
        try {
            dev.setColorTemperature(ct)
            dev.setLevel(level)
        } catch (Exception e) {
            log.warn "${app.label}: Could not apply preview ${sceneName}: ${e.message}"
        }
    }
}

private void scheduleCommit(String reason) {
    Integer seconds = Math.max(safeInteger(commitDelaySeconds, 10), 1)
    debug "Scheduling House Intent commit in ${seconds} seconds: ${reason}"
    runIn(seconds, commitPendingIntent, [overwrite: true])
}

def commitPendingIntent(String reason = "scheduled") {
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
    } catch (Exception e) {
        log.warn "${app.label}: Could not commit House Intent level=${level}, ct=${ct}: ${e.message}"
    }
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

// -------------------- Helpers --------------------

private void ensurePendingFromRoom() {
    if (state.pendingLevel == null) state.pendingLevel = currentRoomLevel()
    if (state.pendingCt == null) state.pendingCt = currentRoomCt()
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

private Map roomOptions() {
    def parentApp = parent
    if (!parentApp) return [:]

    try {
        return parentApp.roomStateChildOptions(app?.id) ?: [:]
    } catch (Exception e) {
        log.warn "${app.label}: Could not load room options: ${e.message}"
        return [:]
    }
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

private Integer ctStep() {
    return Math.max(safeInteger(colorTemperatureStep, 250), 1)
}

private Integer eventIntegerValue(evt) {
    try {
        return evt.value as Integer
    } catch (Exception ignored) {
        return null
    }
}

private Integer normalizedLevel(value) {
    return Math.max(Math.min(safeInteger(value, 50), 100), 0)
}

private Integer normalizedColorTemperature(value) {
    Integer ct = 2700
    try {
        ct = (value == null ? 2700 : value) as Integer
    } catch (Exception ignored) {
        ct = 2700
    }
    return Math.max(Math.min(ct, 10000), 1500)
}

private Integer safeInteger(value, Integer fallback) {
    try {
        return value == null ? fallback : value as Integer
    } catch (Exception ignored) {
        return fallback
    }
}

private List asList(value) {
    if (!value) return []
    return value instanceof List ? value : [value]
}

private void debug(String message) {
    if (debugLogging) {
        log.debug "${app.label}: ${message}"
    }
}
