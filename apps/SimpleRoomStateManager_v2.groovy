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

#include lundby.SimpleHomeHelpers

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
            def modeManager = modeManagerChildApp()
            if (modeManager) {
                renderChildConfigureLink("configureModeManager", "Configure Mode Manager", modeManager, "Simple Mode Manager")
            } else {
                app(
                    name: "modeApps",
                    appName: "Simple Mode Manager v2",
                    namespace: "lundby",
                    title: "Add mode manager",
                    multiple: false
                )
            }
        }

        section("Lighting") {
            app(
                name: "lightingApps",
                appName: "Simple Room Lighting v2",
                namespace: "lundby",
                title: "Add room lighting",
                multiple: true
            )
        }

        section("Circadian lighting") {
            input "defaultCircadianReferenceBulb", "capability.colorTemperature", title: "House reference bulb", multiple: false, required: false
            input "useHouseIntentVirtualRoom", "bool", title: "Use House Intent Virtual Room", defaultValue: false, required: true
            input "createOpenMeteoReferenceNow", "button", title: "Create Open-Meteo reference device"
            paragraph openMeteoReferenceSummaryText()
            renderOpenMeteoReferenceLink()
            paragraph houseIntentSummaryText()
            renderHouseIntentRoomLink()
            renderHouseIntentLightingLink()
            def circadian = circadianLightingChildApp()
            if (circadian) {
                renderChildConfigureLink("configureCircadianLighting", "Configure Circadian Lighting", circadian, "Simple Circadian Lighting")
            } else {
                app(
                    name: "circadianApps",
                    appName: "Simple Circadian Lighting v2",
                    namespace: "lundby",
                    title: "Add circadian reference lighting",
                    multiple: false
                )
            }
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
    validateDefensiveAppCreation()
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
    if (buttonName == "createOpenMeteoReferenceNow") {
        createOpenMeteoReferenceDevice()
    }
}

