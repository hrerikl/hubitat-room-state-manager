/*****************************************************************************************
 * Simple Room Meta Device - Driver
 *
 * Install as Drivers Code file 1:
 *   Name: Simple Room Meta Device
 *   Namespace: lundby
 *
 * This device is the public Room device:
 *   switch = on/off for dashboard/control use
 *   level = room-level virtual lighting level for dashboard/voice/control use
 *   roomState = Off | Occupied | Engaged | Locked
 *   lightingIntent = Off | Courtesy | On
 *
 * It owns component children:
 *   <Room display name> MetaLight = automation-facing effective switch/level output
 *   <Room display name> Courtesy = enables/disables neighbor courtesy lighting
 *****************************************************************************************/

metadata {
    definition(
        name: 'Simple Room Meta Device',
        namespace: 'lundby',
        author: 'Erik Lundby / ChatGPT'
    ) {
        capability 'Actuator'
        capability 'Switch'
        capability 'SwitchLevel'
        capability 'Sensor'

        attribute 'roomState', 'enum', ['Off', 'Occupied', 'Engaged', 'Locked']
        attribute 'lightingIntent', 'enum', ['Off', 'Courtesy', 'On']
        attribute 'courtesyEnabled', 'enum', ['off', 'on']
        attribute 'presenceActivity', 'number'
        attribute 'lastPresenceActivity', 'string'

        command 'setRoomState', [[name: 'Room State', type: 'ENUM', constraints: ['Off', 'Occupied', 'Engaged', 'Locked']]]
        command 'setLightingIntent', [[name: 'Lighting Intent', type: 'ENUM', constraints: ['Off', 'Courtesy', 'On']]]
        command 'setSwitchState', [[name: 'Switch State', type: 'ENUM', constraints: ['off', 'on']]]
        command 'setRoomLevel', [[name: 'Room Level', type: 'NUMBER']]
        command 'setMetaLightSwitchState', [[name: 'Switch State', type: 'ENUM', constraints: ['off', 'on']]]
        command 'setMetaLightLevel', [[name: 'MetaLight Level', type: 'NUMBER']]
        command 'setCourtesySwitchState', [[name: 'Courtesy Switch State', type: 'ENUM', constraints: ['off', 'on']]]
        command 'recordPresenceActivity', [[name: 'Epoch milliseconds', type: 'STRING']]
    }
}

void installed() {
    initialize()
}

void updated() {
    initialize()
}

void initialize() {
    if (device.currentValue('switch') == null) {
        sendEvent(name: 'switch', value: 'off')
    }
    if (device.currentValue('level') == null) {
        sendEvent(name: 'level', value: 0, unit: '%')
    }
    if (device.currentValue('roomState') == null) {
        sendEvent(name: 'roomState', value: 'Off')
    }
    if (device.currentValue('lightingIntent') == null) {
        sendEvent(name: 'lightingIntent', value: 'Off')
    }
    if (device.currentValue('courtesyEnabled') == null) {
        sendEvent(name: 'courtesyEnabled', value: 'on')
    }
    if (device.currentValue('presenceActivity') == null) {
        sendEvent(name: 'presenceActivity', value: 0)
    }
    if (device.currentValue('lastPresenceActivity') == null) {
        sendEvent(name: 'lastPresenceActivity', value: 'Never')
    }
    createOrUpdateMetaLightDevice()
    createOrUpdateCourtesyDevice()
}

void on() {
    sendEvent(name: 'switch', value: 'on', type: 'digital')
}

void off() {
    sendEvent(name: 'switch', value: 'off', type: 'digital')
    sendEvent(name: 'level', value: 0, unit: '%', type: 'digital')
}

void setLevel(value) {
    Integer normalized = normalizeLevel(value)
    sendEvent(name: 'level', value: normalized, unit: '%', type: 'digital')
    sendEvent(name: 'switch', value: normalized > 0 ? 'on' : 'off', type: 'digital')
}

void setSwitchState(String value) {
    String normalized = value == 'on' ? 'on' : 'off'
    if (device.currentValue('switch') != normalized) {
        sendEvent(name: 'switch', value: normalized)
    }
}

void setRoomLevel(value) {
    Integer normalized = normalizeLevel(value)
    if ((device.currentValue('level') ?: -1) as Integer != normalized) {
        sendEvent(name: 'level', value: normalized, unit: '%')
    }
}

