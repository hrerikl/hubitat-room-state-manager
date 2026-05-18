/**
 * Simple Room Lighting v2 - Child App
 *
 * Install as Apps Code:
 *   Name: Simple Room Lighting v2
 *   Namespace: lundby
 *
 * Opinionated room lighting adapter:
 *   Room MetaLight + lightingIntent + Location Mode -> selected physical lights
 *   selected physical controls -> Room on/off/level
 */

definition(
    name: "Simple Room Lighting v2",
    namespace: "lundby",
    author: "Erik Lundby / ChatGPT",
    description: "Simple room-centric lighting adapter for Room MetaLight output and physical controls.",
    category: "Convenience",
    parent: "lundby:Simple Room State Manager v2",
    singleInstance: false,
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage", title: "Simple Room Lighting", install: true, uninstall: true) {
        section("Room") {
            input "roomChildAppId", "enum", title: "Room", options: roomOptions(), required: true, submitOnChange: true
            input "lightingName", "text", title: "Lighting app name override, optional", required: false
        }

        section("Devices to automate") {
            input "automatedDimmers", "capability.switchLevel", title: "Dimmers to automate", multiple: true, required: false, submitOnChange: true
            input "automatedSwitches", "capability.switch", title: "Switch-only devices to automate", multiple: true, required: false, submitOnChange: true
        }

        section("Matrix options") {
            input "matrixVariation", "enum", title: "Matrix variation", options: matrixVariationOptions(), defaultValue: "all", required: true, submitOnChange: true
            if (matrixUsesMode()) {
                input "matrixModes", "enum", title: "Modes with custom matrix settings", options: locationModeOptions(), multiple: true, required: false, submitOnChange: true
            }
            input "offCondition", "enum", title: "Off condition", options: offConditionOptions(), defaultValue: "metaLightOff", required: true
            input "transitionSeconds", "number", title: "Dimmer transition seconds", defaultValue: 1, required: true
            input "reapplyOnModeChange", "bool", title: "Reassess matrix on Location Mode change when MetaLight is on", defaultValue: true, required: true
            input "alwaysActivateRows", "bool", title: "Always send activation commands for active rows", defaultValue: true, required: true
        }

        renderMatrixSections()

        section("Physical controls") {
            input "controlDimmers", "capability.switchLevel", title: "Physical dimmers that send on/off/level to Room", multiple: true, required: false
            input "controlSwitches", "capability.switch", title: "Physical switches that send on/off to Room", multiple: true, required: false
            input "physicalControlEventsOnly", "bool", title: "Only physical control events update Room", defaultValue: true, required: true
        }

        section("Room remote defaults") {
            input "picoRemotes", "capability.pushableButton", title: "5-button Pico remotes", multiple: true, required: false
            input "casetaDimmers", "capability.switchLevel", title: "4-button Caseta dimmers/switches", multiple: true, required: false
            input "picoLevelChangeDimmers", "capability.switchLevel", title: "Dimmers for held level-change buttons", multiple: true, required: false
            input "sceneCycleSwitches", "capability.switch", title: "Button 3 scene switches/activators", multiple: true, required: false
            input "picoStepSize", "number", title: "Push level step", defaultValue: 10, required: true
            input "sleepSceneTimeoutMinutes", "number", title: "When asleep, button 3 extends Night lighting minutes", defaultValue: 45, required: true
        }

        section("Announcements") {
            input "speechDevices", "capability.speechSynthesis", title: "Speech devices for Room state announcements", multiple: true, required: false
            input "announceRoomControls", "bool", title: "Announce Lock, Unlock, Sleep, and Wake", defaultValue: false, required: true
            if (announceRoomControls) {
                input "lockedAnnouncement", "text", title: "Locked message", defaultValue: "Room locked", required: true
                input "unlockedAnnouncement", "text", title: "Unlocked message", defaultValue: "Room unlocked", required: true
                input "asleepAnnouncement", "text", title: "Sleep message", defaultValue: "Room asleep", required: true
                input "awakeAnnouncement", "text", title: "Wake message", defaultValue: "Room awake", required: true
            }
        }

        section("Debug") {
            input "debugLogging", "bool", title: "Enable debug logging", defaultValue: true, required: true
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
    updateAppLabel()

    def room = roomDevice()
    subscribe(room, "switch.off", roomSwitchOffHandler)
    subscribe(room, "metaLightSwitch", reassessHandler)
    subscribe(room, "metaLightLevel", reassessHandler)
    subscribe(room, "lightingIntent", reassessHandler)
    subscribe(room, "lockedEnabled", roomControlAnnouncementHandler)
    subscribe(room, "asleepEnabled", roomControlAnnouncementHandler)
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

    reassessLighting("initialize")
}

// -------------------- Events --------------------

def reassessHandler(evt) {
    debug "Room lighting event ${evt.name}=${evt.value}"

    if (evt.name == "metaLightSwitch" && evt.value == "off" && offCondition == "metaLightOff") {
        applyOffCondition("MetaLight off")
        return
    }

    reassessLighting("${evt.name} changed")
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

    if (roomDevice()?.currentValue("metaLightSwitch") != "on") {
        debug "Mode change ignored because MetaLight is off"
        return
    }

    reassessLighting("Location Mode changed")
}

def overrideSwitchHandler(evt) {
    debug "Override switch ${evt.value}: ${evt.displayName}"
    reassessLighting("override switch changed")
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
        message = evt.value == "on" ? asleepAnnouncementText() : awakeAnnouncementText()
    }

    announceRoomControl(message)
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

    Integer level = normalizedLevel(evt.value, 0)
    debug "Control level ${level}: ${evt.displayName}"
    suppressNextLevelFollow()
    roomDevice()?.setLevel(level)
}

