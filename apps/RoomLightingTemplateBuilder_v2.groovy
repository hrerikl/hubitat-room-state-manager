import groovy.json.JsonSlurper
import groovy.json.JsonOutput

/**
 * Room Lighting Template Builder v2 - Child App
 *
 * Purpose:
 * - Generate Room Lighting app import text for one selected Room.
 * - Uses embedded On and Courtesy templates.
 * - Replaces the template Room meta-device with the selected Room meta-device.
 * - Replaces the template controlled-light matrix with all selected lights.
 *
 * Intended workflow:
 * 1. Select Hubitat Room / Room meta-device.
 * 2. Select all lights in that room. Use the helper button if it works on your hub.
 * 3. Press Generate.
 * 4. Copy generated import text from the output pages into Hubitat app import.
 * 5. Customize levels/modes in Room Lighting after import.
 */

definition(
    name: "Room Lighting Template Builder v2",
    namespace: "lundby",
    author: "Erik Lundby / ChatGPT",
    description: "Generates Room Lighting import files from standard On/Courtesy templates.",
    category: "Convenience",
    parent: "lundby:Simple Room State Manager v2",
    singleInstance: false,
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage", title: "Room Lighting Template Builder v2", install: true, uninstall: true) {
        section("Target Room") {
            input "roomChildAppId", "enum", title: "Room State room", options: roomStateChildOptions(), required: true, submitOnChange: true
            input "roomNameOverride", "text", title: "Room name override, optional", required: false
            paragraph targetRoomSummary()
        }

        section("Lights") {
            input "roomDevices", "capability.*", title: "Room devices", multiple: true, required: false
            paragraph "Select devices assigned to this Hubitat Room. Only devices with switch, dimmer, color, or color-temperature lighting capability are included in generated templates."
            input "loadLightsFromRoomNow", "button", title: "Try loading lights from selected room"
            input "filterSelectedDevicesToRoomNow", "button", title: "Filter selected devices to selected room"
        }

        section("Generate") {
            input "generateNow", "button", title: "Generate Room Lighting imports"
            href "onOutputPage", title: "View generated On Lighting import", description: state.generatedOnLabel ?: "Generate first"
            href "courtesyOutputPage", title: "View generated Courtesy Lighting import", description: state.generatedCourtesyLabel ?: "Generate first"
        }

        section("Debug") {
            input "debugLogging", "bool", title: "Enable debug logging", defaultValue: true, required: true
        }
    }

    page(name: "onOutputPage", title: "Generated On Lighting Import", install: false, uninstall: false) {
        section("Copy this text") {
            paragraph state.generatedOn ?: "No generated On import yet."
        }
    }

    page(name: "courtesyOutputPage", title: "Generated Courtesy Lighting Import", install: false, uninstall: false) {
        section("Copy this text") {
            paragraph state.generatedCourtesy ?: "No generated Courtesy import yet."
        }
    }
}

// -------------------- Embedded Templates --------------------

