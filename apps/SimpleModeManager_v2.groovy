/**
 * Simple Mode Manager v2 - Child App
 *
 * Install as Apps Code:
 *   Name: Simple Mode Manager v2
 *   Namespace: lundby
 *
 * Manages Hubitat Location Mode from house-level presence, vacation, evening,
 * day, night readiness, and StillUp extension signals.
 */

definition(
    name: "Simple Mode Manager v2",
    namespace: "lundby",
    author: "Erik Lundby / ChatGPT",
    description: "Lightweight Location Mode manager for Simple Room State.",
    category: "Convenience",
    parent: "lundby:Simple Room State Manager v2",
    singleInstance: false,
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage", title: "Simple Mode Manager", install: true, uninstall: true) {
        section("Modes") {
            input "dayModeName", "enum", title: "Day mode", options: locationModeOptions(), defaultValue: "Day", required: true
            input "eveningModeName", "enum", title: "Evening mode", options: locationModeOptions(), defaultValue: "Evening", required: true
            input "nightModeName", "enum", title: "Night mode", options: locationModeOptions(), defaultValue: "Night", required: true
            input "awayModeName", "enum", title: "Away mode", options: locationModeOptions(), defaultValue: "Away", required: true
            input "vacationModeName", "enum", title: "Vacation mode", options: locationModeOptions(), defaultValue: "Vacation", required: true
        }

        section("Presence") {
            input "presenceSwitches", "capability.switch", title: "Presence switches. On means someone is home.", multiple: true, required: false
            input "vacationSwitch", "capability.switch", title: "Vacation switch. On means no-presence becomes Vacation instead of Away.", multiple: false, required: false
        }

        section("Day and evening") {
            input "dayStartTime", "time", title: "Day starts at", defaultValue: "06:30", required: true
            input "eveningOffsetMinutes", "number", title: "Evening starts this many minutes before sunset", defaultValue: 10, required: true
        }

        section("Night") {
            input "nightStartTime", "time", title: "Night check starts at", defaultValue: "23:00", required: true
            input "nightReadyRoomChildAppIds", "enum", title: "Rooms that must be Off for Night. Locked rooms count as ready.", options: roomOptions(), multiple: true, required: false
            input "nightReadyMinutes", "number", title: "Rooms must be ready for this many minutes", defaultValue: 10, required: true
            input "stillUpSwitches", "capability.switch", title: "StillUp switches. On extends Night; during Night, On returns to Evening.", multiple: true, required: false
            input "stillUpButtons", "capability.pushableButton", title: "StillUp buttons. Pushed extends Night; during Night, pushed returns to Evening.", multiple: true, required: false
            input "stillUpExtensionMinutes", "number", title: "StillUp extends Night by minutes from activation", defaultValue: 60, required: true
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

def initialize() {
    subscribe(presenceSwitches, "switch", presenceHandler)
    subscribe(vacationSwitch, "switch", presenceHandler)
    subscribe(stillUpSwitches, "switch.on", stillUpHandler)
    subscribe(stillUpButtons, "pushed", stillUpHandler)
    subscribe(nightReadyRooms(), "roomState", nightReadyRoomHandler)

    scheduleDayStart()
    scheduleEveningStart()
    scheduleNightStart()

    evaluatePresenceMode("initialize")
}

// -------------------- Scheduling --------------------

private void scheduleDayStart() {
    if (dayStartTime) {
        schedule(dayStartTime, dayStartHandler)
    }
}

private void scheduleEveningStart() {
    Integer offset = Math.max((eveningOffsetMinutes ?: 10) as Integer, 0)
    def sunsetTime = getSunriseAndSunset()?.sunset
    if (!sunsetTime) return

    Date target = new Date(sunsetTime.time - (offset * 60L * 1000L))
    schedule(target, eveningStartHandler)
}

private void scheduleNightStart() {
    if (nightStartTime) {
        schedule(nightStartTime, nightStartHandler)
    }
}

def dayStartHandler() {
    scheduleDayStart()
    evaluatePresenceMode("day start")
    if (someoneHome()) {
        setModeIfNeeded(dayModeName, "day start")
    }
}

def eveningStartHandler() {
    scheduleEveningStart()
    evaluatePresenceMode("evening start")
    if (someoneHome()) {
        setModeIfNeeded(eveningModeName, "evening start")
    }
}

def nightStartHandler() {
    scheduleNightStart()
    evaluateNight("night start")
}

// -------------------- Events --------------------

def presenceHandler(evt) {
    debug "Presence input ${evt.name}=${evt.value}: ${evt.displayName}"
    evaluatePresenceMode("presence input")
}

def stillUpHandler(evt) {
    debug "StillUp activated: ${evt.displayName}"

    if (currentModeName() == nightModeName) {
        setModeIfNeeded(eveningModeName, "StillUp during Night")
    }

    Long until = now() + (stillUpExtensionSeconds() * 1000L)
    state.nightExtendedUntil = until
    debug "Night extended until ${formatTime(until)}"
}

def nightReadyRoomHandler(evt) {
    debug "Night-ready room event ${evt.name}=${evt.value}: ${evt.displayName}"

    if (nightWindowActive()) {
        evaluateNight("night-ready room changed")
    }
}

// -------------------- Evaluation --------------------

private void evaluatePresenceMode(String reason) {
    if (someoneHome()) {
        debug "Someone is home; presence evaluation did not force Away/Vacation: ${reason}"
        return
    }

    if (vacationActive()) {
        setModeIfNeeded(vacationModeName, "no presence and vacation active: ${reason}")
    } else {
        setModeIfNeeded(awayModeName, "no presence: ${reason}")
    }
}

private void evaluateNight(String reason) {
    if (!someoneHome()) {
        evaluatePresenceMode("night check with no presence")
        return
    }

    if (!nightWindowActive()) {
        debug "Night blocked because night window is not active: ${reason}"
        return
    }

    if (nightExtended()) {
        debug "Night blocked by StillUp extension until ${formatTime(state.nightExtendedUntil as Long)}"
        return
    }

    if (nightReadyRoomsReady()) {
        Integer seconds = nightReadySeconds()
        debug "Night-ready rooms are ready; scheduling Night in ${seconds} seconds"
        runIn(seconds, activateNightIfStillReady, [overwrite: true])
    } else {
        unschedule(activateNightIfStillReady)
        debug "Night blocked because selected rooms are not ready"
    }
}

def activateNightIfStillReady() {
    if (!someoneHome()) {
        evaluatePresenceMode("night activation with no presence")
        return
    }

    if (!nightWindowActive() || nightExtended()) {
        debug "Night activation skipped because window or extension changed"
        return
    }

    if (nightReadyRoomsReady()) {
        setModeIfNeeded(nightModeName, "night-ready rooms settled")
    } else {
        debug "Night activation skipped because rooms are no longer ready"
    }
}

private Boolean nightReadyRoomsReady() {
    List rooms = nightReadyRooms()
    if (!rooms) return true

    return rooms.every { room ->
        String roomState = room.currentValue("roomState")?.toString()
        roomState in ["Off", "Locked"]
    }
}

private Boolean nightWindowActive() {
    Date start = timeToday(nightStartTime ?: "23:00", location.timeZone)
    return now() >= start.time
}

private Boolean nightExtended() {
    Long until = state.nightExtendedUntil as Long
    return until && now() < until
}

// -------------------- Helpers --------------------

private Boolean someoneHome() {
    if (!presenceSwitches) return true
    return presenceSwitches?.any { it.currentSwitch == "on" } ?: false
}

private Boolean vacationActive() {
    return vacationSwitch?.currentSwitch == "on"
}

private Integer nightReadySeconds() {
    return Math.max(((nightReadyMinutes ?: 10) as Integer) * 60, 1)
}

private Integer stillUpExtensionSeconds() {
    return Math.max(((stillUpExtensionMinutes ?: 60) as Integer) * 60, 1)
}

private List nightReadyRooms() {
    if (!nightReadyRoomChildAppIds) return []

    try {
        return parent.neighborRoomDevicesForChildIds(nightReadyRoomChildAppIds) ?: []
    } catch (Exception e) {
        log.warn "${app.label}: Could not resolve night-ready rooms: ${e.message}"
        return []
    }
}

private Map roomOptions() {
    try {
        return parent.roomStateChildOptions(app.id) ?: [:]
    } catch (Exception e) {
        log.warn "${app.label}: Could not load room options: ${e.message}"
        return [:]
    }
}

private Map locationModeOptions() {
    try {
        return location?.modes?.collectEntries { mode ->
            String name = mode?.name?.toString() ?: mode?.toString()
            [(name): name]
        } ?: [:]
    } catch (Exception e) {
        log.warn "${app.label}: Could not load Location Modes: ${e.message}"
        return [:]
    }
}

private void setModeIfNeeded(String modeName, String reason) {
    if (!modeName) return

    if (currentModeName() == modeName) {
        debug "Location Mode already ${modeName}: ${reason}"
        return
    }

    try {
        setLocationMode(modeName)
        log.info "${app.label}: Location Mode set to ${modeName}: ${reason}"
    } catch (Exception e) {
        log.warn "${app.label}: Could not set Location Mode to ${modeName}: ${e.message}"
    }
}

private String currentModeName() {
    try {
        return location?.mode?.toString()
    } catch (Exception ignored) {
        return null
    }
}

private String formatTime(Long epochMs) {
    if (!epochMs) return "unknown"
    return new Date(epochMs).format("yyyy-MM-dd HH:mm:ss", location.timeZone)
}

private void debug(String msg) {
    if (debugLogging) {
        log.debug "${app.label}: ${msg}"
    }
}
