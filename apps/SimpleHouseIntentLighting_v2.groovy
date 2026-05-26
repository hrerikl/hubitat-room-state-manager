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

// -------------------- Helpers --------------------

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

private void debug(String message) {
    if (debugLogging) {
        log.debug "${app.label}: ${message}"
    }
}