private String onTemplateText() {
    return '''{"deviceReplacements":{"139":{"deviceName":"Room Utillity Room","deviceLabel":"Room Utillity Room","deviceTypeName":"Simple Room Meta Device","deviceTypeNamespace":"lundby"},"102":{"deviceName":"Utillity Room Lights on Lundby Home","deviceLabel":null,"deviceTypeName":"Qubino Flush Dimmer","deviceTypeNamespace":"hubitat"}},"appReplacements":{"72":{"appTypeName":"Room Lights","appTypeNamespace":"hubitat","appType":"sys","appName":"Room Lights","appLabel":"Utillity Room Lights","parentAppInstalledAppId":"71","parentAppTypeName":"Room Lighting","parentAppTypeNamespace":"hubitat","parentAppName":"Room Lighting","parentAppLabel":"Room Lighting","childApps":{},"singleInstance":false}},"appData":{"72":{"state":{"buttonTable":{},"modes":[{"4":"Away"},{"1":"Day"},{"2":"Evening"},{"3":"Night"},{"33":"Vacation"}],"fixVariableDisable":true,"clonedName":"Utillity Room Lights","captured1":true,"virginOff":true,"priorOffMeans":["custom attribute"],"capDevs1.1":{"102":{"doOff":true,"doAct":true,"isSet":true,"swVal":"on","dimVal":25,"CM":"Dimmer","useVarD":false,"force":null}},"priorOnMeans":["custom attribute"],"priorSchedType":"Hub Modes","captured3":true,"attrType":"ENUM","captured2":true,"mode":"1","dayGroups":{"1":[true,true,true,true,true,true,true]},"virginOn":true,"allVarsI":[],"appLabel":"Utillity Room Lights","isOn":{"102":false},"randHue":-1,"priorUseModes":true,"capDevs1":{"102":{"doOff":true,"doAct":true,"isSet":false,"swVal":"on","dimVal":100,"CM":"Dimmer","useVarD":false,"force":null}},"priorDevs":["102"],"allVarsB":[],"capDevs3":{"102":{"doOff":true,"doAct":true,"isSet":true,"swVal":"on","dimVal":40,"CM":"Dimmer","useVarD":false,"force":null}},"capDevs2":{"102":{"doOff":true,"doAct":true,"isSet":true,"swVal":"on","dimVal":60,"CM":"Dimmer","useVarD":false,"force":null}},"priorActive":false,"modeNames":{"33":"Vacation","C":"Pre-Capture","-1":"Preset Off Day","-2":"Preset Off Evening","-3":"Preset Off Night","-4":"Preset Off Away","-33":"Preset Off Vacation","0":"All Modes","P":"Preset Off","1":"Day","2":"Evening","3":"Night","T":"Transition","4":"Away"},"updateForTimes":true,"previousModes":[1,2,3,4,33],"active":false,"notEqual":true,"captured1.1":true,"started":true,"offStarted":true,"alexaName":null,"allVarsS":[],"allVarsT":[],"firstName":true,"prevState":{},"attrTypeOff":"ENUM","onStarted":true,"modeIds":{"Away":"4","Vacation":"33","Evening":"2","Night":"3","Day":"1"},"dayGroupNdx":1,"previousModeNames":["Day","Evening","Night","Away","Vacation"]},"appSettings":[{"deviceList":null,"multiple":false,"name":"indicator","type":"enum","value":"scene"},{"deviceList":null,"multiple":false,"name":"cancel","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"on~102~1.1","type":"button","value":""},{"deviceList":null,"multiple":true,"name":"modes","type":"enum","value":"[\\"1\\",\\"2\\",\\"3\\"]"},{"deviceList":null,"multiple":false,"name":"dimLAUseVar","type":"bool","value":"false"},{"deviceList":null,"multiple":false,"name":"xo~102~2","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"xo~102~3","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"capture.3","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"capture.2","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"capture.1","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"capture.0","type":"button","value":""},{"deviceList":{"139":"Room Utillity Room"},"multiple":true,"name":"attrDevice","type":"capability.*","value":null},{"deviceList":null,"multiple":false,"name":"sw~102~1.1","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"schedTypeL","type":"enum","value":"Hub Modes"},{"deviceList":null,"multiple":false,"name":"switchPOD","type":"bool","value":"false"},{"deviceList":null,"multiple":true,"name":"onConds","type":"enum","value":null},{"deviceList":null,"multiple":false,"name":"motionTime","type":"decimal","value":"1"},{"deviceList":{"139":"Room Utillity Room"},"multiple":true,"name":"attrDeviceOff","type":"capability.*","value":null},{"deviceList":null,"multiple":false,"name":"capture.1.1","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"dm~102~1.1","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"delayOn","type":"number","value":""},{"deviceList":null,"multiple":false,"name":"alexaName","type":"text","value":""},{"deviceList":null,"multiple":false,"name":"pause","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"doTurnOff","type":"bool","value":"true"},{"deviceList":null,"multiple":false,"name":"xo~102~0","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"customAttr","type":"enum","value":"lightingIntent"},{"deviceList":null,"multiple":false,"name":"attrStateOff","type":"enum","value":"Off"},{"deviceList":null,"multiple":true,"name":"offConds","type":"enum","value":null},{"deviceList":null,"multiple":false,"name":"origLabel","type":"text","value":"Utillity Room Lights"},{"deviceList":null,"multiple":false,"name":"sw~102~0","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"xo~102~1","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"sw~102~1","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"sw~102~2","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"sw~102~3","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"debugLogging","type":"bool","value":""},{"deviceList":null,"multiple":false,"name":"activate","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"alterTurnOff","type":"enum","value":""},{"deviceList":null,"multiple":false,"name":"logging","type":"bool","value":"true"},{"deviceList":null,"multiple":false,"name":"attrState","type":"enum","value":"On"},{"deviceList":null,"multiple":false,"name":"turnOff","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"switchPE","type":"bool","value":"false"},{"deviceList":null,"multiple":false,"name":"xo~102~1.1","type":"button","value":""},{"deviceList":null,"multiple":true,"name":"roomNames","type":"enum","value":"[\\"Utillity Room\\"]"},{"deviceList":null,"multiple":true,"name":"offMeans","type":"enum","value":"[\\"custom attribute\\"]"},{"deviceList":null,"multiple":false,"name":"update","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"useModes","type":"bool","value":"true"},{"deviceList":null,"multiple":false,"name":"delayOff","type":"number","value":""},{"deviceList":null,"multiple":false,"name":"sx~102~1.1","type":"button","value":""},{"deviceList":null,"multiple":true,"name":"onEnable","type":"enum","value":null},{"deviceList":null,"multiple":true,"name":"onDisable","type":"enum","value":null},{"deviceList":{"102":"Utillity Room Lights on Lundby Home"},"multiple":true,"name":"switchesE","type":"capability.switch","value":null},{"deviceList":null,"multiple":false,"name":"sx~102~2","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"sx~102~3","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"sx~102~0","type":"button","value":""},{"deviceList":null,"multiple":true,"name":"otherOnOpts","type":"enum","value":"[\\"doTurnOn\\"]"},{"deviceList":null,"multiple":false,"name":"on~102~3","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"fade","type":"number","value":"3"},{"deviceList":null,"multiple":true,"name":"offDisable","type":"enum","value":null},{"deviceList":null,"multiple":false,"name":"sx~102~1","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"on~102~2","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"cx~102~3","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"cx~102~2","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"cx~102~1","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"cx~102~0","type":"button","value":""},{"deviceList":null,"multiple":true,"name":"offLightsL","type":"capability.switch","value":null},{"deviceList":null,"multiple":true,"name":"onMeans","type":"enum","value":"[\\"custom attribute\\"]"},{"deviceList":null,"multiple":false,"name":"on~102~1","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"on~102~0","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"switchOffP","type":"bool","value":""},{"deviceList":{"102":"Utillity Room Lights on Lundby Home"},"multiple":true,"name":"switchesOD","type":"capability.switch","value":null},{"deviceList":null,"multiple":false,"name":"cx~102~1.1","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"switchP","type":"bool","value":""},{"deviceList":null,"multiple":false,"name":"customAttrOff","type":"enum","value":"lightingIntent"},{"deviceList":null,"multiple":true,"name":"room","type":"enum","value":"[\\"2\\"]"},{"deviceList":null,"multiple":false,"name":"preventMotionOff","type":"bool","value":""},{"deviceList":null,"multiple":false,"name":"dm~102~0","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"dm~102~1","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"dm~102~2","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"dm~102~3","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"createCapture","type":"enum","value":""},{"deviceList":null,"multiple":false,"name":"dimLA","type":"number","value":""},{"deviceList":{"102":"Utillity Room Lights on Lundby Home"},"multiple":true,"name":"roomDevsL","type":"capability.switch,capability.pushableButton,capability.windowShade,capability.windowBlind","value":null}],"subscriptions":[{"handler":"actHandler","name":"lightingIntent.On","type":"DEVICE","typeId":139,"typeName":"Room Utillity Room","filter":"true"},{"handler":"offHandler","name":"lightingIntent.Off","type":"DEVICE","typeId":139,"typeName":"Room Utillity Room","filter":"true"},{"handler":"evtHandler","name":"switch","type":"DEVICE","typeId":102,"typeName":"Utillity Room Lights on Lundby Home","filter":"true"},{"handler":"checkSceneEvt","name":"level","type":"DEVICE","typeId":102,"typeName":"Utillity Room Lights on Lundby Home","filter":"true"},{"handler":"modeHandler","name":"mode","type":"LOCATION","typeId":1,"typeName":"Lundby Coordinator","filter":"false"}]}}}'''
}

