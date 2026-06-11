/**
 * Simple Room Lighting - Child App
 *
 * Install as Apps Code:
 *   Name: Simple Room Lighting
 *   Namespace: lundby
 *
 * Opinionated room lighting adapter:
 *   Room MetaLight + lightingIntent + Location Mode -> selected physical lights
 *   selected physical controls -> Room on/off/level
 */

definition(
    name: "Simple Room Lighting",
    namespace: "lundby",
    author: "Erik Lundby / ChatGPT",
    description: "Simple room-centric lighting adapter for Room MetaLight output and physical controls.",
    category: "Convenience",
    parent: "lundby:Simple Home",
    singleInstance: false,
    iconUrl: "",
    iconX2Url: ""
)

#include lundby.SimpleHomeHelpers

preferences {
    page(name: "mainPage", title: "Simple Room Lighting", install: true, uninstall: true) {
        section("Room") {
            input "roomChildAppId", "enum", title: "Room", options: roomOptions(), required: true, submitOnChange: true
        }

        section("Devices to automate") {
            input "automatedDimmers", "capability.switchLevel", title: "Dimmers to automate", multiple: true, required: false, submitOnChange: true
            input "automatedSwitches", "capability.switch", title: "Switch-only devices to automate", multiple: true, required: false, submitOnChange: true
        }

        section("Room controls") {
            input "picoRemotes", "capability.pushableButton", title: "5-button Pico remotes", multiple: true, required: false, submitOnChange: true
            if (hasPicoRemotes()) {
                input "picoPushesImplyPresenceActivity", "bool", title: "Pico button pushes imply room presence activity", defaultValue: true, required: true
                input "picoButtonsOneToFourEngage", "bool", title: "Pico buttons 1-4 engage the room", defaultValue: false, required: true
                input "sceneCycleSwitches", "capability.switch", title: "Button 3 scene switches/activators", multiple: true, required: false
            }
        }

        section("Voice") {
            input "speechDevices", "capability.speechSynthesis", title: "Speech devices for Room state announcements", multiple: true, required: false
            input "announceRoomControls", "bool", title: "Announce Lock, Unlock, Sleep, and Wake", defaultValue: false, required: true
            input "useEchoSpeaksSleepRoutines", "bool", title: "Use Echo Speaks GoodNight/GoodMorning commands when available", defaultValue: true, required: true
        }

        section("Advanced") {
            input "showAdvancedSettings", "bool", title: "Show advanced settings", defaultValue: false, required: true, submitOnChange: true
        }

        if (showAdvancedSettings) {
            section("Advanced room") {
                input "lightingName", "text", title: "Lighting app name override, optional", required: false
            }

            section("Matrix options") {
                input "matrixVariation", "enum", title: "Matrix variation", options: matrixVariationOptions(), defaultValue: "all", required: true, submitOnChange: true
                if (matrixUsesMode()) {
                    input "matrixModes", "enum", title: "Modes with custom matrix settings", options: locationModeOptions(), multiple: true, required: false, submitOnChange: true
                }
                input "offCondition", "enum", title: "Off condition", options: offConditionOptions(), defaultValue: "metaLightOff", required: true
                input "activationTransitionSeconds", "number", title: "Activation transition seconds", defaultValue: 2, required: true
                input "deactivationTransitionSeconds", "number", title: "Deactivation transition seconds", defaultValue: 30, required: true
                input "referenceTransitionSeconds", "number", title: "Reference change transition seconds", defaultValue: 10, required: true
                input "reapplyOnModeChange", "bool", title: "Reassess matrix on Location Mode change when MetaLight is on", defaultValue: true, required: true
                input "alwaysActivateRows", "bool", title: "Always send activation commands for active rows", defaultValue: true, required: true
                input "showAdvancedOffActions", "bool", title: "Allow rows to turn devices on when room is off", defaultValue: false, required: true, submitOnChange: true
            }

            renderMatrixSections()

            section("Physical controls") {
                input "controlDimmers", "capability.switchLevel", title: "Physical dimmers that send on/off/level to Room", multiple: true, required: false
                input "controlSwitches", "capability.switch", title: "Physical switches that send on/off to Room", multiple: true, required: false
                input "physicalControlEventsOnly", "bool", title: "Only physical control events update Room", defaultValue: true, required: true
            }

            section("Advanced room controls") {
                input "casetaDimmers", "capability.switchLevel", title: "4-button Caseta dimmers/switches", multiple: true, required: false
                if (hasPicoRemotes()) {
                    input "picoLevelChangeDimmers", "capability.switchLevel", title: "Dimmers for held level-change buttons", multiple: true, required: false
                    input "asleepSceneCycleSwitches", "capability.switch", title: "Button 3 asleep scene switches/activators", multiple: true, required: false
                    input "picoStepSize", "number", title: "Push level step", defaultValue: 10, required: true
                    input "sleepSceneTimeoutMinutes", "number", title: "When asleep and no asleep scenes are selected, button 3 extends Night lighting minutes", defaultValue: 45, required: true
                    input "asleepFallbackLevelBoost", "number", title: "When asleep and no asleep scenes are selected, button 3 adds this much light", defaultValue: 20, required: true
                }
            }

            section("Lock behavior") {
                input "fadeOffWhenLocked", "bool", title: "Fade automated devices off when room locks", defaultValue: false, required: true
                if (fadeOffWhenLocked) {
                    input "lockedFadeOffTransitionSeconds", "number", title: "Locked fade off seconds", defaultValue: 30, required: true
                }
            }

            section("Debug") {
                input "debugLogging", "bool", title: "Enable debug logging", defaultValue: true, required: true
                input "traceLightingCommands", "bool", title: "Trace lighting command decisions", defaultValue: false, required: true
                paragraph "Simple Home can suppress or force debug logging from the parent app."
            }
        }
    }
    page(name: "matrixPage")
}