String roomSummaryText() {
    List rows = roomStateChildApps().collect { child ->
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

String openMeteoReferenceSummaryText() {
    def dev = openMeteoReferenceDevice()
    if (dev) return "Open-Meteo reference device: ${dev.displayName}"
    return "Open-Meteo reference device: not created by Simple Home"
}

private void renderOpenMeteoReferenceLink() {
    def dev = openMeteoReferenceDevice()
    if (!dev) return

    href(
        name: "configureOpenMeteoReference",
        title: "Configure Open-Meteo Reference Device",
        description: dev.displayName ?: "Simple Open-Meteo Reference",
        url: deviceConfigureUrl(dev)
    )
}

private void renderHouseIntentRoomLink() {
    def child = houseIntentChildApp()
    if (!child) return

    renderChildConfigureLink("configureHouseIntentRoom", "Configure House Intent Room", child, "Room House Intent")
}

private void renderHouseIntentLightingLink() {
    def lighting = houseIntentLightingChildApp()
    if (!lighting) {
        paragraph "House Intent Lighting: not configured"
        return
    }

    renderChildConfigureLink("configureHouseIntentLighting", "Configure House Intent Lighting", lighting, "# House Intent Lighting")
}

private void renderChildConfigureLink(String name, String title, def child, String fallbackDescription) {
    href(
        name: name,
        title: title,
        description: child.label ?: fallbackDescription,
        url: childConfigureUrl(child)
    )
}

private String childConfigureUrl(def child) {
    return "/installedapp/configure/${child.id}"
}

private String deviceConfigureUrl(def dev) {
    return "/device/edit/${dev.id}"
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
    if (multipleHouseIntentLightingApps()) {
        log.error "Simple Home: More than one House Intent Lighting child exists. Reusing ${lighting?.label ?: lighting?.id}; delete extras manually."
    }

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
    def appWithReference = circadianLightingChildApps().find { child ->
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
    return houseIntentChildApps().find { child -> child }
}

private List houseIntentChildApps() {
    return roomStateChildApps().findAll { child ->
        try {
            if (child.getRoomProfile() == "houseIntent") return true
            if (child.getConfiguredRoomName() == "House Intent") return true
        } catch (Throwable ignored) {
            // Ignore non-room child apps.
        }
        return child?.label == "Room House Intent"
    }
}

private List roomStateChildApps() {
    List configured = childApps ?: []
    List discovered = []
    try {
        discovered = getChildApps()?.findAll { child ->
            try {
                return child?.getRoomStateAppName() == "Simple Room State"
            } catch (Throwable ignored) {
                String label = child?.label?.toString()
                return label == "Simple Room State" || label?.startsWith("Room ")
            }
        } ?: []
    } catch (Throwable ignored) {
        discovered = []
    }

    return uniqueChildApps(configured + discovered)
}

private def houseIntentLightingChildApp() {
    return houseIntentLightingChildApps().find { child -> child }
}

private List houseIntentLightingChildApps() {
    List configured = houseIntentLightingApps ?: []
    List discovered = []
    try {
        discovered = getChildApps()?.findAll { child ->
            try {
                return child?.getHouseIntentLightingAppName() == "Simple House Intent Lighting"
            } catch (Throwable ignored) {
                return child?.label == "# House Intent Lighting" || child?.label == "Simple House Intent Lighting"
            }
        } ?: []
    } catch (Throwable ignored) {
        discovered = []
    }

    return uniqueChildApps(configured + discovered)
}

private Boolean multipleHouseIntentLightingApps() {
    return houseIntentLightingChildApps().size() > 1
}

private List simpleRoomLightingChildApps() {
    List configured = lightingApps ?: []
    List discovered = []
    try {
        discovered = getChildApps()?.findAll { child ->
            try {
                if (child?.getHouseIntentLightingAppName() == "Simple House Intent Lighting") return false
            } catch (Throwable ignored) {
                // Continue probing for room lighting identity.
            }

            try {
                return child?.getRoomLightingAppName() == "Simple Room Lighting v2"
            } catch (Throwable ignored) {
                String label = child?.label?.toString()
                return label == "Simple Room Lighting" || (label?.endsWith(" Lighting") && label != "# House Intent Lighting")
            }
        } ?: []
    } catch (Throwable ignored) {
        discovered = []
    }

    return uniqueChildApps(configured + discovered)
}

private List modeManagerChildApps() {
    List configured = modeApps ?: []
    List discovered = []
    try {
        discovered = getChildApps()?.findAll { child ->
            try {
                return child?.getModeManagerAppName() == "Simple Mode Manager v2"
            } catch (Throwable ignored) {
                return child?.label == "Simple Mode Manager" || child?.label == "Simple Mode Manager v2"
            }
        } ?: []
    } catch (Throwable ignored) {
        discovered = []
    }

    return uniqueChildApps(configured + discovered)
}

private def modeManagerChildApp() {
    return modeManagerChildApps().find { child -> child }
}

private List circadianLightingChildApps() {
    List configured = circadianApps ?: []
    List discovered = []
    try {
        discovered = getChildApps()?.findAll { child ->
            try {
                return child?.getCircadianLightingAppName() == "Simple Circadian Lighting v2"
            } catch (Throwable ignored) {
                return child?.label == "Simple Circadian Lighting" || child?.label == "Simple Circadian Lighting v2"
            }
        } ?: []
    } catch (Throwable ignored) {
        discovered = []
    }

    return uniqueChildApps(configured + discovered)
}

private def circadianLightingChildApp() {
    return circadianLightingChildApps().find { child -> child }
}

private List uniqueChildApps(List apps) {
    Map byId = [:]
    apps.findAll { it }.each { child ->
        String id = child?.id?.toString()
        if (id && !byId.containsKey(id)) byId[id] = child
    }
    return byId.values() as List
}

private void validateDefensiveAppCreation() {
    List modes = modeManagerChildApps()
    if (modes.size() > 1) {
        log.error "Simple Home: More than one Mode Manager exists. Keep one and delete extras manually."
    }

    List circadianLighting = circadianLightingChildApps()
    if (circadianLighting.size() > 1) {
        log.error "Simple Home: More than one Circadian Lighting app exists. Keep one and delete extras manually."
    }

    List houseIntentRooms = houseIntentChildApps()
    if (houseIntentRooms.size() > 1) {
        log.error "Simple Home: More than one House Intent room exists. Reusing ${houseIntentRooms[0]?.label ?: houseIntentRooms[0]?.id}; delete extras manually."
    }

    List houseIntentLighting = houseIntentLightingChildApps()
    if (houseIntentLighting.size() > 1) {
        log.error "Simple Home: More than one House Intent Lighting app exists. Reusing ${houseIntentLighting[0]?.label ?: houseIntentLighting[0]?.id}; delete extras manually."
    }

    Map roomLightingByRoom = [:]
    simpleRoomLightingChildApps().each { lighting ->
        String roomId = null
        try {
            roomId = lighting.getConfiguredRoomChildAppId()?.toString()
        } catch (Throwable ignored) {
            roomId = null
        }
        if (!roomId) return

        List appsForRoom = roomLightingByRoom[roomId] ?: []
        appsForRoom << lighting
        roomLightingByRoom[roomId] = appsForRoom
    }

    roomLightingByRoom.each { roomId, appsForRoom ->
        if (appsForRoom.size() > 1) {
            log.error "Simple Home: More than one Room Lighting app targets room child ${roomId}. Keep one and delete extras manually."
        }
    }
}

Boolean modeManagerAllowed(def requestingChildAppId) {
    return primaryChildAllowed(modeManagerChildApps(), requestingChildAppId, "Mode Manager")
}

Boolean circadianLightingAllowed(def requestingChildAppId) {
    return primaryChildAllowed(circadianLightingChildApps(), requestingChildAppId, "Circadian Lighting")
}

Boolean houseIntentRoomAllowed(def requestingChildAppId) {
    return primaryChildAllowed(houseIntentChildApps(), requestingChildAppId, "House Intent room")
}

Boolean houseIntentLightingAllowed(def requestingChildAppId) {
    return primaryChildAllowed(houseIntentLightingChildApps(), requestingChildAppId, "House Intent Lighting")
}

Boolean roomLightingRoomAllowed(def roomChildAppId, def requestingChildAppId) {
    if (!roomChildAppId) return true

    List matches = simpleRoomLightingChildApps().findAll { lighting ->
        try {
            return lighting.getConfiguredRoomChildAppId()?.toString() == "${roomChildAppId}"
        } catch (Throwable ignored) {
            return false
        }
    }

    if (!matches) return true
    def primary = matches[0]
    Boolean allowed = primary?.id?.toString() == "${requestingChildAppId}"
    if (!allowed) {
        log.error "Simple Home: Refusing duplicate Room Lighting app ${requestingChildAppId} for room child ${roomChildAppId}. ${primary?.label ?: primary?.id} already owns that room."
    }
    return allowed
}

private Boolean primaryChildAllowed(List children, def requestingChildAppId, String childType) {
    if (!requestingChildAppId) return true
    if (!children) return true

    def primary = children[0]
    Boolean allowed = primary?.id?.toString() == "${requestingChildAppId}"
    if (!allowed) {
        log.error "Simple Home: Refusing duplicate ${childType} child ${requestingChildAppId}. ${primary?.label ?: primary?.id} is already the primary ${childType}."
    }
    return allowed
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

def openMeteoReferenceDevice() {
    return getChildDevice(openMeteoReferenceDeviceNetworkId())
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

private void createOpenMeteoReferenceDevice() {
    String dni = openMeteoReferenceDeviceNetworkId()
    String label = "Simple Open-Meteo Reference"
    def child = getChildDevice(dni)

    if (!child) {
        try {
            child = addChildDevice("lundby", "Simple Open-Meteo Outdoor Light Device", dni, [
                label      : label,
                name       : label,
                isComponent: false
            ])
            log.info "Simple Home: Created Open-Meteo reference device."
        } catch (Exception e) {
            log.warn "Simple Home: Could not create Open-Meteo reference device: ${e.message}"
            return
        }
    } else {
        log.info "Simple Home: Open-Meteo reference device already exists; leaving settings unchanged."
    }

    try {
        if (child.displayName != label) {
            child.setLabel(label)
        }
        child.initialize()
    } catch (Exception e) {
        log.warn "Simple Home: Could not initialize Open-Meteo reference device: ${e.message}"
    }

    if (!defaultCircadianReferenceBulb) {
        try {
            app.updateSetting("defaultCircadianReferenceBulb", [type: "capability.colorTemperature", value: child.id])
            log.info "Simple Home: House reference bulb set to ${child.displayName}."
        } catch (Exception e) {
            log.warn "Simple Home: Could not set Open-Meteo reference as House reference bulb: ${e.message}"
        }
    }
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

private String openMeteoReferenceDeviceNetworkId() {
    return "simple-home-open-meteo-reference-${app.id}"
}

private List allManagedChildren() {
    return uniqueChildApps(roomStateChildApps() + modeManagerChildApps() + simpleRoomLightingChildApps() + houseIntentLightingChildApps() + circadianLightingChildApps())
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
        Boolean requestingHouseIntentLighting = houseIntentLightingRequester(requestingChildAppId)
        Boolean requestingRoomLighting = roomLightingRequester(requestingChildAppId)
        List usedRoomLightingIds = roomLightingRoomChildIds(requestingChildAppId)

        roomStateChildApps().each { child ->
            String id = child?.id?.toString()
            if (id == "${requestingChildAppId}") return

            try {
                Boolean childIsHouseIntent = child.getRoomProfile() == "houseIntent"
                if (requestingHouseIntentLighting && !childIsHouseIntent) return
                if (!requestingHouseIntentLighting && childIsHouseIntent) return
                if (requestingRoomLighting && usedRoomLightingIds.contains(id)) return
                if (!includeHouseIntent && childIsHouseIntent) return
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

private Boolean houseIntentLightingRequester(def requestingChildAppId) {
    def requester = childAppById(requestingChildAppId)
    if (!requester) return false
    try {
        return requester.getHouseIntentLightingAppName() == "Simple House Intent Lighting"
    } catch (Throwable ignored) {
        return false
    }
}

private Boolean roomLightingRequester(def requestingChildAppId) {
    def requester = childAppById(requestingChildAppId)
    if (!requester) return false
    try {
        return requester.getRoomLightingAppName() == "Simple Room Lighting v2"
    } catch (Throwable ignored) {
        return false
    }
}

private List roomLightingRoomChildIds(def requestingChildAppId) {
    List ids = []
    simpleRoomLightingChildApps().each { lighting ->
        if (lighting?.id?.toString() == "${requestingChildAppId}") return
        try {
            String roomId = lighting.getConfiguredRoomChildAppId()?.toString()
            if (roomId) ids << roomId
        } catch (Throwable ignored) {
            // Ignore child apps that cannot report a room selection.
        }
    }
    return ids.unique()
}

private def childAppById(def childAppId) {
    if (!childAppId) return null
    try {
        return getChildApps()?.find { child -> child?.id?.toString() == "${childAppId}" }
    } catch (Throwable ignored) {
        return allManagedChildren().find { child -> child?.id?.toString() == "${childAppId}" }
    }
}

Map roomStateChildInfo(def childAppId) {
    def child = roomStateChildApps().find { it?.id?.toString() == "${childAppId}" }
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
    def child = roomStateChildApps().find { it?.id?.toString() == "${childAppId}" }
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
        List allChildren = roomStateChildApps()
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