private String courtesyTemplateText() {
    return '''{"deviceReplacements":{"139":{"deviceName":"Room Utillity Room","deviceLabel":"Room Utillity Room","deviceTypeName":"Simple Room Meta Device","deviceTypeNamespace":"lundby"},"102":{"deviceName":"Utillity Room Lights on Lundby Home","deviceLabel":null,"deviceTypeName":"Qubino Flush Dimmer","deviceTypeNamespace":"hubitat"}},"appReplacements":{"75":{"appTypeName":"Room Lights","appTypeNamespace":"hubitat","appType":"sys","appName":"Room Lights","appLabel":"Utillity Room Courtesy Lights ","parentAppInstalledAppId":"71","parentAppTypeName":"Room Lighting","parentAppTypeNamespace":"hubitat","parentAppName":"Room Lighting","parentAppLabel":"Room Lighting","childApps":{},"singleInstance":false}},"appData":{"75":{"state":{"buttonTable":{},"modes":[{"4":"Away"},{"1":"Day"},{"2":"Evening"},{"3":"Night"},{"33":"Vacation"}],"fixVariableDisable":true,"clonedName":"Utillity Room Courtesy Lights ","virginOff":true,"captured0":true,"priorOffMeans":["custom attribute"],"priorOnMeans":["custom attribute"],"priorSchedType":"No Periods","attrType":"ENUM","mode":"0","dayGroups":{"1":[true,true,true,true,true,true,true]},"virginOn":true,"allVarsI":[],"appLabel":"Utillity Room Courtesy Lights ","isOn":{"102":false},"randHue":-1,"priorUseModes":false,"allVarsB":[],"priorDevs":["102"],"capDevs0":{"102":{"doOff":true,"doAct":true,"isSet":false,"swVal":"on","dimVal":20,"CM":"Dimmer","useVarD":false,"force":null}},"modeNames":{"33":"Vacation","C":"Pre-Capture","-1":"Preset Off Day","-2":"Preset Off Evening","-3":"Preset Off Night","-4":"Preset Off Away","-33":"Preset Off Vacation","0":"All Modes","P":"Preset Off","1":"Day","2":"Evening","3":"Night","T":"Transition","4":"Away"},"previousModes":[1,2,3,4,33],"active":false,"notEqual":true,"started":true,"offStarted":true,"alexaName":null,"allVarsS":[],"allVarsT":[],"firstName":true,"prevState":{},"attrTypeOff":"ENUM","onStarted":true,"modeIds":{"Away":"4","Vacation":"33","Evening":"2","Night":"3","Day":"1"},"dayGroupNdx":1,"previousModeNames":["Day","Evening","Night","Away","Vacation"]},"appSettings":[{"deviceList":null,"multiple":false,"name":"cancel","type":"button","value":""},{"deviceList":null,"multiple":true,"name":"modes","type":"enum","value":"[\\"0\\"]"},{"deviceList":null,"multiple":false,"name":"dimLAUseVar","type":"bool","value":"false"},{"deviceList":null,"multiple":false,"name":"capture.0","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"schedTypeL","type":"enum","value":"No Periods"},{"deviceList":null,"multiple":false,"name":"switchPOD","type":"bool","value":""},{"deviceList":null,"multiple":true,"name":"onConds","type":"enum","value":null},{"deviceList":null,"multiple":false,"name":"motionTime","type":"decimal","value":"1"},{"deviceList":null,"multiple":true,"name":"switchesOEO","type":"capability.switch","value":null},{"deviceList":null,"multiple":false,"name":"delayOn","type":"number","value":""},{"deviceList":null,"multiple":false,"name":"alexaName","type":"text","value":""},{"deviceList":null,"multiple":false,"name":"pause","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"xo~102~0","type":"button","value":""},{"deviceList":null,"multiple":true,"name":"offConds","type":"enum","value":null},{"deviceList":null,"multiple":false,"name":"customAttr","type":"enum","value":"lightingIntent"},{"deviceList":null,"multiple":false,"name":"attrStateOff","type":"enum","value":"Off"},{"deviceList":null,"multiple":false,"name":"origLabel","type":"text","value":"Utillity Room Courtesy Lights "},{"deviceList":null,"multiple":false,"name":"sw~102~0","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"debugLogging","type":"bool","value":""},{"deviceList":null,"multiple":false,"name":"activate","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"alterTurnOff","type":"enum","value":""},{"deviceList":null,"multiple":false,"name":"logging","type":"bool","value":"true"},{"deviceList":null,"multiple":true,"name":"switchesDO","type":"capability.switch","value":null},{"deviceList":null,"multiple":false,"name":"attrState","type":"enum","value":"Courtesy"},{"deviceList":null,"multiple":false,"name":"turnOff","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"switchPE","type":"bool","value":""},{"deviceList":null,"multiple":true,"name":"roomNames","type":"enum","value":"[\\"Utillity Room\\"]"},{"deviceList":null,"multiple":true,"name":"offMeans","type":"enum","value":"[\\"custom attribute\\"]"},{"deviceList":null,"multiple":false,"name":"update","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"useModes","type":"bool","value":"false"},{"deviceList":null,"multiple":false,"name":"delayOff","type":"number","value":""},{"deviceList":null,"multiple":false,"name":"switchPDO","type":"bool","value":""},{"deviceList":null,"multiple":true,"name":"onEnable","type":"enum","value":null},{"deviceList":null,"multiple":true,"name":"offEnable","type":"enum","value":null},{"deviceList":null,"multiple":true,"name":"onDisable","type":"enum","value":null},{"deviceList":null,"multiple":true,"name":"switchesE","type":"capability.switch","value":null},{"deviceList":null,"multiple":false,"name":"sx~102~0","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"fade","type":"number","value":"3"},{"deviceList":null,"multiple":true,"name":"offDisable","type":"enum","value":null},{"deviceList":null,"multiple":false,"name":"cx~102~0","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"switchPOEO","type":"bool","value":""},{"deviceList":null,"multiple":true,"name":"onMeans","type":"enum","value":"[\\"custom attribute\\"]"},{"deviceList":{"139":"Room Utillity Room"},"multiple":false,"name":"attrDeviceDO","type":"capability.*","value":null},{"deviceList":null,"multiple":false,"name":"on~102~0","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"switchOffP","type":"bool","value":""},{"deviceList":null,"multiple":true,"name":"switchesOD","type":"capability.switch","value":null},{"deviceList":null,"multiple":false,"name":"switchP","type":"bool","value":""},{"deviceList":null,"multiple":false,"name":"customAttrOff","type":"enum","value":"lightingIntent"},{"deviceList":null,"multiple":false,"name":"customAttrDO","type":"enum","value":"lightingIntent"},{"deviceList":null,"multiple":true,"name":"room","type":"enum","value":"[\\"2\\"]"},{"deviceList":null,"multiple":false,"name":"attrStateDO","type":"enum","value":"Off"},{"deviceList":null,"multiple":false,"name":"preventMotionOff","type":"bool","value":""},{"deviceList":null,"multiple":false,"name":"dm~102~0","type":"button","value":""},{"deviceList":null,"multiple":false,"name":"createCapture","type":"enum","value":""},{"deviceList":null,"multiple":false,"name":"dimLA","type":"number","value":""},{"deviceList":null,"multiple":true,"name":"otherOnOpts","type":"enum","value":"[\\"doTurnOn\\"]"},{"deviceList":null,"multiple":false,"name":"indicator","type":"enum","value":"scene"},{"deviceList":{"139":"Room Utillity Room"},"multiple":true,"name":"attrDevice","type":"capability.*","value":null},{"deviceList":{},"multiple":true,"name":"offLightsL","type":"capability.switch","value":null},{"deviceList":null,"multiple":false,"name":"doTurnOff","type":"bool","value":"true"},{"deviceList":{"139":"Room Utillity Room"},"multiple":true,"name":"attrDeviceOff","type":"capability.*","value":null},{"deviceList":{"102":"Utillity Room Lights on Lundby Home"},"multiple":true,"name":"roomDevsL","type":"capability.switch,capability.pushableButton,capability.windowShade,capability.windowBlind","value":null}],"subscriptions":[{"handler":"actHandler","name":"lightingIntent.Courtesy","type":"DEVICE","typeId":139,"typeName":"Room Utillity Room","filter":"true"},{"handler":"offHandler","name":"lightingIntent.Off","type":"DEVICE","typeId":139,"typeName":"Room Utillity Room","filter":"true"},{"handler":"evtHandler","name":"switch","type":"DEVICE","typeId":102,"typeName":"Utillity Room Lights on Lundby Home","filter":"true"},{"handler":"checkSceneEvt","name":"level","type":"DEVICE","typeId":102,"typeName":"Utillity Room Lights on Lundby Home","filter":"true"}]}}}'''
}

