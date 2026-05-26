/*****************************************************************************************
 * Simple House Intent Lighting - Child App
 *
 * Install as Apps Code:
 *   Name: Simple House Intent Lighting
 *   Namespace: lundby
 *
 * Minimal installable shell for House Intent lighting controls.
 *****************************************************************************************/

definition(
    name: "Simple House Intent Lighting",
    namespace: "lundby",
    author: "Erik Lundby / ChatGPT",
    description: "House-level lighting intent controls.",
    category: "Convenience",
    parent: "lundby:Simple Home",
    singleInstance: false,
    iconUrl: "",
    iconX2Url: ""
)

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
    subscribe(picoRemotes, "pushed", "picoPushedHandler")
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

def recoverSimpleHomeHandler(evt) {
    state.commitReason = "Simple Home recovery"
    commitPendingIntent()
}

def picoPushedHandler(evt) {
    debug "Pico pushed ${evt?.value}: ${evt?.displayName}"
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

private void updateAppLabel() {
    String desired = lightingName?.trim()
    if (!desired) desired = "# House Intent Lighting"
    if (app.label != desired) {
        app.updateLabel(desired)
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

private void debug(String message) {
    if (debugLogging) {
        log.debug "${app.label}: ${message}"
    }
}