void setMetaLightSwitchState(String value) {
    def child = metaLightDevice()
    if (!child) return
    child.setSwitchState(value)
}

void setMetaLightLevel(value) {
    def child = metaLightDevice()
    if (!child) return
    child.setRoomLevel(value)
}

void setCourtesySwitchState(String value) {
    String normalized = value == 'off' ? 'off' : 'on'
    if (device.currentValue('courtesyEnabled') != normalized) {
        sendEvent(name: 'courtesyEnabled', value: normalized)
    }

    def child = courtesyDevice()
    if (child) {
        child.setSwitchState(normalized)
    }
}

void componentOn(def childDevice) {
    if (childDevice?.deviceNetworkId == courtesyDni()) {
        setCourtesySwitchState('on')
    }
}

void componentOff(def childDevice) {
    if (childDevice?.deviceNetworkId == courtesyDni()) {
        setCourtesySwitchState('off')
    }
}

void setRoomState(String value) {
    List allowed = ['Off', 'Occupied', 'Engaged', 'Locked']
    String normalized = allowed.contains(value) ? value : 'Off'
    if (device.currentValue('roomState') != normalized) {
        sendEvent(name: 'roomState', value: normalized)
    }
}

void setLightingIntent(String value) {
    List allowed = ['Off', 'Courtesy', 'On']
    String normalized = allowed.contains(value) ? value : 'Off'
    if (device.currentValue('lightingIntent') != normalized) {
        sendEvent(name: 'lightingIntent', value: normalized)
    }
}

void recordPresenceActivity(String epochMs) {
    Long epoch = 0L
    try {
        epoch = epochMs as Long
    } catch (Exception ignored) {
        epoch = now()
    }

    String formatted = new Date(epoch).format('yyyy-MM-dd HH:mm:ss', location.timeZone)
    sendEvent(name: 'presenceActivity', value: epoch, unit: 'ms', isStateChange: true)
    sendEvent(name: 'lastPresenceActivity', value: formatted, isStateChange: true)
}

private Integer normalizeLevel(value) {
    Integer level = 0
    try {
        level = value as Integer
    } catch (Exception ignored) {
        level = 0
    }
    return Math.max(Math.min(level, 100), 0)
}

private void createOrUpdateMetaLightDevice() {
    String dni = metaLightDni()
    String desiredLabel = "${device.displayName ?: device.name} MetaLight"
    def child = getChildDevice(dni)

    if (!child) {
        addChildDevice('lundby', 'Simple Room Meta Light Device', dni, [
            name: desiredLabel,
            label: desiredLabel,
            isComponent: true
        ])
        log.info "Created component MetaLight: ${desiredLabel}"
    } else if (child.label != desiredLabel) {
        child.setLabel(desiredLabel)
        log.info "Updated component MetaLight label: ${desiredLabel}"
    }
}

private void createOrUpdateCourtesyDevice() {
    String dni = courtesyDni()
    String desiredLabel = "${device.displayName ?: device.name} Courtesy"
    def child = getChildDevice(dni)

    if (!child) {
        child = addChildDevice('lundby', 'Simple Room Child Switch Device', dni, [
            name: desiredLabel,
            label: desiredLabel,
            isComponent: true
        ])
        log.info "Created component Courtesy switch: ${desiredLabel}"
    } else if (child.label != desiredLabel) {
        child.setLabel(desiredLabel)
        log.info "Updated component Courtesy switch label: ${desiredLabel}"
    }

    child.setSwitchState((device.currentValue('courtesyEnabled') ?: 'on') as String)
}

private def metaLightDevice() {
    def child = getChildDevice(metaLightDni())
    if (!child) {
        createOrUpdateMetaLightDevice()
        child = getChildDevice(metaLightDni())
    }
    return child
}

private def courtesyDevice() {
    def child = getChildDevice(courtesyDni())
    if (!child) {
        createOrUpdateCourtesyDevice()
        child = getChildDevice(courtesyDni())
    }
    return child
}

private String metaLightDni() {
    return "${device.deviceNetworkId}-MetaLight"
}

private String courtesyDni() {
    return "${device.deviceNetworkId}-Courtesy"
}