// -------------------- Buttons --------------------

def appButtonHandler(btn) {
    if (btn == "generateNow") generateImports()
    if (btn == "loadLightsFromRoomNow") loadLightsFromRoom()
    if (btn == "filterSelectedDevicesToRoomNow") filterSelectedDevicesToRoom()
}

// -------------------- Lifecycle --------------------

def installed() {
    log.info "Installed ${app.label}"
}

def updated() {
    log.info "Updated ${app.label}"
}

// -------------------- Hubitat Room Helpers --------------------

private Map roomStateChildOptions() {
    try {
        if (!parent) return [:]
        return parent.roomStateChildOptions(app.id) ?: [:]
    } catch (Exception e) {
        log.warn "${app.label}: Could not load Room State room list: ${e.message}"
        return [:]
    }
}

private Map selectedRoomChildInfo() {
    if (!roomChildAppId) return [:]

    try {
        return parent.roomStateChildInfo(roomChildAppId) ?: [:]
    } catch (Exception e) {
        log.warn "Could not look up Room State child ${roomChildAppId}: ${e.message}"
        return [:]
    }
}

private def selectedRoomDevice() {
    if (!roomChildAppId) return null

    try {
        return parent.roomStateChildRoomDevice(roomChildAppId)
    } catch (Exception e) {
        log.warn "Could not look up Room meta-device for child ${roomChildAppId}: ${e.message}"
        return null
    }
}

