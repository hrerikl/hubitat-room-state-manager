/**
 * Simple Home - Parent App
 *
 * Install as Apps Code:
 *   Name: Simple Home
 *   Namespace: lundby
 *
 * Provides parent container plus one-time setup helper for reciprocal neighbors. Neighbor relationships are stored by child app ID.
 */

definition(
    name: "Simple Home",
    namespace: "lundby",
    author: "Erik Lundby / ChatGPT",
    description: "Parent app for lightweight room state child apps.",
    category: "Convenience",
    singleInstance: true,
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage", title: "Simple Home", install: true, uninstall: true) {
        section("Rooms") {
            app(
                name: "childApps",
                appName: "Simple Room State",
                namespace: "lundby",
                title: "Add a room",
                multiple: true
            )
            paragraph roomSummaryText()
        }

        section("House mode") {
            app(
                name: "modeApps",
                appName: "Simple Mode Manager v2",
                namespace: "lundby",
                title: "Add mode manager",
                multiple: true
            )
        }

        section("Lighting") {
            app(
                name: "lightingApps",
                appName: "Simple Room Lighting v2",
                namespace: "lundby",
                title: "Add room lighting",
                multiple: true
            )
            app(
                name: "houseIntentLightingApps",
                appName: "Simple House Intent Lighting",
                namespace: "lundby",
                title: "Add House Intent lighting",
                multiple: true
            )
        }

        section("Circadian lighting") {
            input "defaultCircadianReferenceBulb", "capability.colorTemperature", title: "House reference bulb", multiple: false, required: false
            input "useHouseIntentVirtualRoom", "bool", title: "Use House Intent Virtual Room", defaultValue: false, required: true
            paragraph houseIntentSummaryText()
            app(
                name: "circadianApps",
                appName: "Simple Circadian Lighting v2",
                namespace: "lundby",
                title: "Add circadian reference lighting",
                multiple: true
            )
        }

        section("Recovery") {
            paragraph "Creates a Recover Simple Home switch. Turn it on from Rule Machine, dashboards, or voice assistants to ask child apps to reassert their current state."
        }

        section("Shared activators") {
            paragraph "Creates a Someone Arrived switch. Mode Manager pulses it when a selected presence input changes to present/home."
        }

        section("Announcement defaults") {
            input "defaultCustomLightingOnText", "text", title: "Custom lighting on text", defaultValue: '<audio src="soundbank://soundlibrary/alarms/beeps_and_bloops/bell_01"/>', required: true
            input "defaultCustomLightingOffText", "text", title: "Custom lighting off text", defaultValue: '<audio src="soundbank://soundlibrary/alarms/beeps_and_bloops/boing_01"/>', required: true
        }

        section("Maintenance") {
            input "reinitializeChildrenNow", "button", title: "Reinitialize child apps"
            paragraph "Use after code changes to rebuild child app subscriptions and schedules without opening each child app."
        }
    }
}

def installed() {
    log.info "Installed Simple Home"
    initialize()
}

def updated() {
    log.info "Updated Simple Home"
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    createOrUpdateSharedDevices()
    if (useHouseIntentVirtualRoom) {
        createOrUpdateHouseIntentVirtualRoom()
    } else if (settings?.containsKey("useHouseIntentVirtualRoom") && useHouseIntentVirtualRoom == false) {
        dewireHouseIntentVirtualRoomIfNeeded()
    }
    subscribe(recoveryDevice(), "switch.on", recoverySwitchOnHandler)
}

def appButtonHandler(String buttonName) {
    if (buttonName == "reinitializeChildrenNow") {
        reinitializeChildApps()
    }
}

String roomSummaryText() {
    List rows = (childApps ?: []).collect { child ->
        try {
            String label = child.getManagedRoomDeviceLabel()
            if (!label || label == "null") return null
            String profile = profileLabel(child.getRoomProfile())
            return "${label} - ${profile}"
        } catch (Throwable ignored) {
            return null
        }
    }.findAll { it }

    if (!rows) return "No Simple Room State rooms configured yet."
    return "Rooms:\n${rows.sort().join('\n')}"
}