def picoPushedHandler(evt) {
    Integer button = eventIntegerValue(evt)
    debug "Pico pushed ${button}: ${evt.displayName}"

    def room = roomDevice()
    if (!room) return

    Integer step = normalizedLevel(picoStepSize, 10)
    if (step <= 0) step = 10

    if (button == 1) {
        roomOnAndEngageIfUnlocked()
    } else if (button == 2) {
        allowNextLevelFollow()
        room.setLevel(adjustedRoomLevel(step))
    } else if (button == 3) {
        cycleSceneSwitch()
    } else if (button == 4) {
        allowNextLevelFollow()
        room.setLevel(adjustedRoomLevel(-step))
    } else if (button == 5) {
        room.off()
    } else {
        debug "No default Pico pushed action for button ${button}"
    }
}

def picoHeldHandler(evt) {
    Integer button = eventIntegerValue(evt)
    debug "Pico held ${button}: ${evt.displayName}"

    if (button == 2) {
        startLevelChange("up")
    } else if (button == 4) {
        startLevelChange("down")
    } else if (button == 5) {
        roomDevice()?.setAsleepSwitchState("on")
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
        roomDevice()?.unlock()
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
        roomOnAndEngageIfUnlocked()
    } else if (button == 2) {
        allowNextLevelFollow()
        room.setLevel(adjustedRoomLevel(step))
    } else if (button == 3) {
        allowNextLevelFollow()
        room.setLevel(adjustedRoomLevel(-step))
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
        roomDevice()?.setAsleepSwitchState("on")
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

private void reassessLighting(String reason) {
    def room = roomDevice()
    if (!room) return

    String switchState = room.currentValue("metaLightSwitch") ?: "off"
    String intent = room.currentValue("lightingIntent") ?: "Off"
    Integer metaLevel = normalizedLevel(room.currentValue("metaLightLevel"), 0)
    String context = activeContextKey()
    String intentBucket = activeIntentBucket(intent)

    debug "Reassessing context=${context} intent=${intentBucket} roomIntent=${intent} meta=${switchState}/${metaLevel}: ${reason}"

    if (switchState != "on" || metaLevel <= 0 || !(intent in ["On", "Courtesy", "Night"])) {
        debug "Skipping activation because MetaLight is off or intent is not active"
        return
    }

    Boolean sameActiveMatrix = state.lastActiveMatrixContext == context && state.lastActiveMatrixIntent == intentBucket
    Boolean levelChangeOnly = reason == "metaLightLevel changed" && sameActiveMatrix && levelFollowAllowed()
    if (reason == "metaLightLevel changed") {
        clearLevelFollowMarkers()
    }

    state.lastActiveMatrixContext = context
    state.lastActiveMatrixIntent = intentBucket
    applyIntentRows(context, intentBucket, metaLevel, levelChangeOnly, reason)
}

private void applyIntentRows(String context, String intentBucket, Integer metaLevel, Boolean levelChangeOnly = false, String reason = "") {
    Boolean useOverride = overrideActive(context, intentBucket)
    Boolean forceActivation = alwaysActivateRowsEnabled()
    if (useOverride) {
        debug "Using override matrix for context=${context} intent=${intentBucket}"
    }

    allAutomatedDevices().each { dev ->
        if (!rowAct(context, intentBucket, dev, useOverride)) return

        String switchCommand = rowSwitch(context, intentBucket, dev, useOverride)
        String levelMode = isDimmer(dev) ? rowLevelMode(context, intentBucket, dev, useOverride) : "none"
        Boolean skipInitial = isDimmer(dev) && switchCommand == "on" && levelMode == "followSkip"
        if (skipInitial && shouldSkipInitialActivation(reason, levelChangeOnly)) {
            debug "Skipping initial activation for ${dev.displayName}"
            return
        }

        if (switchCommand == "off") {
            turnOffDevice(dev, "activation switch off")
        } else if (isDimmer(dev)) {
            if (levelMode == "none") {
                turnOnDevice(dev, forceActivation)
            } else {
                Integer level = levelMode == "explicit" ? rowExplicitLevel(context, intentBucket, dev, metaLevel, useOverride) : metaLevel
                setDimmer(dev, level, forceActivation)
            }
        } else {
            turnOnDevice(dev, forceActivation)
        }
    }
}

private Boolean shouldSkipInitialActivation(String reason, Boolean levelChangeOnly) {
    if (levelChangeOnly) return false
    return reason in ["initialize", "metaLightSwitch changed", "lightingIntent changed"]
}

private void applyOffCondition(String reason) {
    String context = (state.lastActiveMatrixContext ?: activeContextKey()) as String
    String intentBucket = (state.lastActiveMatrixIntent ?: activeIntentBucket(roomDevice()?.currentValue("lightingIntent")?.toString())) as String
    debug "Applying off condition context=${context} intent=${intentBucket}: ${reason}"
    applyOffRows(context, intentBucket, reason)
}

private void applyOffRows(String context, String intentBucket, String reason) {
    Boolean useOverride = overrideActive(context, intentBucket)
    allAutomatedDevices().each { dev ->
        if (rowOff(context, intentBucket, dev, useOverride)) {
            turnOffDevice(dev, reason)
        }
    }
}

private void recoverSimpleHome() {
    def room = roomDevice()
    if (!room) return

    String switchState = room.currentValue("metaLightSwitch") ?: "off"
    String intent = room.currentValue("lightingIntent") ?: "Off"
    Integer metaLevel = normalizedLevel(room.currentValue("metaLightLevel"), 0)

    if (switchState == "on" && metaLevel > 0 && intent in ["On", "Courtesy", "Night"]) {
        debug "Recover Simple Home: reapplying active matrix"
        reassessLighting("Simple Home recovery")
    } else {
        debug "Recover Simple Home: applying Off rows"
        applyOffCondition("Simple Home recovery")
    }
}

private Boolean rowAct(String context, String intent, def dev, Boolean override = false) {
    return settingBool(rowName("act", context, intent, dev, override), defaultAct(context, intent))
}

private Boolean rowOff(String context, String intent, def dev, Boolean override = false) {
    return settingBool(rowName("off", context, intent, dev, override), true)
}

private String rowLevelMode(String context, String intent, def dev, Boolean override = false) {
    String mode = (settings[rowName("levelMode", context, intent, dev, override)] ?: "follow").toString()
    if (mode == "follow" && rowLegacySkipInitial(context, intent, dev, override)) return "followSkip"
    return mode
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

private Boolean defaultAct(String context, String intent) {
    if (context != "all") return false
    return intent in ["Any", "On"]
}

private Boolean alwaysActivateRowsEnabled() {
    return settings?.alwaysActivateRows != false
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
            input rowName("off", context, intent, dev, override), "bool", title: "Off", defaultValue: true, required: true

            if (settingBool(actName, defaultAct(context, intent))) {
                input switchName, "enum", title: "Switch", options: switchCommandOptions(), defaultValue: "on", required: true, submitOnChange: true
            }

            if (isDimmer(dev) && settingBool(actName, defaultAct(context, intent)) && (settings[switchName] ?: "on") == "on") {
                input rowName("levelMode", context, intent, dev, override), "enum", title: "Level", options: levelModeOptions(), defaultValue: "follow", required: true, submitOnChange: true
                if (settings[rowName("levelMode", context, intent, dev, override)] == "explicit") {
                    input rowName("level", context, intent, dev, override), "number", title: "Explicit level", required: true
                }
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
        String switchCommand = rowAct(context, intent, dev, override) ? rowSwitch(context, intent, dev, override) : ""
        String level = ""

        if (isDimmer(dev) && rowAct(context, intent, dev, override) && switchCommand == "on") {
            String mode = rowLevelMode(context, intent, dev, override)
            level = mode == "explicit" ? "${rowExplicitLevel(context, intent, dev, 100, override)}" : levelModeOptions()[mode]
        }

        return """
            <tr>
                <td>${htmlEscape(dev.displayName ?: dev.name ?: dev.id)}</td>
                <td>${isDimmer(dev) ? "Dimmer" : "Switch"}</td>
                <td>${act}</td>
                <td>${off}</td>
                <td>${switchCommand}</td>
                <td>${level ?: ""}</td>
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
                </tr>
            </thead>
            <tbody>${rows}</tbody>
        </table>
    """
}

private Map levelModeOptions() {
    return [
        follow    : "Follow MetaLight",
        followSkip: "Follow MetaLight (skip initial)",
        explicit  : "Explicit level",
        none      : "No level command"
    ]
}

private Map switchCommandOptions() {
    return [
        on : "on",
        off: "off"
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

private void setDimmer(def dimmer, Integer level, Boolean forceCommand = true) {
    if (!forceCommand && dimmer.currentValue("switch") == "on" && normalizedLevel(dimmer.currentValue("level"), -1) == level) {
        debug "Skipping ${dimmer.displayName}; already on at level ${level}"
        return
    }

    try {
        if (((transitionSeconds ?: 0) as Integer) > 0) {
            dimmer.setLevel(level, transitionSeconds as Integer)
        } else {
            dimmer.setLevel(level)
        }
    } catch (Exception e) {
        log.warn "${app.label}: Could not set ${dimmer.displayName} to ${level}: ${e.message}"
    }
}

private void turnOnDevice(def dev, Boolean forceCommand = true) {
    if (!forceCommand && dev.currentValue("switch") == "on") {
        debug "Skipping ${dev.displayName}; already on"
        return
    }

    try {
        dev.on()
    } catch (Exception e) {
        log.warn "${app.label}: Could not turn on ${dev.displayName}: ${e.message}"
    }
}

private void turnOffDevice(def dev, String reason) {
    try {
        dev.off()
    } catch (Exception e) {
        log.warn "${app.label}: Could not turn off ${dev.displayName} (${reason}): ${e.message}"
    }
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
            speaker.speak(text)
        } catch (Exception e) {
            log.warn "${app.label}: Could not speak '${text}' on ${speaker.displayName}: ${e.message}"
        }
    }
}

private void cycleSceneSwitch() {
    List scenes = asList(sceneCycleSwitches).findAll { it }.unique { it.id }
    if (!scenes) {
        debug "No scene switches configured for button 3"
        return
    }

    Integer previousIndex = safeInteger(state.sceneCycleIndex, -1)
    Integer nextIndex = previousIndex + 1
    if (nextIndex >= scenes.size()) {
        nextIndex = 0
    }
    state.sceneCycleIndex = nextIndex

    def scene = scenes[nextIndex]
    try {
        String sceneIds = scenes.collect { "${it.id}:${it.displayName}" }.join(", ")
        debug "Activating scene ${nextIndex + 1}/${scenes.size()} after index ${previousIndex}: ${scene.displayName}; scenes=${sceneIds}"
        scene.on()
    } catch (Exception e) {
        log.warn "${app.label}: Could not activate scene ${scene?.displayName}: ${e.message}"
    }

    if (roomDevice()?.currentValue("roomState") == "Asleep") {
        Integer minutes = sceneNightExtensionMinutesForRoom()
        debug "Extending Night lighting for ${minutes} minutes from button 3 scene"
        roomDevice()?.activateNightLighting(minutes)
    }
}

private void roomOnAndEngageIfUnlocked() {
    def room = roomDevice()
    if (!room) return

    if (room.currentValue("roomState") == "Asleep") {
        room.activateNightLighting(0)
        return
    }

    room.on()

    if (room.currentValue("lock") == "locked" || room.currentValue("lockedEnabled") == "on") {
        debug "Skipping engage because Room is locked"
        return
    }

    try {
        room.setEngagedSwitchState("on")
    } catch (Exception e) {
        log.warn "${app.label}: Could not engage Room from remote button 1: ${e.message}"
    }
}

// -------------------- Control Filtering --------------------

private Boolean shouldAcceptControlEvent(evt) {
    if (!physicalControlEventsOnly) return true

    if (eventType(evt) == "physical") return true

    debug "Ignoring non-physical control event ${evt.name}=${evt.value}: ${evt.displayName}"
    return false
}

private String eventType(evt) {
    try {
        return evt.type?.toString()
    } catch (Throwable ignored) {
        return ""
    }
}

// -------------------- Room / Parent --------------------

private Map roomOptions() {
    try {
        return parent.roomStateChildOptions(app.id) ?: [:]
    } catch (Exception e) {
        log.warn "${app.label}: Could not load room options: ${e.message}"
        return [:]
    }
}

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

private Boolean bedroomRoomProfile() {
    return roomInfo()?.roomProfile == "bedroom"
}

private Boolean announceRoomControlsEnabled() {
    return announceRoomControls == true && asList(speechDevices)
}

private String lockedAnnouncementText() {
    return (lockedAnnouncement ?: "Room locked").toString()
}

private String unlockedAnnouncementText() {
    return (unlockedAnnouncement ?: "Room unlocked").toString()
}

private String asleepAnnouncementText() {
    return (asleepAnnouncement ?: "Room asleep").toString()
}

private String awakeAnnouncementText() {
    return (awakeAnnouncement ?: "Room awake").toString()
}

private List levelChangeDimmers() {
    List selected = asList(picoLevelChangeDimmers)
    return selected ?: asList(automatedDimmers)
}

private List selectedMatrixModes() {
    if (!matrixModes) return []
    return matrixModes instanceof List ? matrixModes.collect { it.toString() } : [matrixModes.toString()]
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

private Integer safeInteger(value, Integer fallback) {
    try {
        return value == null ? fallback : value as Integer
    } catch (Exception ignored) {
        return fallback
    }
}

private Long safeLong(value, Long fallback) {
    try {
        return value == null ? fallback : value as Long
    } catch (Exception ignored) {
        return fallback
    }
}

private String currentLocationModeName() {
    try {
        return location?.mode?.toString()
    } catch (Exception ignored) {
        return null
    }
}

private String contextKey(String value) {
    return value.replaceAll("[^A-Za-z0-9]", "_")
}

private Boolean settingBool(String name, Boolean fallback) {
    def value = settings[name]
    if (value == null) return fallback
    return value == true || value?.toString() == "true"
}

private String htmlEscape(value) {
    return "${value ?: ''}"
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
}

private List asList(def value) {
    if (!value) return []
    return value instanceof List ? value : [value]
}

private Integer normalizedLevel(value, Integer fallback) {
    Integer level = fallback == null ? 0 : fallback
    try {
        level = value == null ? level : value as Integer
    } catch (Exception ignored) {
        level = fallback == null ? 0 : fallback
    }
    return Math.max(Math.min(level, 100), 0)
}

private Integer adjustedRoomLevel(Integer delta) {
    Integer current = normalizedLevel(roomDevice()?.currentValue("level"), 0)
    return normalizedLevel(current + delta, current)
}

private Integer eventIntegerValue(evt) {
    try {
        return evt.value as Integer
    } catch (Exception ignored) {
        return null
    }
}

private void debug(String msg) {
    if (debugLogging) {
        log.debug "${app.label}: ${msg}"
    }
}