private def selectedHubitatRoom() {
    String selectedRoomId = selectedRoomChildInfo().hubitatRoomId?.toString()
    if (!selectedRoomId) return null

    try { return getRooms()?.find { "${it.id}" == selectedRoomId } }
    catch (Exception e) { log.warn "Could not look up Hubitat Room ${selectedRoomId}: ${e.message}"; return null }
}

private String targetRoomSummary() {
    Map info = selectedRoomChildInfo()
    if (!info) return "Select a Room State room."

    String roomName = info.hubitatRoom ?: "No Hubitat Room selected in Room State child"
    String deviceLabel = info.label ?: "No Room meta-device found"
    return "Hubitat Room: ${roomName}. Room meta-device: ${deviceLabel}."
}

private String safeRoomName() {
    String name = roomNameOverride?.trim()
    if (!name) name = selectedRoomChildInfo().roomName ?: selectedRoomDevice()?.displayName ?: selectedHubitatRoom()?.name ?: "Room"
    name = name.trim()
    if (name.toLowerCase().startsWith("room ")) name = name.substring(5).trim()
    return name ?: "Room"
}

private void loadLightsFromRoom() {
    def room = selectedHubitatRoom()
    if (!room) { log.warn "No Hubitat Room selected."; return }

    // Hubitat's getRooms() behavior varies. Some versions expose device IDs, some expose richer objects.
    // We attempt best-effort discovery, but manual selection remains the reliable fallback.
    try {
        List ids = roomSwitchDeviceIds(room)
        ids = ids.unique()
        if (!ids) { log.warn "Room ${room.name} did not expose device IDs. Select lights manually."; return }

        app.updateSetting("roomDevices", [type: "capability.*", value: ids])
        log.info "Loaded ${ids.size()} lighting-capable room device(s) from Hubitat Room ${room.name}: ${ids}"
    } catch (Exception e) {
        log.warn "Could not load lights from Hubitat Room: ${e.message}"
    }
}