String houseIntentSummaryText() {
    def child = houseIntentChildApp()
    String childText = child ? "House Intent room: ${child.label ?: child.id}" : "House Intent room: not configured"
    String referenceText = defaultCircadianReferenceBulb ? "House reference bulb: ${defaultCircadianReferenceBulb.displayName}" : "House reference bulb: not selected"
    return "${referenceText}\n${childText}"
}

private String profileLabel(String profile) {
    if (profile == "houseIntent") return "House Intent"
    if (profile == "bedroom") return "Bedroom"
    return "Standard"
}

private void createOrUpdateHouseIntentVirtualRoom() {
    def child = houseIntentChildApp()
    def rawReference = rawCircadianReferenceBulb(child)

    if (!child) {
        try {
            child = addChildApp("lundby", "Simple Room State", "Room House Intent", [
                settings: [
                    roomProfile             : [type: "enum", value: "houseIntent"],
                    roomName                : [type: "text", value: "House Intent"],
                    followCircadianReference: [type: "bool", value: true],
                    createDevicesNow        : [type: "bool", value: true]
                ]
            ])
            log.info "Simple Home: Created House Intent virtual room."
        } catch (Throwable e) {
            log.warn "Simple Home: Could not create House Intent virtual room: ${e.message}"
            return
        }
    }

    rawReference = rawCircadianReferenceBulb(child)

    try {
        child.configureHouseIntentFromParent(rawReference)
    } catch (Throwable e) {
        log.warn "Simple Home: Could not configure House Intent virtual room: ${e.message}"
    }

    createOrUpdateHouseIntentLighting(child)

    def houseReference = null
    try {
        houseReference = child.getManagedMetaLightDevice()
    } catch (Throwable e) {
        log.warn "Simple Home: Could not get House Intent MetaLight device: ${e.message}"
    }

    if (houseReference) {
        try {
            houseReference.on()
        } catch (Throwable e) {
            log.warn "Simple Home: Could not turn on House Intent room reference: ${e.message}"
        }

        if (sameDevice(rawReference, houseReference)) {
            log.warn "Simple Home: House reference already points at House Intent. Confirm the House Intent child has an override raw circadian reference selected."
        } else {
            try {
                app.updateSetting("defaultCircadianReferenceBulb", [type: "capability.colorTemperature", value: houseReference.id])
                log.info "Simple Home: House reference bulb set to ${houseReference.displayName}."
            } catch (Exception e) {
                log.warn "Simple Home: Could not set House reference bulb: ${e.message}"
            }
        }
    } else {
        log.warn "Simple Home: House Intent virtual room was configured, but no House Intent MetaLight device was available to set as the House reference."
    }

}

private void createOrUpdateHouseIntentLighting(def houseIntentChild) {
    if (!houseIntentChild) return

    def lighting = houseIntentLightingChildApp()
    if (!lighting) {
        try {
            lighting = addChildApp("lundby", "Simple House Intent Lighting", "# House Intent Lighting", [
                settings: [
                    roomChildAppId: [type: "enum", value: houseIntentChild.id?.toString()]
                ]
            ])
            log.info "Simple Home: Created House Intent lighting app."
        } catch (Throwable e) {
            log.warn "Simple Home: Could not create House Intent lighting app: ${e.message}"
            return
        }
    }

    try {
        lighting.configureHouseIntentLightingFromParent(houseIntentChild.id)
    } catch (Throwable e) {
        log.warn "Simple Home: Could not configure House Intent lighting app: ${e.message}"
    }
}

private void dewireHouseIntentVirtualRoomIfNeeded() {
    def child = houseIntentChildApp()
    if (!child) return

    def houseReference = null
    try {
        houseReference = child.getManagedMetaLightDevice()
    } catch (Throwable ignored) {
        houseReference = null
    }

    if (!sameDevice(defaultCircadianReferenceBulb, houseReference)) return

    def rawReference = rawCircadianReferenceBulb(child)
    if (!rawReference || sameDevice(rawReference, houseReference)) {
        log.warn "Simple Home: Could not dewire House Intent reference because no raw Circadian reference bulb was found."
        return
    }

    try {
        app.updateSetting("defaultCircadianReferenceBulb", [type: "capability.colorTemperature", value: rawReference.id])
        log.info "Simple Home: House reference bulb reset to ${rawReference.displayName}."
    } catch (Exception e) {
        log.warn "Simple Home: Could not reset House reference bulb: ${e.message}"
    }
}