def matrixPage(params) {
    String title = params?.title ?: "Matrix"
    String context = params?.context ?: "all"
    String intent = params?.intent ?: "Any"

    dynamicPage(name: "matrixPage", title: "Edit ${title}", install: false, uninstall: false) {
        renderMatrixInputSection(title, context, intent)
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
    initialize()
}

def reinitializeFromParent() {
    log.info "Reinitializing ${app.label} from parent"
    unsubscribe()
    initialize()
}

def initialize() {
    cacheDebugEnabled(debugLogging)
    atomicState.roomReassessInFlight = false
    atomicState.roomReassessDirty = false
    updateAppLabel()

    if (!roomLightingAllowed()) {
        markDuplicateForDelete("Room Lighting")
        log.error "${app.label}: Duplicate Room Lighting app for this room. Delete this child app or choose an unused room."
        return
    }

    def room = roomDevice()
    subscribe(room, "switch.off", roomSwitchOffHandler)
    subscribe(room, "metaLightSwitch", reassessHandler)
    subscribe(room, "metaLightLevel", reassessHandler)
    subscribe(room, "metaLightColorTemperature", reassessHandler)
    subscribe(room, "lightingIntent", reassessHandler)
    subscribe(room, "lockedEnabled", lockedEnabledHandler)
    subscribe(room, "asleepEnabled", roomControlAnnouncementHandler)
    subscribe(room, "customLighting", roomControlAnnouncementHandler)
    subscribe(room, "announcementMessagesJson", roomAnnouncementMessagesHandler)
    subscribe(room, "sceneRequest", sceneRequestHandler)
    subscribe(location, "mode", locationModeHandler)

    subscribe(controlDimmers, "switch", controlSwitchHandler)
    subscribe(controlDimmers, "level", controlLevelHandler)
    subscribe(controlSwitches, "switch", controlSwitchHandler)
    subscribe(picoRemotes, "pushed", picoPushedHandler)
    subscribe(picoRemotes, "held", picoHeldHandler)
    subscribe(picoRemotes, "released", picoReleasedHandler)
    subscribe(picoRemotes, "doubleTapped", picoDoubleTappedHandler)
    subscribe(casetaDimmers, "pushed", casetaPushedHandler)
    subscribe(casetaDimmers, "held", casetaHeldHandler)
    subscribe(casetaDimmers, "released", casetaReleasedHandler)
    subscribe(casetaDimmers, "doubleTapped", casetaDoubleTappedHandler)
    subscribe(overrideSwitches(), "switch", overrideSwitchHandler)
    subscribe(parent.recoveryDevice(), "switch.on", recoverSimpleHomeHandler)

    syncRoomAnnouncementMessages(room)
    reassessLighting("initialize")
}

// -------------------- Events --------------------

def reassessHandler(evt) {
    debug "Room lighting event ${evt.name}=${evt.value}"
    queueRoomReassess(evt.name?.toString(), evt.value?.toString())
}

def processRoomReassess() {
    if (atomicState.roomReassessInFlight == true) {
        atomicState.roomReassessDirty = true
        debug "Room reassess already running; marked dirty for trailing pass"
        return
    }

    String reason = state.pendingRoomReassessReason ?: "room device changed"
    Boolean metaLightOff = state.pendingRoomReassessMetaLightOff == true
    state.pendingRoomReassessReason = null
    state.pendingRoomReassessMetaLightOff = false

    executeRoomReassess(reason, metaLightOff)
}

private void executeRoomReassess(String reason, Boolean metaLightOff) {
    atomicState.roomReassessInFlight = true
    atomicState.roomReassessDirty = false

    try {
        executeRoomReassessNow(reason, metaLightOff)
    } finally {
        atomicState.roomReassessInFlight = false
        if (atomicState.roomReassessDirty == true || state.pendingRoomReassessReason) {
            atomicState.roomReassessDirty = false
            runInMillis(150, "processRoomReassess", [overwrite: true])
        }
    }
}

private void executeRoomReassessNow(String reason, Boolean metaLightOff) {
    Map snap = roomSnapshot()
    if (snap.valid != true) return

    Map drivers = lightingDriverSnapshot(snap)
    if (!lightingDriversChanged(drivers)) {
        debug "Skipping room reassess because lighting drivers are unchanged: ${reason}"
        return
    }

    if (metaLightOff && snap.sw == "off" && offCondition == "metaLightOff") {
        applyOffCondition("MetaLight off", snap)
        return
    }

    if (roomLightingInactive(snap)) {
        if (activeIntentSnapshot(snap)) {
            Integer retries = safeInteger(state.activeIntentSnapshotRetries, 0)
            if (retries < 3) {
                state.activeIntentSnapshotRetries = retries + 1
                debug "Active lighting intent has incomplete MetaLight snapshot; retrying reassess (${state.activeIntentSnapshotRetries}/3): ${snap}"
                state.pendingRoomReassessReason = appendRoomReassessReason(state.pendingRoomReassessReason, reason)
                runInMillis(300, "processRoomReassess", [overwrite: true])
            } else {
                log.warn "${app.label}: Active lighting intent still has incomplete MetaLight snapshot after retries; skipping Off command: ${snap}"
                state.activeIntentSnapshotRetries = 0
            }
            return
        }
        state.activeIntentSnapshotRetries = 0
        applyOffCondition("${reason} while inactive", snap)
        return
    }

    state.activeIntentSnapshotRetries = 0
    reassessLighting(reason, snap)
}

def roomSwitchOffHandler(evt) {
    debug "Room switch off event"

    if (offCondition == "roomSwitchOff") {
        applyOffCondition("Room switch off")
    }
}

def locationModeHandler(evt) {
    debug "Location Mode changed to ${evt.value}"

    if (!reapplyOnModeChange) {
        debug "Mode change ignored because reassess is disabled"
        return
    }

    Map snap = roomSnapshot()
    if (roomLightingInactive(snap)) {
        applyInactiveOnRowOffCondition("Location Mode changed while inactive", snap)
        return
    }

    reassessLighting("Location Mode changed", snap)
}

def overrideSwitchHandler(evt) {
    debug "Override switch ${evt.value}: ${evt.displayName}"
    Map snap = roomSnapshot()
    if (roomLightingInactive(snap)) {
        applyInactiveOnRowOffCondition("override switch changed while inactive", snap)
        return
    }
    reassessLighting("override switch changed", snap)
}

private void queueRoomReassess(String eventName, String eventValue) {
    String reason = "${eventName ?: 'room device'} changed"
    state.pendingRoomReassessReason = appendRoomReassessReason(state.pendingRoomReassessReason, reason)
    if (eventName == "metaLightSwitch" && eventValue == "off") {
        state.pendingRoomReassessMetaLightOff = true
    }
    if (atomicState.roomReassessInFlight == true) {
        atomicState.roomReassessDirty = true
        debug "Queued room reassess while running: ${reason}"
        return
    }
    runInMillis(300, "processRoomReassess", [overwrite: true])
}

private String appendRoomReassessReason(def existing, String reason) {
    String existingText = existing?.toString()
    if (!existingText) return reason
    if (existingText.split(", ").contains(reason)) return existingText
    return "${existingText}, ${reason}"
}

def sceneRequestHandler(evt) {
    debug "Scene cycle requested from room device"
    cycleSceneSwitch()
}

def lockedEnabledHandler(evt) {
    if (evt.value == "on" && fadeOffWhenLockedEnabled()) {
        applyLockedFadeOff()
    }
    roomControlAnnouncementHandler(evt)
}

def recoverSimpleHomeHandler(evt) {
    debug "Recover Simple Home requested"
    recoverSimpleHome()
}

def roomControlAnnouncementHandler(evt) {
    if (!announceRoomControlsEnabled()) return

    String message = null
    if (evt.name == "lockedEnabled") {
        message = evt.value == "on" ? lockedAnnouncementText() : unlockedAnnouncementText()
    } else if (evt.name == "asleepEnabled") {
        if (useEchoSpeaksSleepRoutinesEnabled() && announceEchoSpeaksSleepRoutine(evt.value == "on")) return
        message = evt.value == "on" ? asleepAnnouncementText() : awakeAnnouncementText()
    } else if (evt.name == "customLighting") {
        if (!isDigitalEvent(evt)) {
            debug "Skipping announcement for app-published custom lighting ${evt.value} event"
            return
        }
        message = evt.value == "on" ? customLightingOnText() : customLightingOffText()
    }

    announceRoomControl(message)
}

def roomAnnouncementMessagesHandler(evt) {
    debug "Room announcement messages updated"
}

def controlSwitchHandler(evt) {
    if (!shouldAcceptControlEvent(evt)) return

    debug "Control switch ${evt.value}: ${evt.displayName}"
    def room = roomDevice()
    if (!room) return

    if (evt.value == "on") {
        room.on()
    } else if (evt.value == "off") {
        room.off()
    }
}

def controlLevelHandler(evt) {
    if (!shouldAcceptControlEvent(evt)) return

    Integer deviceLevel = normalizedLevel(evt.value, 0)
    Integer roomLevel = roomLevelFromControlDevice(evt.device, deviceLevel)
    debug "Control level ${deviceLevel} -> Room level ${roomLevel}: ${evt.displayName}"
    suppressNextLevelFollow()
    setRoomLevelAsCustom(roomLevel)
}

def picoPushedHandler(evt) {
    Integer button = eventIntegerValue(evt)
    debug "Pico pushed ${button}: ${evt.displayName}"

    def room = roomDevice()
    if (!room) return

    recordPicoPresenceActivity(button, evt)
    recordPicoEngagementActivity(button, evt)

    Integer step = normalizedLevel(picoStepSize, 10)
    if (step <= 0) step = 10

    if (button == 1) {
        roomOnIfNeeded()
    } else if (button == 2) {
        allowNextLevelFollow()
        setRoomLevelAsCustom(adjustedRoomLevel(step))
    } else if (button == 3) {
        cycleSceneSwitch()
    } else if (button == 4) {
        allowNextLevelFollow()
        setRoomLevelAsCustom(adjustedRoomLevel(-step))
    } else if (button == 5) {
        room.off()
    } else {
        debug "No default Pico pushed action for button ${button}"
    }
}

def picoHeldHandler(evt) {
    Integer button = eventIntegerValue(evt)
    debug "Pico held ${button}: ${evt.displayName}"

    if (button == 1) {
        wakeAndUnlockRoom("Pico held 1")
    } else if (button == 2) {
        startLevelChange("up")
    } else if (button == 3) {
        returnToAutomaticLighting()
    } else if (button == 4) {
        startLevelChange("down")
    } else if (button == 5) {
        setRoomAsleepFromRemote("Pico held 5")
    } else {
        debug "No default Pico held action for button ${button}"
    }
}

def picoReleasedHandler(evt) {
    Integer button = eventIntegerValue(evt)
    debug "Pico released ${button}: ${evt.displayName}"

    if (button in [2, 4]) {
        stopLevelChange()
    }
}

def picoDoubleTappedHandler(evt) {
    Integer button = eventIntegerValue(evt)
    debug "Pico doubleTapped ${button}: ${evt.displayName}"

    if (button == 1) {
        wakeAndUnlockRoom("Pico double-tapped 1")
    } else if (button == 5) {
        roomDevice()?.lock()
    } else {
        debug "No default Pico double-tap action for button ${button}"
    }
}

def casetaPushedHandler(evt) {
    Integer button = eventIntegerValue(evt)
    debug "Caseta pushed ${button}: ${evt.displayName}"

    def room = roomDevice()
    if (!room) return

    Integer step = normalizedLevel(picoStepSize, 10)
    if (step <= 0) step = 10

    if (button == 1) {
        roomOnIfNeeded()
    } else if (button == 2) {
        allowNextLevelFollow()
        setRoomLevelAsCustom(adjustedRoomLevel(step))
    } else if (button == 3) {
        allowNextLevelFollow()
        setRoomLevelAsCustom(adjustedRoomLevel(-step))
    } else if (button == 4) {
        room.off()
    } else {
        debug "No default Caseta pushed action for button ${button}"
    }
}

def casetaHeldHandler(evt) {
    Integer button = eventIntegerValue(evt)
    debug "Caseta held ${button}: ${evt.displayName}"

    if (button == 2) {
        startLevelChange("up")
    } else if (button == 3) {
        startLevelChange("down")
    } else if (button == 4) {
        setRoomAsleepFromRemote("Caseta held 4")
    } else {
        debug "No default Caseta held action for button ${button}"
    }
}

def casetaReleasedHandler(evt) {
    Integer button = eventIntegerValue(evt)
    debug "Caseta released ${button}: ${evt.displayName}"

    if (button in [2, 3]) {
        stopLevelChange()
    }
}

def casetaDoubleTappedHandler(evt) {
    Integer button = eventIntegerValue(evt)
    debug "Caseta doubleTapped ${button}: ${evt.displayName}"

    if (button == 1) {
        roomDevice()?.unlock()
    } else if (button == 4) {
        roomDevice()?.lock()
    } else {
        debug "No default Caseta double-tap action for button ${button}"
    }
}

// -------------------- Matrix Output --------------------

private void reassessLighting(String reason, Map snap = null) {
    Long startedAt = now()
    snap = snap ?: roomSnapshot()
    if (snap.valid != true) return

    String switchState = snap.sw
    String intent = snap.intent
    Integer metaLevel = snap.level
    Integer metaCt = snap.ct
    String context = activeContextKey()
    String intentBucket = activeIntentBucket(intent)
    List devices = allAutomatedDevices()
    Map stats = lightingStats(devices)

    debug "Reassessing context=${context} intent=${intentBucket} roomIntent=${intent} meta=${switchState}/${metaLevel}/${metaCt}: ${reason}"

    if (switchState != "on" || metaLevel <= 0 || !(intent in ["On", "Courtesy", "Night"])) {
        debug "Skipping activation because MetaLight is off or intent is not active"
        logLightingTiming("reassessLighting", startedAt, reason, stats)
        return
    }

    Boolean sameActiveMatrix = state.lastActiveMatrixContext == context && state.lastActiveMatrixIntent == intentBucket
    Boolean levelChangeOnly = reason == "metaLightLevel changed" && sameActiveMatrix && levelFollowAllowed()
    Boolean colorTemperatureOnly = reason == "metaLightColorTemperature changed" && sameActiveMatrix
    if (reason == "metaLightLevel changed") {
        clearLevelFollowMarkers()
    }

    state.lastActiveMatrixContext = context
    state.lastActiveMatrixIntent = intentBucket
    applyIntentRows(context, intentBucket, metaLevel, metaCt, levelChangeOnly, colorTemperatureOnly, reason, devices, stats)
    recordLightingDrivers(lightingDriverSnapshot(snap))
    if (!levelChangeOnly && !colorTemperatureOnly) {
        publishLastLightingAction("${intentBucket} ${metaLevel}% ${metaCt}K")
    }
    logLightingTiming("reassessLighting", startedAt, reason, stats)
}

private void applyIntentRows(String context, String intentBucket, Integer metaLevel, Integer metaCt, Boolean levelChangeOnly = false, Boolean colorTemperatureOnly = false, String reason = "", List devices = null, Map stats = null) {
    Boolean useOverride = overrideActive(context, intentBucket)
    Boolean forceActivation = alwaysActivateRowsEnabled() && !levelChangeOnly && !colorTemperatureOnly
    Integer transition = transitionSecondsFor(reason, "activation")
    List levelCtAfterSwitchDevices = []
    if (useOverride) {
        debug "Using override matrix for context=${context} intent=${intentBucket}"
    }

    (devices ?: allAutomatedDevices()).each { dev ->
        if (!rowAct(context, intentBucket, dev, useOverride)) return

        String switchCommand = rowSwitch(context, intentBucket, dev, useOverride)
        String levelMode = isDimmer(dev) ? rowLevelMode(context, intentBucket, dev, useOverride) : "none"
        String ctMode = isColorTemperatureDevice(dev) ? rowCtMode(context, intentBucket, dev, useOverride) : "none"
        Boolean skipInitial = isDimmer(dev) && switchCommand == "on" && levelMode == "followSkip"
        if (skipInitial && shouldSkipInitialActivation(reason, levelChangeOnly)) {
            debug "Skipping initial activation for ${dev.displayName}"
            return
        }
        if (isDimmer(dev) && switchCommand == "on" && levelMode == "followAfterSwitches") {
            levelCtAfterSwitchDevices << dev
            return
        }

        if (colorTemperatureOnly) {
            if (switchCommand == "on" && ctMode == "follow") {
                setColorTemperature(dev, metaCt, forceActivation, stats)
            }
        } else if (switchCommand == "off") {
            turnOffDevice(dev, "activation switch off", transitionSecondsFor(reason, "deactivation"), stats)
        } else if (isDimmer(dev)) {
            Boolean combinedCommandSent = false
            if (levelMode == "none") {
                turnOnDevice(dev, forceActivation, stats)
            } else {
                Integer level = levelMode == "explicit" ? rowExplicitLevel(context, intentBucket, dev, metaLevel, useOverride) : rowFollowLevel(context, intentBucket, dev, metaLevel, useOverride)
                level = physicalLevelForRow(context, intentBucket, dev, level, useOverride)
                if (ctMode == "follow" && !levelChangeOnly) {
                    combinedCommandSent = setDimmerColorTemperature(dev, level, metaCt, transition, forceActivation, stats)
                }
                if (!combinedCommandSent) {
                    setDimmer(dev, level, transition, forceActivation, stats)
                }
            }
            if (ctMode == "follow" && !combinedCommandSent) {
                setColorTemperature(dev, metaCt, forceActivation, stats)
            }
        } else {
            turnOnDevice(dev, forceActivation, stats)
        }
    }

    Boolean suppressInitialFeedback = !levelChangeOnly && !colorTemperatureOnly
    levelCtAfterSwitchDevices.each { dev ->
        applyLevelCtAfterSwitchesRow(context, intentBucket, dev, useOverride, metaLevel, metaCt, levelChangeOnly, colorTemperatureOnly, suppressInitialFeedback, transition, forceActivation, stats)
    }
}

private void applyLevelCtAfterSwitchesRow(String context, String intentBucket, def dev, Boolean useOverride, Integer metaLevel, Integer metaCt, Boolean levelChangeOnly, Boolean colorTemperatureOnly, Boolean suppressInitialFeedback, Integer transition, Boolean forceActivation, Map stats = null) {
    String ctMode = isColorTemperatureDevice(dev) ? rowCtMode(context, intentBucket, dev, useOverride) : "none"
    Boolean combinedCommandSent = false

    if (!colorTemperatureOnly) {
        Integer level = physicalLevelForRow(context, intentBucket, dev, rowFollowLevel(context, intentBucket, dev, metaLevel, useOverride), useOverride)
        if (suppressInitialFeedback) {
            suppressControlFeedback(dev)
        }
        if (ctMode == "follow" && !levelChangeOnly) {
            combinedCommandSent = setDimmerColorTemperature(dev, level, metaCt, transition, forceActivation, stats)
        }
        if (!combinedCommandSent) {
            setDimmer(dev, level, transition, forceActivation, stats)
        }
    }

    if (!levelChangeOnly && ctMode == "follow" && !combinedCommandSent) {
        if (suppressInitialFeedback) {
            suppressControlFeedback(dev)
        }
        setColorTemperature(dev, metaCt, forceActivation, stats)
    }
}

private Boolean shouldSkipInitialActivation(String reason, Boolean levelChangeOnly) {
    if (levelChangeOnly) return false
    return reason in ["initialize", "metaLightSwitch changed", "lightingIntent changed"]
}

private void applyOffCondition(String reason, Map snap = null) {
    Long startedAt = now()
    snap = snap ?: roomSnapshot()
    String context = activeContextKey()
    String intentBucket = activeIntentBucket(state.lastActiveMatrixIntent ?: snap.intent)
    List devices = allAutomatedDevices()
    Map stats = lightingStats(devices)
    debug "Applying off condition context=${context} intent=${intentBucket}: ${reason}"
    applyOffRows(context, intentBucket, reason, devices, stats)
    recordLightingDrivers(lightingDriverSnapshot(snap))
    publishLastLightingAction("Off")
    logLightingTiming("applyOffCondition", startedAt, reason, stats)
}

private void applyLockedFadeOff() {
    Long startedAt = now()
    Map snap = roomSnapshot()
    String context = activeContextKey()
    String intentBucket = activeIntentBucket(state.lastActiveMatrixIntent ?: snap.intent)
    List devices = allAutomatedDevices()
    Map stats = lightingStats(devices)
    debug "Applying locked fade off context=${context} intent=${intentBucket}"
    applyOffRows(context, intentBucket, "Room locked", devices, stats)
    recordLightingDrivers(lightingDriverSnapshot(snap))
    publishLastLightingAction("Locked fade off")
    logLightingTiming("applyLockedFadeOff", startedAt, "Room locked", stats)
}

private void applyInactiveOnRowOffCondition(String reason, Map snap = null) {
    Long startedAt = now()
    String context = activeContextKey()
    String intentBucket = matrixUsesIntent() ? "On" : "Any"
    List devices = allAutomatedDevices()
    Map stats = lightingStats(devices)
    debug "Applying inactive On-row off condition context=${context} intent=${intentBucket}: ${reason}"
    applyOffRows(context, intentBucket, reason, devices, stats)
    recordLightingDrivers(lightingDriverSnapshot(snap))
    logLightingTiming("applyInactiveOnRowOffCondition", startedAt, reason, stats)
}

private void applyOffRows(String context, String intentBucket, String reason, List devices = null, Map stats = null) {
    Boolean useOverride = overrideActive(context, intentBucket)
    Boolean useAdvancedOffActions = advancedOffActionsEnabled()
    Integer deactivationTransition = transitionSecondsFor(reason, "deactivation")
    Integer activationTransition = transitionSecondsFor(reason, "activation")
    (devices ?: allAutomatedDevices()).each { dev ->
        if (rowOff(context, intentBucket, dev, useOverride)) {
            applyOffRow(context, intentBucket, dev, useOverride, reason, useAdvancedOffActions, deactivationTransition, activationTransition, stats)
        }
    }
}

private void applyOffRow(String context, String intentBucket, def dev, Boolean useOverride, String reason, Boolean useAdvancedOffActions, Integer deactivationTransition, Integer activationTransition, Map stats = null) {
    String action = useAdvancedOffActions ? rowOffAction(context, intentBucket, dev, useOverride) : "off"
    if (action == "on") {
        if (isDimmer(dev)) {
            Integer level = physicalLevelForRow(context, intentBucket, dev, rowOffLevel(context, intentBucket, dev, 100, useOverride), useOverride)
            setDimmer(dev, level, activationTransition, alwaysActivateRowsEnabled(), stats)
        } else {
            turnOnDevice(dev, alwaysActivateRowsEnabled(), stats)
        }
    } else {
        turnOffDevice(dev, reason, deactivationTransition, stats)
    }
}

private void recoverSimpleHome() {
    Map snap = roomSnapshot(true)
    if (snap.valid != true) return

    if (snap.roomState == "Locked" || snap.lockedEnabled == "on" || snap.lock == "locked") {
        debug "Recover Simple Home skipped because room is locked"
        return
    }

    if (snap.sw == "on" && snap.level > 0 && snap.intent in ["On", "Courtesy", "Night"]) {
        debug "Recover Simple Home: reapplying active matrix"
        reassessLighting("Simple Home recovery", snap)
    } else {
        debug "Recover Simple Home: applying Off rows"
        applyInactiveOnRowOffCondition("Simple Home recovery", snap)
    }
}

private Boolean rowAct(String context, String intent, def dev, Boolean override = false) {
    return settingBool(rowName("act", context, intent, dev, override), defaultAct(context, intent))
}

private Boolean rowOff(String context, String intent, def dev, Boolean override = false) {
    return settingBool(rowName("off", context, intent, dev, override), true)
}

private String rowOffAction(String context, String intent, def dev, Boolean override = false) {
    return (settings[rowName("offAction", context, intent, dev, override)] ?: "off").toString()
}

private Integer rowOffLevel(String context, String intent, def dev, Integer fallback, Boolean override = false) {
    return normalizedLevel(settings[rowName("offLevel", context, intent, dev, override)], fallback)
}

private String rowLevelMode(String context, String intent, def dev, Boolean override = false) {
    String mode = (settings[rowName("levelMode", context, intent, dev, override)] ?: "follow").toString()
    if (mode == "follow" && rowLegacySkipInitial(context, intent, dev, override)) return "followSkip"
    return mode
}

private String rowCtMode(String context, String intent, def dev, Boolean override = false) {
    return (settings[rowName("ctMode", context, intent, dev, override)] ?: "follow").toString()
}

private String rowSwitch(String context, String intent, def dev, Boolean override = false) {
    return (settings[rowName("switch", context, intent, dev, override)] ?: "on").toString()
}

private Boolean rowLegacySkipInitial(String context, String intent, def dev, Boolean override = false) {
    return settingBool(rowName("skipInitial", context, intent, dev, override), false)
}

private Integer rowExplicitLevel(String context, String intent, def dev, Integer fallback, Boolean override = false) {
    return normalizedLevel(settings[rowName("level", context, intent, dev, override)], fallback)
}

private Integer rowFollowLevel(String context, String intent, def dev, Integer metaLevel, Boolean override = false) {
    Integer percent = normalizedPercent(settings[rowName("levelPercent", context, intent, dev, override)], 100)
    Integer offset = normalizedOffset(settings[rowName("levelOffset", context, intent, dev, override)], 0)
    BigDecimal adjusted = ((metaLevel as BigDecimal) * (percent as BigDecimal) / 100G) + (offset as BigDecimal)
    return normalizedLevel(adjusted.setScale(0, BigDecimal.ROUND_HALF_UP) as Integer, metaLevel)
}

private Integer rowUsableMinimumLevel(String context, String intent, def dev, Boolean override = false) {
    return normalizedUsableMinimum(settings[rowName("usableMin", context, intent, dev, override)])
}

private Integer physicalLevelForRow(String context, String intent, def dev, Integer logicalLevel, Boolean override = false) {
    return physicalLevelFromLogical(logicalLevel, rowUsableMinimumLevel(context, intent, dev, override))
}

private Integer roomLevelFromControlDevice(def controlDevice, Integer physicalLevel) {
    Map row = activeRowForControlDevice(controlDevice)
    if (!row) return physicalLevel

    Integer deviceLogical = logicalLevelFromPhysical(physicalLevel, row.usableMinimum as Integer)
    Integer percent = normalizedPercent(settings[rowName("levelPercent", row.context as String, row.intent as String, row.device, row.override as Boolean)], 100)
    Integer offset = normalizedOffset(settings[rowName("levelOffset", row.context as String, row.intent as String, row.device, row.override as Boolean)], 0)
    if (percent <= 0) return deviceLogical

    BigDecimal roomLevel = (((deviceLogical as BigDecimal) - (offset as BigDecimal)) * 100G) / (percent as BigDecimal)
    return normalizedLevel(roomLevel.setScale(0, BigDecimal.ROUND_HALF_UP) as Integer, deviceLogical)
}

private Map activeRowForControlDevice(def controlDevice) {
    if (!controlDevice) return null

    def dev = allAutomatedDevices().find { "${it.id}" == "${controlDevice.id}" }
    if (!dev || !isDimmer(dev)) return null

    String context = activeContextKey()
    String intent = activeIntentBucket(roomDevice()?.currentValue("lightingIntent")?.toString())
    Boolean useOverride = overrideActive(context, intent)
    if (!rowAct(context, intent, dev, useOverride) || rowSwitch(context, intent, dev, useOverride) != "on") return null

    String levelMode = rowLevelMode(context, intent, dev, useOverride)
    if (!(levelMode in ["follow", "followSkip", "followAfterSwitches"])) return null

    return [device: dev, context: context, intent: intent, override: useOverride, usableMinimum: rowUsableMinimumLevel(context, intent, dev, useOverride)]
}

private Integer physicalLevelFromLogical(Integer logicalLevel, Integer usableMinimum) {
    Integer logical = normalizedLevel(logicalLevel, 0)
    if (logical <= 0) return 0

    Integer minimum = normalizedUsableMinimum(usableMinimum)
    if (minimum <= 1) return logical

    BigDecimal physical = (minimum as BigDecimal) + (((logical - 1) as BigDecimal) * ((100 - minimum) as BigDecimal) / 99G)
    return normalizedLevel(physical.setScale(0, BigDecimal.ROUND_HALF_UP) as Integer, logical)
}

private Integer logicalLevelFromPhysical(Integer physicalLevel, Integer usableMinimum) {
    Integer physical = normalizedLevel(physicalLevel, 0)
    if (physical <= 0) return 0

    Integer minimum = normalizedUsableMinimum(usableMinimum)
    if (minimum <= 1) return physical
    if (physical <= minimum) return 1

    BigDecimal logical = 1G + (((physical - minimum) as BigDecimal) * 99G / ((100 - minimum) as BigDecimal))
    return normalizedLevel(logical.setScale(0, BigDecimal.ROUND_HALF_UP) as Integer, physical)
}

private Boolean defaultAct(String context, String intent) {
    if (context != "all") return false
    return intent in ["Any", "On", "Courtesy", "Night"]
}

private Boolean alwaysActivateRowsEnabled() {
    return settings?.alwaysActivateRows != false
}

private Boolean advancedOffActionsEnabled() {
    return showAdvancedOffActions == true
}

private Boolean fadeOffWhenLockedEnabled() {
    return settingBool("fadeOffWhenLocked", false)
}

private Map roomSnapshot(Boolean includeLockState = false) {
    def room = roomDevice()
    if (!room) return [valid: false]

    Map snap = [
        valid        : true,
        sw           : room.currentValue("metaLightSwitch") ?: "off",
        intent       : room.currentValue("lightingIntent") ?: "Off",
        level        : normalizedLevel(room.currentValue("metaLightLevel"), 0),
        ct           : normalizedColorTemperature(room.currentValue("metaLightColorTemperature"), 2700)
    ]

    if (includeLockState) {
        snap.roomState = room.currentValue("roomState") ?: "Off"
        snap.lockedEnabled = room.currentValue("lockedEnabled") ?: "off"
        snap.lock = room.currentValue("lock") ?: "unlocked"
    }

    return snap
}

private Map lightingDriverSnapshot(Map snap = null) {
    snap = snap ?: roomSnapshot()
    if (snap.valid != true) return [valid: false]

    String context = activeContextKey()
    String intentBucket = activeIntentBucket(snap.intent)
    return [
        valid    : true,
        sw       : snap.sw,
        intent   : snap.intent,
        bucket   : intentBucket,
        level    : snap.level,
        ct       : snap.ct,
        context  : context,
        override : overrideActive(context, intentBucket),
        roomState: roomDevice()?.currentValue("roomState") ?: "Off"
    ]
}

private Boolean lightingDriversChanged(Map drivers) {
    if (drivers?.valid != true) return true

    Map last = state.lastLightingDrivers instanceof Map ? state.lastLightingDrivers : null
    if (!last) return true

    Long elapsed = now() - safeLong(state.lastLightingDriversAt, 0L)
    if (elapsed > lightingDriverSkipWindowMs()) return true

    List keys = ["sw", "intent", "bucket", "level", "ct", "context", "override", "roomState"]
    Boolean changed = keys.any { key -> drivers[key] != last[key] }
    if (!changed) {
        trace "Lighting drivers unchanged within ${elapsed}ms: ${drivers}"
    }
    return changed
}

private void recordLightingDrivers(Map drivers) {
    if (drivers?.valid != true) return
    state.lastLightingDrivers = drivers
    state.lastLightingDriversAt = now()
}

private Integer lightingDriverSkipWindowMs() {
    return 2000
}

private Boolean roomLightingInactive(Map snap = null) {
    snap = snap ?: roomSnapshot()
    if (snap.valid != true) return true
    return snap.sw != "on" || snap.level <= 0 || !(snap.intent in ["On", "Courtesy", "Night"])
}

private Boolean activeIntentSnapshot(Map snap = null) {
    snap = snap ?: roomSnapshot()
    if (snap.valid != true) return false
    return snap.intent in ["On", "Courtesy", "Night"]
}

private String activeContextKey() {
    if (!matrixUsesMode()) return "all"

    String modeName = currentLocationModeName()
    if (!modeName) return "all"

    return selectedMatrixModes().contains(modeName) ? contextKey(modeName) : "all"
}

private String activeIntentBucket(String intent) {
    if (!matrixUsesIntent()) return "Any"
    return intent in ["On", "Courtesy", "Night"] ? intent : "On"
}

// -------------------- Matrix UI --------------------

private void renderMatrixSections() {
    matrixContexts().each { contextConfig ->
        matrixIntentBuckets().each { intentBucket ->
            renderMatrixSummarySection(contextConfig.title, contextConfig.key, intentBucket)
        }
    }
}

private void renderMatrixSummarySection(String title, String context, String intent) {
    List devices = allAutomatedDevices()
    if (!devices) return

    String sectionTitle = intent == "Any" ? title : "${title}: ${intent}"
    section(sectionTitle) {
        paragraph matrixSummaryTable(context, intent, false)
        def overrideSwitch = matrixOverrideSwitch(context, intent)
        if (overrideSwitch) {
            paragraph "Override when ${overrideSwitch.displayName} is on"
            paragraph matrixSummaryTable(context, intent, true)
        }
        href(
            name: "edit_${context}_${intent}",
            title: "Edit ${sectionTitle}",
            page: "matrixPage",
            params: [title: sectionTitle, context: context, intent: intent]
        )
    }
}

private void renderMatrixInputSection(String title, String context, String intent) {
    List devices = allAutomatedDevices()
    if (!devices) {
        section("Devices") {
            paragraph "No devices selected."
        }
        return
    }

    section("Summary") {
        paragraph matrixSummaryTable(context, intent, false)
    }

    renderMatrixRows("Rows", context, intent, false)

    section("Override") {
        input matrixName("overrideSwitch", context, intent), "capability.switch", title: "Override switch, optional", multiple: false, required: false, submitOnChange: true
    }

    if (matrixOverrideSwitch(context, intent)) {
        section("Override summary") {
            paragraph matrixSummaryTable(context, intent, true)
        }
        renderMatrixRows("Override rows", context, intent, true)
    }
}

private void renderMatrixRows(String title, String context, String intent, Boolean override) {
    section(title) {
        allAutomatedDevices().each { dev ->
            paragraph "${dev.displayName}"
            String actName = rowName("act", context, intent, dev, override)
            String switchName = rowName("switch", context, intent, dev, override)

            input actName, "bool", title: "Act", defaultValue: defaultAct(context, intent), required: true, submitOnChange: true
            String offName = rowName("off", context, intent, dev, override)
            input offName, "bool", title: "Off", defaultValue: true, required: true, submitOnChange: true

            if (advancedOffActionsEnabled() && settingBool(offName, true)) {
                String offActionName = rowName("offAction", context, intent, dev, override)
                input offActionName, "enum", title: "Off action", options: offActionOptions(), defaultValue: "off", required: true, submitOnChange: true
                if (isDimmer(dev) && (settings[offActionName] ?: "off") == "on") {
                    input rowName("offLevel", context, intent, dev, override), "number", title: "Off action level", defaultValue: 100, required: true
                }
            }

            if (settingBool(actName, defaultAct(context, intent))) {
                input switchName, "enum", title: "Switch", options: switchCommandOptions(), defaultValue: "on", required: true, submitOnChange: true
            }

            if (isDimmer(dev) && settingBool(actName, defaultAct(context, intent)) && (settings[switchName] ?: "on") == "on") {
                String levelModeName = rowName("levelMode", context, intent, dev, override)
                input levelModeName, "enum", title: "Level", options: levelModeOptions(), defaultValue: "follow", required: true, submitOnChange: true
                if ((settings[levelModeName] ?: "follow") in ["follow", "followSkip", "followAfterSwitches"]) {
                    input rowName("levelPercent", context, intent, dev, override), "number", title: "Level multiplier percent", defaultValue: 100, required: true
                    input rowName("levelOffset", context, intent, dev, override), "number", title: "Level offset. Example: +10 or -10", defaultValue: 0, required: true
                }
                if (settings[levelModeName] == "explicit") {
                    input rowName("level", context, intent, dev, override), "number", title: "Explicit level", required: true
                }
                input rowName("usableMin", context, intent, dev, override), "number", title: "Usable minimum level. Maps logical 1-100 into this device range", defaultValue: 1, required: true
            }

            if (isColorTemperatureDevice(dev) && settingBool(actName, defaultAct(context, intent)) && (settings[switchName] ?: "on") == "on") {
                input rowName("ctMode", context, intent, dev, override), "enum", title: "Color temperature", options: colorTemperatureModeOptions(), defaultValue: "follow", required: true
            }
        }
    }
}

private String matrixSummaryTable(String context, String intent, Boolean override) {
    List devices = allAutomatedDevices()
    if (!devices) return "No devices selected."

    String rows = devices.collect { dev ->
        String act = rowAct(context, intent, dev, override) ? "yes" : "no"
        String off = rowOff(context, intent, dev, override) ? "yes" : "no"
        if (advancedOffActionsEnabled() && rowOff(context, intent, dev, override)) {
            String action = rowOffAction(context, intent, dev, override)
            off = action == "on" && isDimmer(dev) ? "on ${rowOffLevel(context, intent, dev, 100, override)}" : action
        }
        String switchCommand = rowAct(context, intent, dev, override) ? rowSwitch(context, intent, dev, override) : ""
        String level = ""
        String ct = ""

        if (isDimmer(dev) && rowAct(context, intent, dev, override) && switchCommand == "on") {
            String mode = rowLevelMode(context, intent, dev, override)
            level = mode == "explicit" ? "${rowExplicitLevel(context, intent, dev, 100, override)}" : adjustedLevelSummary(context, intent, dev, override)
        }
        if (isColorTemperatureDevice(dev) && rowAct(context, intent, dev, override) && switchCommand == "on") {
            ct = colorTemperatureModeOptions()[rowCtMode(context, intent, dev, override)] ?: ""
        }

        return """
            <tr>
                <td>${htmlEscape(dev.displayName ?: dev.name ?: dev.id)}</td>
                <td>${isDimmer(dev) ? "Dimmer" : "Switch"}</td>
                <td>${act}</td>
                <td>${off}</td>
                <td>${switchCommand}</td>
                <td>${level ?: ""}</td>
                <td>${ct ?: ""}</td>
            </tr>
        """
    }.join("")

    return """
        <table style="border-collapse:collapse;width:100%;">
            <thead>
                <tr>
                    <th style="text-align:left;border-bottom:1px solid #999;padding:4px;">Device</th>
                    <th style="text-align:left;border-bottom:1px solid #999;padding:4px;">Type</th>
                    <th style="text-align:left;border-bottom:1px solid #999;padding:4px;">Act</th>
                    <th style="text-align:left;border-bottom:1px solid #999;padding:4px;">Off</th>
                    <th style="text-align:left;border-bottom:1px solid #999;padding:4px;">Switch</th>
                    <th style="text-align:left;border-bottom:1px solid #999;padding:4px;">Level</th>
                    <th style="text-align:left;border-bottom:1px solid #999;padding:4px;">CT</th>
                </tr>
            </thead>
            <tbody>${rows}</tbody>
        </table>
    """
}

private Map levelModeOptions() {
    return [
        follow             : "Follow MetaLight",
        followSkip         : "Follow MetaLight (skip initial)",
        followAfterSwitches: "Follow MetaLight level/CT after switches",
        explicit           : "Explicit level",
        none               : "No level command"
    ]
}

private Map colorTemperatureModeOptions() {
    return [
        follow: "Follow MetaLight",
        none  : "No CT command"
    ]
}

private String adjustedLevelSummary(String context, String intent, def dev, Boolean override) {
    String mode = rowLevelMode(context, intent, dev, override)
    if (mode == "none") return levelModeOptions()[mode]

    Integer percent = normalizedPercent(settings[rowName("levelPercent", context, intent, dev, override)], 100)
    Integer offset = normalizedOffset(settings[rowName("levelOffset", context, intent, dev, override)], 0)
    Integer usableMinimum = rowUsableMinimumLevel(context, intent, dev, override)
    String offsetText = offset == 0 ? "" : (offset > 0 ? " +${offset}" : " ${offset}")
    String rangeText = usableMinimum > 1 ? ", usable ${usableMinimum}-100" : ""
    return "${levelModeOptions()[mode]} (${percent}%${offsetText}${rangeText})"
}

private Map switchCommandOptions() {
    return [
        on : "on",
        off: "off"
    ]
}

private Map offActionOptions() {
    return [
        off: "off",
        on : "on"
    ]
}

private Map offConditionOptions() {
    return [
        metaLightOff : "MetaLight off",
        roomSwitchOff: "Room switch off"
    ]
}

private Map matrixVariationOptions() {
    return [
        all       : "All Modes",
        mode      : "Vary by Location Mode",
        intent    : "Vary by Lighting Intent",
        modeIntent: "Vary by Location Mode and Lighting Intent"
    ]
}

private Boolean matrixUsesMode() {
    return matrixVariation in ["mode", "modeIntent"]
}

private Boolean matrixUsesIntent() {
    return matrixVariation in ["intent", "modeIntent"]
}

private List matrixIntentBuckets() {
    if (!matrixUsesIntent()) return ["Any"]
    return bedroomRoomProfile() ? ["On", "Night"] : ["On", "Courtesy"]
}

private List matrixContexts() {
    List contexts = [[title: "All Modes", key: "all"]]

    if (matrixUsesMode()) {
        selectedMatrixModes().each { modeName ->
            contexts << [title: modeName, key: contextKey(modeName)]
        }
    }

    return contexts
}

private String rowName(String prefix, String context, String intent, def dev, Boolean override = false) {
    String rowPrefix = override ? "override_${prefix}" : prefix
    return "mx_${rowPrefix}_${context}_${intent}_${dev.id}".replaceAll("[^A-Za-z0-9_]", "_")
}

private String matrixName(String prefix, String context, String intent) {
    return "mx_${prefix}_${context}_${intent}".replaceAll("[^A-Za-z0-9_]", "_")
}

private def matrixOverrideSwitch(String context, String intent) {
    return settings[matrixName("overrideSwitch", context, intent)]
}

private Boolean overrideActive(String context, String intent) {
    def overrideSwitch = matrixOverrideSwitch(context, intent)
    return overrideSwitch?.currentValue("switch") == "on"
}

private List overrideSwitches() {
    List switches = []
    matrixContexts().each { contextConfig ->
        matrixIntentBuckets().each { intentBucket ->
            switches.addAll(asList(matrixOverrideSwitch(contextConfig.key, intentBucket)))
        }
    }
    return switches.unique { it?.id }.findAll { it }
}

// -------------------- Device Commands --------------------

private void setDimmer(def dimmer, Integer level, Integer transitionSecondsForCommand, Boolean forceCommand = true, Map stats = null) {
    String currentSwitch = dimmer.currentValue("switch")?.toString()
    Integer currentLevel = normalizedLevel(dimmer.currentValue("level"), -1)
    if (!forceCommand && currentSwitch == "on" && currentLevel == level) {
        incrementLightingStat(stats, "skips")
        debug "Skipping ${dimmer.displayName}; already on at level ${level}"
        trace "Skip setLevel ${dimmer.displayName}: current=${currentSwitch}/${currentLevel}, target=on/${level}, transition=${transitionSecondsForCommand}, force=${forceCommand}"
        return
    }

    try {
        incrementLightingStat(stats, "commands")
        String command = "setLevel ${dimmer.displayName} -> ${level}%/${transitionSecondsForCommand ?: 0}s"
        recordLightingCommand(stats, command)
        trace "Command setLevel ${dimmer.displayName}: current=${currentSwitch}/${currentLevel}, target=${level}, transition=${transitionSecondsForCommand}, force=${forceCommand}"
        Long commandStartedAt = now()
        if ((transitionSecondsForCommand ?: 0) > 0) {
            dimmer.setLevel(level, transitionSecondsForCommand)
        } else {
            dimmer.setLevel(level)
        }
        recordLightingCommandTiming(stats, command, commandStartedAt)
    } catch (Exception e) {
        log.warn "${app.label}: Could not set ${dimmer.displayName} to ${level}: ${e.message}"
    }
}

private Boolean setDimmerColorTemperature(def dev, Integer level, Integer colorTemperature, Integer transitionSecondsForCommand, Boolean forceCommand = true, Map stats = null) {
    if (!isDimmer(dev) || !isColorTemperatureDevice(dev)) return false

    String currentSwitch = dev.currentValue("switch")?.toString()
    Integer currentLevel = normalizedLevel(dev.currentValue("level"), -1)
    Integer currentCt = normalizedColorTemperature(dev.currentValue("colorTemperature"), -1)
    if (!forceCommand && currentSwitch == "on" && currentLevel == level && currentCt == colorTemperature) {
        incrementLightingStat(stats, "skips")
        debug "Skipping ${dev.displayName}; already on at level ${level} and CT ${colorTemperature}"
        trace "Skip setColorTemperature+level ${dev.displayName}: current=${currentSwitch}/${currentLevel}/${currentCt}, target=on/${level}/${colorTemperature}, transition=${transitionSecondsForCommand}, force=${forceCommand}"
        return true
    }

    String command = "setColorTemperature ${dev.displayName} -> ${colorTemperature}K/${level}%"
    try {
        incrementLightingStat(stats, "commands")
        recordLightingCommand(stats, command)
        trace "Command setColorTemperature+level ${dev.displayName}: current=${currentSwitch}/${currentLevel}/${currentCt}, target=${colorTemperature}/${level}, force=${forceCommand}"
        Long commandStartedAt = now()
        dev.setColorTemperature(colorTemperature, level)
        recordLightingCommandTiming(stats, command, commandStartedAt)
        return true
    } catch (Exception e) {
        trace "Combined setColorTemperature+level not available for ${dev.displayName}: ${e.message}"
        decrementLightingStat(stats, "commands")
        removeLastLightingCommand(stats, command)
        return false
    }
}

private void setColorTemperature(def dev, Integer colorTemperature, Boolean forceCommand = true, Map stats = null) {
    if (!isColorTemperatureDevice(dev)) return

    Integer currentCt = normalizedColorTemperature(dev.currentValue("colorTemperature"), -1)
    if (!forceCommand && currentCt == colorTemperature) {
        incrementLightingStat(stats, "skips")
        debug "Skipping ${dev.displayName}; already at CT ${colorTemperature}"
        trace "Skip setColorTemperature ${dev.displayName}: current=${currentCt}, target=${colorTemperature}, force=${forceCommand}"
        return
    }

    try {
        incrementLightingStat(stats, "commands")
        String command = "setColorTemperature ${dev.displayName} -> ${colorTemperature}K"
        recordLightingCommand(stats, command)
        trace "Command setColorTemperature ${dev.displayName}: current=${currentCt}, target=${colorTemperature}, force=${forceCommand}"
        Long commandStartedAt = now()
        dev.setColorTemperature(colorTemperature)
        recordLightingCommandTiming(stats, command, commandStartedAt)
    } catch (Exception e) {
        log.warn "${app.label}: Could not set ${dev.displayName} color temperature to ${colorTemperature}: ${e.message}"
    }
}

private void turnOnDevice(def dev, Boolean forceCommand = true, Map stats = null) {
    String currentSwitch = dev.currentValue("switch")?.toString()
    if (!forceCommand && currentSwitch == "on") {
        incrementLightingStat(stats, "skips")
        debug "Skipping ${dev.displayName}; already on"
        trace "Skip on ${dev.displayName}: current=${currentSwitch}, force=${forceCommand}"
        return
    }

    try {
        incrementLightingStat(stats, "commands")
        String command = "on ${dev.displayName}"
        recordLightingCommand(stats, command)
        trace "Command on ${dev.displayName}: current=${currentSwitch}, force=${forceCommand}"
        Long commandStartedAt = now()
        dev.on()
        recordLightingCommandTiming(stats, command, commandStartedAt)
    } catch (Exception e) {
        log.warn "${app.label}: Could not turn on ${dev.displayName}: ${e.message}"
    }
}

private void turnOffDevice(def dev, String reason, Integer transitionSecondsForCommand = 0, Map stats = null) {
    String currentSwitch = dev.currentValue("switch")?.toString()
    Integer currentLevel = isDimmer(dev) ? normalizedLevel(dev.currentValue("level"), -1) : null
    if (isDimmer(dev)) {
        try {
            incrementLightingStat(stats, "commands")
            String command = "setLevel ${dev.displayName} -> 0%/${transitionSecondsForCommand ?: 0}s"
            recordLightingCommand(stats, command)
            trace "Command off-level ${dev.displayName}: current=${currentSwitch}/${currentLevel}, target=0, transition=${transitionSecondsForCommand}, reason=${reason}"
            Long commandStartedAt = now()
            if ((transitionSecondsForCommand ?: 0) > 0) {
                dev.setLevel(0, transitionSecondsForCommand)
            } else {
                dev.setLevel(0)
            }
            recordLightingCommandTiming(stats, command, commandStartedAt)
            return
        } catch (Exception e) {
            log.warn "${app.label}: Could not fade ${dev.displayName} off (${reason}); trying off(): ${e.message}"
        }
    }

    try {
        incrementLightingStat(stats, "commands")
        String command = "off ${dev.displayName}"
        recordLightingCommand(stats, command)
        trace "Command off ${dev.displayName}: current=${currentSwitch}, reason=${reason}"
        Long commandStartedAt = now()
        dev.off()
        recordLightingCommandTiming(stats, command, commandStartedAt)
    } catch (Exception e) {
        log.warn "${app.label}: Could not turn off ${dev.displayName} (${reason}): ${e.message}"
    }
}

private Integer transitionSecondsFor(String reason, String actionType) {
    if (reason == "Room locked") return safeTransitionSeconds(lockedFadeOffTransitionSeconds, 30)
    if (actionType == "deactivation") return safeTransitionSeconds(deactivationTransitionSeconds, 30)
    if (reason in ["metaLightLevel changed", "metaLightColorTemperature changed"] && instantSceneTransitionActive()) return 0
    if (reason in ["metaLightLevel changed", "metaLightColorTemperature changed", "Location Mode changed", "override switch changed"]) {
        return safeTransitionSeconds(referenceTransitionSeconds, 10)
    }
    return safeTransitionSeconds(activationTransitionSeconds ?: transitionSeconds, 2)
}

private Integer safeTransitionSeconds(value, Integer fallback) {
    Integer seconds = fallback
    try {
        seconds = value == null ? fallback : value as Integer
    } catch (Exception ignored) {
        seconds = fallback
    }
    return Math.max(seconds, 0)
}

private void startLevelChange(String direction) {
    levelChangeDimmers().each { dimmer ->
        try {
            dimmer.startLevelChange(direction)
        } catch (Exception e) {
            log.warn "${app.label}: Could not start ${direction} level change on ${dimmer.displayName}: ${e.message}"
        }
    }
}

private void stopLevelChange() {
    levelChangeDimmers().each { dimmer ->
        try {
            dimmer.stopLevelChange()
        } catch (Exception e) {
            log.warn "${app.label}: Could not stop level change on ${dimmer.displayName}: ${e.message}"
        }
    }
}

private void allowNextLevelFollow() {
    state.allowLevelFollowUntil = now() + 5000
    state.suppressLevelFollowUntil = null
}

private void suppressNextLevelFollow() {
    state.suppressLevelFollowUntil = now() + 5000
}

private Boolean levelFollowAllowed() {
    Long allowedUntil = safeLong(state.allowLevelFollowUntil, 0L)
    Long suppressedUntil = safeLong(state.suppressLevelFollowUntil, 0L)
    Long current = now()
    return allowedUntil >= current && suppressedUntil < current
}

private void clearLevelFollowMarkers() {
    state.allowLevelFollowUntil = null
    state.suppressLevelFollowUntil = null
}

private void announceRoomControl(String message) {
    String text = message?.trim()
    if (!text) return

    asList(speechDevices).findAll { it }.each { speaker ->
        try {
            speaker.playTextAndResume(text)
        } catch (Throwable firstError) {
            try {
                speaker.playTextAndRestore(text)
            } catch (Throwable secondError) {
                try {
                    speaker.speak(text)
                } catch (Exception e) {
                    log.warn "${app.label}: Could not announce '${text}' on ${speaker.displayName}: ${e.message}"
                }
            }
        }
    }
}

private Boolean announceEchoSpeaksSleepRoutine(Boolean asleep) {
    Boolean announced = false

    asList(speechDevices).findAll { it }.each { speaker ->
        try {
            if (asleep) {
                speaker.sayGoodNight()
            } else {
                speaker.sayGoodMorning()
            }
            announced = true
        } catch (Throwable ignored) {
            // Not an Echo Speaks device, or this Echo Speaks command is unavailable.
        }
    }

    return announced
}

private void cycleSceneSwitch() {
    Boolean asleep = roomDevice()?.currentValue("roomState") == "Asleep"
    List scenes = asleep ? asleepSceneSwitches() : sceneSwitches()
    if (!scenes) {
        cycleShadowScene(asleep)
        return
    }

    String indexName = asleep ? "asleepSceneCycleIndex" : "sceneCycleIndex"
    Integer previousIndex = safeInteger(state[indexName], -1)
    Integer nextIndex = previousIndex + 1
    if (nextIndex >= scenes.size()) {
        nextIndex = 0
    }
    state[indexName] = nextIndex

    def scene = scenes[nextIndex]
    try {
        String sceneIds = scenes.collect { "${it.id}:${it.displayName}" }.join(", ")
        String sceneType = asleep ? "asleep scene" : "scene"
        debug "Activating ${sceneType} ${nextIndex + 1}/${scenes.size()} after index ${previousIndex}: ${scene.displayName}; scenes=${sceneIds}"
        setCustomLighting(true)
        scene.on()
    } catch (Exception e) {
        log.warn "${app.label}: Could not activate scene ${scene?.displayName}: ${e.message}"
    }

    publishActiveScene(scene.displayName?.toString())
    if (asleep) {
        Integer minutes = sceneNightExtensionMinutesForRoom()
        debug "Extending Night lighting for ${minutes} minutes from button 3 asleep scene"
        roomDevice()?.activateNightLighting(minutes)
    }
}

private void cycleShadowScene(Boolean asleep) {
    List shadowScenes = asleep ? asleepShadowScenes() : standardShadowScenes()
    String indexName = asleep ? "asleepShadowSceneIndex" : "shadowSceneIndex"
    Integer previousIndex = safeInteger(state[indexName], -1)
    Integer nextIndex = previousIndex + 1

    if (nextIndex >= shadowScenes.size()) {
        nextIndex = 0
    }

    state[indexName] = nextIndex
    Map shadowScene = shadowScenes[nextIndex]
    debug "Activating ${asleep ? 'asleep shadow scene' : 'shadow scene'} ${nextIndex + 1}/${shadowScenes.size()}: ${shadowScene.name}, level=${shadowScene.level}, ct=${shadowScene.ct}"
    setCustomLighting(true)
    markInstantSceneTransition()
    applyShadowScene(shadowScene)
    publishActiveScene(shadowScene.name as String)

    if (asleep) {
        Integer minutes = sceneNightExtensionMinutesForRoom()
        debug "Extending Night lighting for ${minutes} minutes from button 3 asleep shadow scene"
        roomDevice()?.activateNightLighting(minutes)
    }
}

private void applyShadowScene(Map shadowScene) {
    def room = roomDevice()
    if (!room) return

    try {
        room.setMetaLightSwitchState("on")
        room.setMetaLightColorTemperature(normalizedColorTemperature(shadowScene.ct, 2700))
        room.setMetaLightLevel(normalizedLevel(shadowScene.level, 50))
    } catch (Exception e) {
        log.warn "${app.label}: Could not apply shadow scene ${shadowScene?.name}: ${e.message}"
    }
}

private void markInstantSceneTransition() {
    state.instantSceneTransitionUntil = now() + 5000
}

private Boolean instantSceneTransitionActive() {
    return safeLong(state.instantSceneTransitionUntil, 0L) >= now()
}

private List standardShadowScenes() {
    return [
        [name: "Calm", level: 40, ct: 2400],
        [name: "Energize", level: 90, ct: 5000],
        [name: "Reading", level: 75, ct: 3500],
        [name: "Clean", level: 100, ct: 6000],
        [name: "Low Lights", level: 20, ct: 2200]
    ]
}

private List asleepShadowScenes() {
    return [
        [name: "Night Low", level: 8, ct: 2000],
        [name: "Night Calm", level: 15, ct: 2200],
        [name: "Night Reading", level: 35, ct: 2700]
    ]
}

private void setCustomLighting(Boolean enabled) {
    def room = roomDevice()
    if (!room) return

    String desired = enabled ? "on" : "off"
    if (room.currentValue("customLighting") == desired) return

    try {
        if (enabled) {
            room.activateCustomLighting()
        } else {
            room.clearCustomLighting()
        }
    } catch (Exception e) {
        log.warn "${app.label}: Could not set custom lighting ${desired}: ${e.message}"
    }
}

private void setRoomLevelAsCustom(Integer level) {
    def room = roomDevice()
    if (!room) return

    setCustomLighting(true)
    room.setLevel(level)
}

private void returnToAutomaticLighting() {
    state.sceneCycleIndex = -1
    state.asleepSceneCycleIndex = -1
    state.shadowSceneIndex = -1
    state.asleepShadowSceneIndex = -1
    setCustomLighting(false)
    publishActiveScene("Automatic")
    debug "Returning to automatic room-defined lighting"
    runIn(1, "reapplyAutomaticLighting", [overwrite: true])
}

def reapplyAutomaticLighting() {
    reassessLighting("return to automatic lighting")
}

private List sceneSwitches() {
    return asList(sceneCycleSwitches).findAll { it }.unique { it.id }
}

private List asleepSceneSwitches() {
    return asList(asleepSceneCycleSwitches).findAll { it }.unique { it.id }
}

private Integer boostedAsleepFallbackLevel() {
    Integer current = normalizedLevel(roomDevice()?.currentValue("metaLightLevel"), 0)
    Integer boost = normalizedLevel(asleepFallbackLevelBoost, 20)
    return normalizedLevel(current + boost, 20)
}

private void boostAsleepMetaLight(Integer level) {
    def room = roomDevice()
    if (!room) return

    try {
        room.setMetaLightSwitchState("on")
        room.setMetaLightLevel(level)
    } catch (Exception e) {
        log.warn "${app.label}: Could not boost asleep MetaLight to ${level}: ${e.message}"
    }
}

private void roomOnIfNeeded() {
    def room = roomDevice()
    if (!room) return

    if (room.currentValue("roomState") == "Asleep") {
        room.setAsleepSwitchState("off")
    }

    room.on()
}

private void recordPicoPresenceActivity(Integer button, evt) {
    if (picoPushesImplyPresenceActivity == false) return
    recordRoomPresenceActivity("Pico button ${button ?: 'unknown'} pushed: ${evt?.displayName ?: 'unknown device'}")
}

private void recordPicoEngagementActivity(Integer button, evt) {
    if (picoButtonsOneToFourEngage != true) return
    if (!(button in [1, 2, 3, 4])) return
    recordRoomEngagementActivity("Pico button ${button} pushed: ${evt?.displayName ?: 'unknown device'}")
}

private void recordRoomPresenceActivity(String reason) {
    if (!roomChildAppId) return

    try {
        parent.roomStateChildPresenceActivity(roomChildAppId, reason)
    } catch (Exception e) {
        log.warn "${app.label}: Could not record Room presence activity: ${e.message}"
    }
}

private void recordRoomEngagementActivity(String reason) {
    if (!roomChildAppId) return

    try {
        parent.roomStateChildEngagementActivity(roomChildAppId, reason)
    } catch (Exception e) {
        log.warn "${app.label}: Could not record Room engagement activity: ${e.message}"
    }
}

private void wakeAndUnlockRoom(String reason) {
    def room = roomDevice()
    if (!room) return

    debug "${reason}: waking and unlocking Room"
    try {
        room.setAsleepSwitchState("off")
    } catch (Exception ignored) {
        // Older MetaDevice code or non-bedroom rooms may not expose asleep controls.
    }
    room.unlock()
}

private void setRoomAsleepFromRemote(String reason) {
    if (!bedroomRoomProfile()) {
        debug "Ignoring ${reason} because room profile is not Bedroom"
        return
    }

    roomDevice()?.setAsleepSwitchState("on")
}

// -------------------- Control Filtering --------------------

private Boolean shouldAcceptControlEvent(evt) {
    if (controlFeedbackSuppressed(evt)) {
        debug "Ignoring app-issued control feedback ${evt.name}=${evt.value}: ${evt.displayName}"
        return false
    }

    if (!physicalControlEventsOnly) return true

    if (eventType(evt) == "physical") return true

    debug "Ignoring non-physical control event ${evt.name}=${evt.value}: ${evt.displayName}"
    return false
}

def suppressControlFeedbackForDeviceId(def deviceId, Integer seconds = 5) {
    suppressControlFeedbackDeviceId(deviceId, seconds)
}

def clearControlFeedbackForDeviceId(def deviceId) {
    String normalizedDeviceId = deviceId?.toString()
    if (!normalizedDeviceId) return

    Map suppressions = state.suppressControlFeedbackUntil instanceof Map ? state.suppressControlFeedbackUntil : [:]
    suppressions.remove(normalizedDeviceId)
    state.suppressControlFeedbackUntil = suppressions
}

private void suppressControlFeedback(def dev) {
    suppressControlFeedbackDeviceId(dev?.id, 5)
}

private void suppressControlFeedbackDeviceId(def deviceId, Integer seconds) {
    String normalizedDeviceId = deviceId?.toString()
    if (!normalizedDeviceId) return

    Map suppressions = state.suppressControlFeedbackUntil instanceof Map ? state.suppressControlFeedbackUntil : [:]
    Long current = now()
    suppressions = suppressions.findAll { key, value -> safeLong(value, 0L) >= current }
    suppressions[normalizedDeviceId] = current + (Math.max(safeInteger(seconds, 5), 1) * 1000)
    state.suppressControlFeedbackUntil = suppressions
}

private Boolean controlFeedbackSuppressed(evt) {
    String deviceId = eventDeviceId(evt)
    if (!deviceId) return false

    Map suppressions = state.suppressControlFeedbackUntil instanceof Map ? state.suppressControlFeedbackUntil : [:]
    return safeLong(suppressions[deviceId], 0L) >= now()
}

private String eventDeviceId(evt) {
    try {
        if (evt.deviceId != null) return evt.deviceId.toString()
    } catch (Throwable ignored) {
        // Try alternate event shape below.
    }

    try {
        return evt.device?.id?.toString()
    } catch (Throwable ignored) {
        return ""
    }
}

// -------------------- Room / Parent --------------------

private def roomDevice() {
    if (!roomChildAppId) return null

    try {
        return parent.roomStateChildRoomDevice(roomChildAppId)
    } catch (Exception e) {
        log.warn "${app.label}: Could not resolve Room device: ${e.message}"
        return null
    }
}

// Parent room registries probe child apps for these methods. Lighting apps are
// not rooms, so returning null keeps them out of room pickers without noisy logs.
def getManagedRoomDevice() {
    return null
}

def getManagedRoomDeviceLabel() {
    return null
}

def getSimpleHomeAppType() {
    return "roomLighting"
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

private Boolean roomLightingAllowed() {
    return validateChildUniqueness("Room Lighting") { parent.roomLightingRoomAllowed(roomChildAppId, app.id) }
}

private void updateAppLabel() {
    String desired = lightingName?.trim()
    if (!desired) {
        desired = roomDevice()?.displayName
        desired = desired ? "${desired} Lighting" : "Simple Room Lighting"
    }

    if (app.label != desired) {
        app.updateLabel(desired)
    }
}

// -------------------- Helpers --------------------

private List allAutomatedDevices() {
    return (asList(automatedDimmers) + asList(automatedSwitches)).unique()
}

private Boolean isDimmer(def dev) {
    return asList(automatedDimmers).any { it?.id == dev?.id }
}

private Boolean isColorTemperatureDevice(def dev) {
    try {
        if (dev?.hasCapability("ColorTemperature")) return true
    } catch (Throwable ignored) {
        // Fall through to attribute-based check.
    }
    return dev?.currentValue("colorTemperature") != null
}

private Boolean bedroomRoomProfile() {
    return roomInfo()?.roomProfile == "bedroom"
}

private Boolean announceRoomControlsEnabled() {
    return settingBool("announceRoomControls", false) && asList(speechDevices)
}

private Boolean useEchoSpeaksSleepRoutinesEnabled() {
    return settingBool("useEchoSpeaksSleepRoutines", true)
}

private String lockedAnnouncementText() {
    return roomAnnouncementText("locked", "Locked")
}

private String unlockedAnnouncementText() {
    return roomAnnouncementText("unlocked", "Unlocked")
}

private String asleepAnnouncementText() {
    return roomAnnouncementText("asleep", "Asleep")
}

private String awakeAnnouncementText() {
    return roomAnnouncementText("awake", "Awake")
}

private void syncRoomAnnouncementMessages(def room) {
    if (!room) return

    Map existing = roomAnnouncementMessages(room)
    Map defaults = [
        locked  : defaultRoomControlAnnouncement("Locked"),
        unlocked: defaultRoomControlAnnouncement("Unlocked"),
        asleep  : defaultRoomControlAnnouncement("Asleep"),
        awake   : defaultRoomControlAnnouncement("Awake")
    ]
    Map missing = [:]
    defaults.each { slot, text ->
        if (!existing[slot]) {
            missing[slot] = text
        }
    }

    if (!missing) {
        debug "Room announcement messages already seeded"
        return
    }

    try {
        room.setAnnouncementMessagesJson(groovy.json.JsonOutput.toJson(missing))
        debug "Seeded room announcement messages: ${missing.keySet().join(', ')}"
    } catch (Throwable e) {
        log.warn "${app.label}: Could not seed room announcement messages: ${e.message}"
    }
}

private String roomAnnouncementText(String slot, String stateName) {
    String text = roomAnnouncementMessages()[slot]?.toString()?.trim()
    if (text) return text
    return defaultRoomControlAnnouncement(stateName)
}

private Map roomAnnouncementMessages(def room = null) {
    String json = (room ?: roomDevice())?.currentValue("announcementMessagesJson")?.toString()
    if (!json?.trim()) return [:]

    try {
        def parsed = new groovy.json.JsonSlurper().parseText(json)
        if (!(parsed instanceof Map)) return [:]

        Map result = [:]
        parsed.each { key, value ->
            String slot = key?.toString()?.trim()?.toLowerCase()
            String text = value?.toString()?.trim()
            if (slot in ["locked", "unlocked", "asleep", "awake"] && text) {
                result[slot] = text
            }
        }
        return result
    } catch (Throwable e) {
        log.warn "${app.label}: Could not read room announcement messages: ${e.message}"
        return [:]
    }
}

private String customLightingOnText() {
    return customLightingText(roomInfo()?.customLightingOnText, parentCustomLightingOnText())
}

private String customLightingOffText() {
    return customLightingText(roomInfo()?.customLightingOffText, parentCustomLightingOffText())
}

private String customLightingText(value, String fallback) {
    String text = value?.toString()?.trim()
    return text ?: fallback
}

private String parentCustomLightingOnText() {
    try {
        return parent.customLightingOnText()
    } catch (Throwable ignored) {
        return '<audio src="soundbank://soundlibrary/alarms/beeps_and_bloops/bell_01"/>'
    }
}

private String parentCustomLightingOffText() {
    try {
        return parent.customLightingOffText()
    } catch (Throwable ignored) {
        return '<audio src="soundbank://soundlibrary/alarms/beeps_and_bloops/boing_01"/>'
    }
}

private String defaultRoomControlAnnouncement(String stateName) {
    return "${announcementRoomName()} ${stateName}"
}

private String announcementRoomName() {
    Map info = roomInfo()
    String name = info?.roomName?.toString()?.trim()
    if (name) return stripRoomPrefix(name)

    String label = info?.label?.toString()?.trim()
    if (label) return stripRoomPrefix(label)

    String deviceLabel = roomDevice()?.displayName?.toString()?.trim()
    return deviceLabel ? stripRoomPrefix(deviceLabel) : "Room"
}

private String stripRoomPrefix(String value) {
    return value?.startsWith("Room ") ? value.substring(5) : value
}

private List levelChangeDimmers() {
    List selected = asList(picoLevelChangeDimmers)
    return selected ?: asList(automatedDimmers)
}

private Boolean hasPicoRemotes() {
    return !asList(picoRemotes).isEmpty()
}

private List selectedMatrixModes() {
    if (!matrixModes) return []
    return matrixModes instanceof List ? matrixModes.collect { it.toString() } : [matrixModes.toString()]
}

private Map roomInfo() {
    if (!roomChildAppId) return [:]
    try {
        return parent.roomStateChildInfo(roomChildAppId) ?: [:]
    } catch (Exception e) {
        log.warn "${app.label}: Could not load Room info: ${e.message}"
        return [:]
    }
}

private Integer sceneNightExtensionMinutesForRoom() {
    Integer minutes = (sleepSceneTimeoutMinutes ?: 45) as Integer
    return Math.max(minutes, 1)
}

private String contextKey(String value) {
    return value.replaceAll("[^A-Za-z0-9]", "_")
}

private Integer normalizedUsableMinimum(value) {
    Integer minimum = 1
    try {
        minimum = value == null ? 1 : value as Integer
    } catch (Exception ignored) {
        minimum = 1
    }
    return Math.max(Math.min(minimum, 99), 1)
}

private Integer adjustedRoomLevel(Integer delta) {
    Integer current = normalizedLevel(roomDevice()?.currentValue("level"), 0)
    return normalizedLevel(current + delta, current)
}

private void publishActiveScene(String scene) {
    try {
        roomDevice()?.setActiveScene(scene)
    } catch (Exception e) {
        log.warn "${app.label}: Could not publish active scene ${scene}: ${e.message}"
    }
}

private void publishLastLightingAction(String action) {
    try {
        roomDevice()?.setLastLightingAction(action)
    } catch (Exception e) {
        log.warn "${app.label}: Could not publish last lighting action: ${e.message}"
    }
}

private Map lightingStats(List devices) {
    return [commands: 0, skips: 0, devices: safeInteger(devices?.size(), 0), commandList: [], commandTimings: []]
}

private void incrementLightingStat(Map stats, String key) {
    if (stats == null || key == null) return
    stats[key] = safeInteger(stats[key], 0) + 1
}

private void decrementLightingStat(Map stats, String key) {
    if (stats == null || key == null) return
    stats[key] = Math.max(safeInteger(stats[key], 0) - 1, 0)
}

private void recordLightingCommand(Map stats, String command) {
    if (stats == null || !command) return
    List commands = stats.commandList instanceof List ? stats.commandList : []
    commands << command
    stats.commandList = commands
}

private void removeLastLightingCommand(Map stats, String command) {
    if (stats == null || !command || !(stats.commandList instanceof List)) return
    List commands = stats.commandList
    if (commands && commands[-1] == command) {
        commands.remove(commands.size() - 1)
        stats.commandList = commands
    }
}

private void recordLightingCommandTiming(Map stats, String command, Long startedAt) {
    if (stats == null || !command || !startedAt) return
    Long elapsed = now() - startedAt
    List timings = stats.commandTimings instanceof List ? stats.commandTimings : []
    timings << "${command} ${elapsed}ms"
    stats.commandTimings = timings
}

private void logLightingTiming(String operation, Long startedAt, String reason, Map stats = null) {
    if (!startedAt) return

    Long elapsed = now() - startedAt
    String detail = "reason=${reason}, devices=${safeInteger(stats?.devices, 0)}, commands=${safeInteger(stats?.commands, 0)}, skips=${safeInteger(stats?.skips, 0)}"
    List commandList = stats?.commandList instanceof List ? stats.commandList : []
    if (commandList) {
        detail = "${detail}, commandList=${commandList.join('; ')}"
    }
    List commandTimings = stats?.commandTimings instanceof List ? stats.commandTimings : []
    if (commandTimings) {
        detail = "${detail}, commandTimings=${commandTimings.join('; ')}"
    }
    if (elapsed >= 1000L) {
        log.warn "${app.label}: ${operation} took ${elapsed}ms (${detail})"
    } else {
        trace "${operation} took ${elapsed}ms (${detail})"
    }
}

private void debug(String msg) {
    if (debugEnabled(debugLogging)) {
        log.debug "${app.label}: ${msg}"
    }
}

private void trace(String msg) {
    if (traceLightingCommands == true && debugEnabled(debugLogging)) {
        log.debug "${app.label}: TRACE ${msg}"
    }
}