private void filterSelectedDevicesToRoom() {
    def room = selectedHubitatRoom()
    if (!room) { log.warn "No Hubitat Room selected."; return }

    List roomIds = hubitatRoomDeviceIds(room)
    if (!roomIds) { log.warn "Room ${room.name} did not expose device IDs. Cannot filter selected devices."; return }

    List selectedIds = selectedRoomDevices()
        .collect { "${it.id}" }
        .findAll { id -> roomIds.contains(id) }
        .unique()

    app.updateSetting("roomDevices", [type: "capability.*", value: selectedIds])
    log.info "Filtered selected devices to ${selectedIds.size()} device(s) assigned to Hubitat Room ${room.name}: ${selectedIds}"
}

private List roomSwitchDeviceIds(def room) {
    List ids = []

    if (room.devices instanceof Collection) {
        ids.addAll(room.devices.findAll { dev ->
            isRoomLightingCandidate(dev)
        }.collect { "${it.id}" })
    }

    if (!ids && room.deviceIds instanceof Collection) {
        ids.addAll(room.deviceIds.collect { id ->
            deviceById(id)
        }.findAll { dev ->
            isRoomLightingCandidate(dev)
        }.collect { dev ->
            "${dev.id}"
        })
    }

    return ids
        .findAll { id -> id && id != "${selectedRoomDevice()?.id}" }
        .unique()
}

private List hubitatRoomDeviceIds(def room) {
    List ids = []
    if (room.devices instanceof Collection) {
        ids.addAll(room.devices.collect { "${it.id}" })
    }
    if (room.deviceIds instanceof Collection) {
        ids.addAll(room.deviceIds.collect { "${it}" })
    }
    return ids.findAll { it }.unique()
}

private def deviceById(def id) {
    String deviceId = "${id}"

    try {
        app.updateSetting("_deviceProbe", [type: "capability.*", value: deviceId])
        def probedDevice = settings["_deviceProbe"]
        if (probedDevice?.hasProperty("id") && "${probedDevice.id}" == deviceId) {
            return probedDevice
        }
    } catch (Throwable ignored) {
        // Broad capability probe is best-effort and may vary by hub version.
    }

    try {
        return getDeviceById(deviceId)
    } catch (Throwable ignored) {
        // Not available on all Hubitat app runtimes.
    }

    try {
        return location.getDevice(deviceId as Long)
    } catch (Throwable ignored) {
        // Not available or not authorized on all hubs.
    }

    try {
        return location.getDevice(deviceId)
    } catch (Throwable ignored) {
        // Not available or not authorized on all hubs.
    }

    debug "Could not resolve Hubitat Room device id ${deviceId}; excluding it from generated lights"
    return null
}

private Boolean isRoomLightingCandidate(def dev) {
    try {
        if ("${dev.id}" == "${selectedRoomDevice()?.id}") return false
        if (hasAnyCapability(dev, ["SwitchLevel", "ColorTemperature", "ColorControl"])) return true
        if (dev?.hasCapability("Switch") && looksLikeLight(dev)) return true
        return false
    } catch (Exception ignored) {
        return false
    }
}

private Boolean hasAnyCapability(def dev, List<String> caps) {
    return caps.any { cap -> hasCap(dev, cap) }
}

private Boolean looksLikeLight(def dev) {
    String text = [
        dev?.displayName,
        dev?.name,
        safeTypeName(dev)
    ].findAll { it }.join(" ").toLowerCase()

    return ["light", "lights", "lamp", "bulb", "dimmer", "switch", "hue"].any { token ->
        text.contains(token)
    }
}

// -------------------- Generation --------------------