private def rawCircadianReferenceBulb(def houseIntentChild = null) {
    def circadianReference = circadianAppReferenceBulb()
    if (circadianReference) return circadianReference

    try {
        def childReference = houseIntentChild?.getCircadianReferenceBulb()
        if (childReference) return childReference
    } catch (Throwable ignored) {
        // Fall through to parent setting.
    }

    def houseReference = null
    try {
        houseReference = houseIntentChild?.getManagedMetaLightDevice()
    } catch (Throwable ignored) {
        houseReference = null
    }

    return sameDevice(defaultCircadianReferenceBulb, houseReference) ? null : defaultCircadianReferenceBulb
}

private def circadianAppReferenceBulb() {
    def appWithReference = (circadianApps ?: []).find { child ->
        try {
            return child.getReferenceBulb() != null
        } catch (Throwable ignored) {
            return false
        }
    }

    try {
        return appWithReference?.getReferenceBulb()
    } catch (Throwable ignored) {
        return null
    }
}

private def houseIntentChildApp() {
    return (childApps ?: []).find { child ->
        try {
            if (child.getRoomProfile() == "houseIntent") return true
            if (child.getConfiguredRoomName() == "House Intent") return true
        } catch (Throwable ignored) {
            // Ignore non-room child apps.
        }
        return child?.label == "Room House Intent"
    }
}

private def houseIntentLightingChildApp() {
    return (houseIntentLightingApps ?: []).find { child -> child }
}

private Boolean sameDevice(def first, def second) {
    if (!first || !second) return false
    return first.id?.toString() == second.id?.toString()
}

def reinitializeChildApps() {
    createOrUpdateSharedDevices()

    Integer attempted = 0
    Integer succeeded = 0

    allManagedChildren().each { child ->
        attempted++
        try {
            child.reinitializeFromParent()
            succeeded++
        } catch (Throwable e) {
            log.warn "Simple Home: Could not reinitialize child ${child?.label ?: child?.id}: ${e.message}"
        }
    }

    log.info "Simple Home: Reinitialized ${succeeded} of ${attempted} child app(s)."
}

def componentOn(childDevice) {
    if (childDevice?.deviceNetworkId == recoveryDeviceNetworkId()) {
        log.info "Simple Home: Recover Simple Home requested."
        runIn(1, resetRecoverySwitch, [overwrite: true])
    } else if (childDevice?.deviceNetworkId == arrivalDeviceNetworkId()) {
        log.info "Simple Home: Someone Arrived activated."
        runIn(30, resetArrivalSwitch, [overwrite: true])
    }
}

def componentOff(childDevice) {
    // Parent-owned recovery switch is momentary. No action needed on off.
}

def ensureArrivalDevice() {
    createOrUpdateArrivalDevice()
    return arrivalDevice()
}

def recoverySwitchOnHandler(evt) {
    log.info "Simple Home: Recover Simple Home switch event received."
    runIn(1, resetRecoverySwitch, [overwrite: true])
}

def resetRecoverySwitch() {
    try {
        recoveryDevice()?.setSwitchState("off")
    } catch (Exception e) {
        log.warn "Simple Home: Could not reset Recover Simple Home switch: ${e.message}"
    }
}

def pulseArrivalDevice(Integer resetSeconds = 30) {
    try {
        def dev = arrivalDevice()
        dev?.setSwitchState("off")
        dev?.setSwitchState("on")
        runIn(Math.max(resetSeconds ?: 30, 1), resetArrivalSwitch, [overwrite: true])
    } catch (Exception e) {
        log.warn "Simple Home: Could not pulse Someone Arrived switch: ${e.message}"
    }
}

def resetArrivalSwitch() {
    try {
        arrivalDevice()?.setSwitchState("off")
    } catch (Exception e) {
        log.warn "Simple Home: Could not reset Someone Arrived switch: ${e.message}"
    }
}

def recoveryDevice() {
    return getChildDevice(recoveryDeviceNetworkId())
}