private void generateImports() {
    def roomDevice = selectedRoomDevice()
    if (!roomDevice) { log.warn "Select a Room State room with a Room meta-device first."; return }
    if (!selectedLights()) { log.warn "Select Room devices that include at least one lighting-capable device first."; return }

    Map onMap = parseTemplate(onTemplateText())
    Map courtesyMap = parseTemplate(courtesyTemplateText())

    Map generatedOn = generateFromTemplate(onMap, "On")
    Map generatedCourtesy = generateFromTemplate(courtesyMap, "Courtesy")

    state.generatedOnLabel = "${safeRoomName()} Lights"
    state.generatedCourtesyLabel = "${safeRoomName()} Courtesy Lights"
    state.generatedOn = JsonOutput.prettyPrint(JsonOutput.toJson(generatedOn))
    state.generatedCourtesy = JsonOutput.prettyPrint(JsonOutput.toJson(generatedCourtesy))

    log.info "Generated Room Lighting imports for ${safeRoomName()}"
}

private Map parseTemplate(String txt) {
    return new JsonSlurper().parseText(txt) as Map
}

private Map generateFromTemplate(Map template, String intent) {
    Map out = deepCopy(template)
    def roomDevice = selectedRoomDevice()
    String roomName = safeRoomName()
    String appLabel = intent == "Courtesy" ? "${roomName} Courtesy Lights" : "${roomName} Lights"
    String roomId = "${roomDevice.id}"

    Map old = findTemplateIds(out)
    String oldRoomId = old.roomId
    List<String> oldLightIds = old.lightIds
    String firstOldLightId = oldLightIds ? oldLightIds[0] : null

    Map appDataEntry = firstAppData(out)
    Map stateMap = appDataEntry.state as Map
    List settings = appDataEntry.appSettings as List
    List subscriptions = appDataEntry.subscriptions as List

    // Device replacements
    out.deviceReplacements = buildDeviceReplacements(roomId)

    // App label replacements
    out.appReplacements?.each { id, appRep ->
        appRep.appLabel = appLabel
    }
    stateMap.clonedName = appLabel
    stateMap.appLabel = appLabel

    // Attribute condition values
    stateMap.attrType = "ENUM"
    stateMap.attrTypeOff = "ENUM"
    setSetting(settings, "customAttr", "lightingIntent")
    setSetting(settings, "customAttrOff", "lightingIntent")
    setSetting(settings, "attrState", intent)
    setSetting(settings, "attrStateOff", "Off")
    setSetting(settings, "origLabel", appLabel)
    setSetting(settings, "roomNames", [roomName])
    if (selectedRoomChildInfo().hubitatRoomId) {
        setSetting(settings, "room", [selectedRoomChildInfo().hubitatRoomId.toString()])
    }
    setDeviceList(settings, "attrDevice", [(roomId): roomDevice.displayName])
    setDeviceList(settings, "attrDeviceOff", [(roomId): roomDevice.displayName])
    setDeviceList(settings, "attrDeviceDO", [(roomId): roomDevice.displayName])

    // Rebuild lighting matrix keys based on template period keys.
    List<String> capKeys = stateMap.keySet().findAll { it.startsWith("capDevs") } as List<String>
    capKeys.each { key ->
        stateMap[key] = buildCapDevsForKey(key, stateMap[key] as Map, firstOldLightId)
    }

    stateMap.priorDevs = controlledLightIdList()
    stateMap.isOn = controlledLightIdList().collectEntries { [(it): false] }
    removeButtonSettingsForOldLights(settings, oldLightIds)
    addButtonSettingsForNewLights(settings, capKeys)
    replaceLightDeviceSettings(settings)
    rebuildSubscriptions(appDataEntry, roomId, intent)

    return out
}

private Map findTemplateIds(Map data) {
    Map devs = data.deviceReplacements as Map
    String roomId = null
    List lightIds = []
    devs.each { id, d ->
        if (d.deviceTypeName == "Simple Room Meta Device" && d.deviceTypeNamespace == "lundby") roomId = "${id}"
        else lightIds << "${id}"
    }
    return [roomId: roomId, lightIds: lightIds]
}

private Map firstAppData(Map data) {
    return (data.appData as Map).values().first() as Map
}

private Map buildDeviceReplacements(String roomId) {
    def roomDevice = selectedRoomDevice()
    Map reps = [:]
    reps[roomId] = [
        deviceName: roomDevice.displayName ?: roomDevice.name,
        deviceLabel: roomDevice.displayName,
        deviceTypeName: "Simple Room Meta Device",
        deviceTypeNamespace: "lundby"
    ]
    selectedLights().each { dev ->
        reps["${dev.id}"] = [
            deviceName: dev.displayName ?: dev.name,
            deviceLabel: dev.displayName,
            deviceTypeName: safeTypeName(dev),
            deviceTypeNamespace: safeTypeNamespace(dev)
        ]
    }
    return reps
}

private List selectedLights() {
    return selectedRoomDevices().findAll { dev -> isRoomLightingCandidate(dev) }
}

private List selectedRoomDevices() {
    return roomDevices instanceof List ? roomDevices : (roomDevices ? [roomDevices] : [])
}

private List<String> controlledLightIdList() {
    return selectedLights().collect { "${it.id}" }
}

private Map buildCapDevsForKey(String key, Map templateCapMap, String oldLightId) {
    Map result = [:]
    Map proto = oldLightId && templateCapMap ? (templateCapMap[oldLightId] as Map) : [:]

    selectedLights().each { dev ->
        result["${dev.id}"] = capEntryForDevice(dev, proto)
    }
    return result
}

private Map capEntryForDevice(dev, Map proto) {
    Boolean ct = hasCap(dev, "ColorTemperature")
    Boolean dim = hasCap(dev, "SwitchLevel")

    if (ct) {
        return [
            doOff: true,
            satVal: proto?.satVal ?: 100,
            doAct: true,
            isSet: true,
            swVal: "on",
            dimVal: proto?.dimVal ?: 100,
            CM: "CT",
            useVarD: false,
            useVarT: false,
            force: null,
            tempVal: proto?.tempVal ?: 2732,
            hueVal: proto?.hueVal ?: 0
        ]
    }

    if (dim) {
        return [
            doOff: true,
            doAct: true,
            isSet: true,
            swVal: "on",
            dimVal: proto?.dimVal ?: 100,
            CM: "Dimmer",
            useVarD: false,
            force: null
        ]
    }

    return [
        doOff: true,
        doAct: true,
        isSet: false,
        swVal: "on",
        CM: "Switch",
        useVarD: false,
        force: null
    ]
}

private Boolean hasCap(dev, String capName) {
    try { return dev.hasCapability(capName) }
    catch (Exception ignored) { return false }
}

private void replaceLightDeviceSettings(List settings) {
    Map lightMap = selectedLights().collectEntries { [("${it.id}"): it.displayName] }
    setDeviceList(settings, "roomDevsL", lightMap)

    ["switchesE", "switchesOD", "switchesOEO", "switchesDO"].each { name ->
        def setting = settings.find { it.name == name }
        if (setting?.deviceList != null) {
            setting.deviceList = lightMap
        }
    }
}

private void removeButtonSettingsForOldLights(List settings, List<String> oldLightIds) {
    settings.removeAll { s ->
        String n = s.name ?: ""
        oldLightIds.any { id -> n.contains("~${id}~") || n.endsWith(".${id}") || n.contains(".${id}.") }
    }
}

private void addButtonSettingsForNewLights(List settings, List<String> capKeys) {
    Set periods = [] as Set
    capKeys.each { key -> periods << key.replace("capDevs", "") }
    controlledLightIdList().each { id ->
        periods.each { p ->
            ["on", "sw", "dm", "sx", "xo", "cx", "ct"].each { prefix ->
                settings << [deviceList: null, multiple: false, name: "${prefix}~${id}~${p}", type: "button", value: ""]
            }
        }
    }
}

private void rebuildSubscriptions(Map appDataEntry, String roomId, String intent) {
    def roomDevice = selectedRoomDevice()
    List subs = []
    subs << [handler: "actHandler", name: "lightingIntent.${intent}", type: "DEVICE", typeId: roomId as Integer, typeName: roomDevice.displayName, filter: "true"]
    subs << [handler: "offHandler", name: "lightingIntent.Off", type: "DEVICE", typeId: roomId as Integer, typeName: roomDevice.displayName, filter: "true"]
    selectedLights().each { dev ->
        subs << [handler: "evtHandler", name: "switch", type: "DEVICE", typeId: dev.id as Integer, typeName: dev.displayName, filter: "true"]
        if (hasCap(dev, "SwitchLevel")) subs << [handler: "checkSceneEvt", name: "level", type: "DEVICE", typeId: dev.id as Integer, typeName: dev.displayName, filter: "true"]
    }
    appDataEntry.subscriptions = subs
}

private void setSetting(List settings, String name, value) {
    def s = settings.find { it.name == name }
    if (!s) {
        s = [deviceList: null, multiple: value instanceof Collection, name: name, type: "enum", value: null]
        settings << s
    }
    s.value = value instanceof Collection ? JsonOutput.toJson(value) : "${value}"
}

private void setDeviceList(List settings, String name, Map devices) {
    if (!devices) return
    def s = settings.find { it.name == name }
    if (!s) {
        s = [deviceList: [:], multiple: true, name: name, type: "capability.switch", value: null]
        settings << s
    }
    s.deviceList = devices
}

private Map deepCopy(Map obj) {
    return new JsonSlurper().parseText(JsonOutput.toJson(obj)) as Map
}

private String safeTypeName(dev) {
    try { return dev.getTypeName() ?: "" } catch (Exception ignored) { return "" }
}

private String safeTypeNamespace(dev) {
    try { return dev.getTypeNamespace() ?: "hubitat" } catch (Exception ignored) { return "hubitat" }
}

private void debug(String msg) {
    if (debugLogging) log.debug "Room Lighting Template Builder v2: ${msg}"
}