def arrivalDevice() {
    return getChildDevice(arrivalDeviceNetworkId())
}

def circadianReferenceBulb() {
    return defaultCircadianReferenceBulb
}

String customLightingOnText() {
    return customLightingText(defaultCustomLightingOnText, '<audio src="soundbank://soundlibrary/alarms/beeps_and_bloops/bell_01"/>')
}

String customLightingOffText() {
    return customLightingText(defaultCustomLightingOffText, '<audio src="soundbank://soundlibrary/alarms/beeps_and_bloops/boing_01"/>')
}

private String customLightingText(value, String fallback) {
    String text = value?.toString()?.trim()
    return text ?: fallback
}

private void createOrUpdateRecoveryDevice() {
    String dni = recoveryDeviceNetworkId()
    String label = "Recover Simple Home"
    def child = getChildDevice(dni)

    if (!child) {
        try {
            child = addChildDevice("lundby", "Simple Room Child Switch Device", dni, [
                label      : label,
                name       : label,
                isComponent: true
            ])
        } catch (Exception e) {
            log.warn "Simple Home: Could not create Recover Simple Home switch: ${e.message}"
            return
        }
    }

    try {
        if (child.displayName != label) {
            child.setLabel(label)
        }
        child.initialize()
    } catch (Exception e) {
        log.warn "Simple Home: Could not initialize Recover Simple Home switch: ${e.message}"
    }
}

private void createOrUpdateSharedDevices() {
    createOrUpdateRecoveryDevice()
    createOrUpdateArrivalDevice()
}

private void createOrUpdateArrivalDevice() {
    String dni = arrivalDeviceNetworkId()
    String label = "Someone Arrived"
    def child = getChildDevice(dni)

    if (!child) {
        try {
            child = addChildDevice("lundby", "Simple Room Child Switch Device", dni, [
                label      : label,
                name       : label,
                isComponent: true
            ])
        } catch (Exception e) {
            log.warn "Simple Home: Could not create Someone Arrived switch: ${e.message}"
            return
        }
    }

    try {
        if (child.displayName != label) {
            child.setLabel(label)
        }
        child.initialize()
    } catch (Exception e) {
        log.warn "Simple Home: Could not initialize Someone Arrived switch: ${e.message}"
    }
}

private String recoveryDeviceNetworkId() {
    return "simple-home-recovery-${app.id}"
}

private String arrivalDeviceNetworkId() {
    return "simple-home-arrival-${app.id}"
}

private List allManagedChildren() {
    return ((childApps ?: []) + (modeApps ?: []) + (lightingApps ?: []) + (houseIntentLightingApps ?: []) + (circadianApps ?: [])).findAll { it }
}

/**
 * One-time setup helper.
 *
 * Called by a child app when the user presses:
 *   "Add this room back to selected neighbors"
 *
 * Example:
 *   Utility selects Media Room as a neighbor.
 *   Pressing the button in Utility asks the parent to add Utility's Room device
 *   to Media Room's neighbor list once.
 *
 * After this runs, each child app continues to own its own neighbor list.
 */
def addThisRoomToSelectedNeighbors(sourceChildAppId) {
    def sourceChild = childApps?.find { childAppId(it) == "${sourceChildAppId}" }

    if (!sourceChild) {
        log.warn "Simple Home: Reciprocal neighbor setup failed. Could not find source child app ${sourceChildAppId}."
        return
    }

    String sourceRoomLabel = sourceChild.getManagedRoomDeviceLabel()
    List selectedNeighborChildIds = normalizeIdList(sourceChild.getSelectedNeighborChildAppIds())

    if (!selectedNeighborChildIds) {
        log.info "${sourceChild.label}: No selected neighbor rooms to update."
        return
    }

    Integer changed = 0
    Integer matched = 0

    childApps?.each { targetChild ->
        String targetChildAppId = childAppId(targetChild)

        if (targetChildAppId == "${sourceChildAppId}") {
            return
        }

        if (selectedNeighborChildIds.contains(targetChildAppId)) {
            matched++
            Boolean added = targetChild.addNeighborChildAppId(sourceChildAppId.toString())
            if (added) {
                changed++
                log.info "${sourceChild.label}: Added ${sourceRoomLabel} as reciprocal neighbor to ${targetChild.label}."
            } else {
                log.info "${sourceChild.label}: ${sourceRoomLabel} was already a neighbor of ${targetChild.label}, or could not be added."
            }
        }
    }

    if (matched == 0) {
        log.warn "${sourceChild.label}: No child app matched the selected neighbor room IDs."
    } else {
        log.info "${sourceChild.label}: Reciprocal neighbor setup complete. Matched ${matched} selected neighbor room(s), updated ${changed}."
    }
}

// -------------------- Room Child Registry --------------------

Map neighborRoomOptions(def requestingChildAppId) {
    return managedRoomOptions(requestingChildAppId, false)
}

Map roomStateChildOptions(def requestingChildAppId) {
    return managedRoomOptions(requestingChildAppId, true)
}

private Map managedRoomOptions(def requestingChildAppId, Boolean includeHouseIntent) {
    try {
        Map opts = [:]
        childApps?.each { child ->
            String id = child?.id?.toString()
            if (id == "${requestingChildAppId}") return

            try {
                if (!includeHouseIntent && child.getRoomProfile() == "houseIntent") return
                String label = child.getManagedRoomDeviceLabel()
                if (id && label) {
                    opts[(id)] = label
                }
            } catch (Throwable ignored) {
                // Ignore non-room child apps.
            }
        }
        return opts.sort { it.value }
    } catch (Exception e) {
        log.warn "Simple Home: Could not build neighbor room options: ${e.message}"
        return [:]
    }
}

Map roomStateChildInfo(def childAppId) {
    def child = childApps?.find { it?.id?.toString() == "${childAppId}" }
    if (!child) return [:]

    try {
        return [
            id           : child.id?.toString(),
            label        : child.getManagedRoomDeviceLabel(),
            hubitatRoomId: child.getHubitatRoomId(),
            hubitatRoom  : child.getHubitatRoomName(),
            roomName     : child.getConfiguredRoomName(),
            roomProfile  : child.getRoomProfile(),
            customLightingOnText : child.getCustomLightingOnText(),
            customLightingOffText: child.getCustomLightingOffText()
        ]
    } catch (Throwable ignored) {
        return [:]
    }
}

def roomStateChildRoomDevice(def childAppId) {
    def child = childApps?.find { it?.id?.toString() == "${childAppId}" }
    if (!child) return null

    try {
        return child.getManagedRoomDevice()
    } catch (Throwable ignored) {
        return null
    }
}

List neighborRoomDevicesForChildIds(def selectedChildIds) {
    List ids = normalizeIdList(selectedChildIds)
    if (!ids) return []

    try {
        List allChildren = childApps ?: []
        List matchedChildren = allChildren.findAll { child ->
            if (!ids.contains(child?.id?.toString())) return false
            try {
                return child.getRoomProfile() != "houseIntent"
            } catch (Throwable ignored) {
                return false
            }
        }
        List devices = matchedChildren.collect { child ->
                try {
                    child.getManagedRoomDevice()
                } catch (Throwable ignored) {
                    null
                }
            }.findAll { it != null }

        log.debug "Simple Home: neighbor child IDs=${ids.join(', ')}, available children=${allChildren.collect { it?.id }.join(', ') ?: 'none'}, matched children=${matchedChildren.collect { it?.id }.join(', ') ?: 'none'}, resolved devices=${devices*.displayName?.join(', ') ?: 'none'}"
        return devices
    } catch (Exception e) {
        log.warn "Simple Home: Could not resolve neighbor room devices: ${e.message}"
        return []
    }
}

private String childAppId(def child) {
    if (!child) return null

    return child.id?.toString()
}

private List normalizeIdList(def rawIds) {
    if (!rawIds) return []

    List ids = rawIds instanceof List ? rawIds : [rawIds]
    return ids
        .collectMany { raw ->
            String text = "${raw}".trim()
            if (text.startsWith("[") && text.endsWith("]")) {
                return text.substring(1, text.length() - 1).split(",").collect { it.trim() }
            }
            return [text]
        }
        .findAll { it }
        .unique()
}
