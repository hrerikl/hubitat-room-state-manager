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
 *   colorTemperature = room-level virtual lighting color temperature for dashboard/voice/control use
 *   lock = locked/unlocked for room automation lock control
 *   roomState = Off | Occupied | Engaged | Locked
 *   lightingIntent = Off | Courtesy | On
 *
 * It owns component children:
 *   <Room display name> MetaLight = automation-facing effective switch/level output
 *   <Room display name> Courtesy = enables/disables neighbor courtesy lighting
 *   <Room display name> Engaged = enables/disables engaged room state
 *   <Room display name> Locked = freezes room state automation
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
        capability 'ColorTemperature'
        capability 'Lock'
        capability 'Sensor'

        attribute 'roomState', 'enum', ['Off', 'Occupied', 'Engaged', 'Asleep', 'Locked']
        attribute 'lightingIntent', 'enum', ['Off', 'Courtesy', 'Night', 'On']
        attribute 'metaLightSwitch', 'enum', ['off', 'on']
        attribute 'metaLightLevel', 'number'
        attribute 'metaLightColorTemperature', 'number'
        attribute 'customLighting', 'enum', ['off', 'on']
        attribute 'courtesyEnabled', 'enum', ['off', 'on']
        attribute 'engagedEnabled', 'enum', ['off', 'on']
        attribute 'asleepEnabled', 'enum', ['off', 'on']
        attribute 'nightLighting', 'enum', ['off', 'on']
        attribute 'nightLightingTimeoutMinutes', 'number'
        attribute 'lockedEnabled', 'enum', ['off', 'on']
        attribute 'presenceActivity', 'number'
        attribute 'lastPresenceActivity', 'string'

        command 'setRoomState', [[name: 'Room State', type: 'ENUM', constraints: ['Off', 'Occupied', 'Engaged', 'Asleep', 'Locked']]]
        command 'setLightingIntent', [[name: 'Lighting Intent', type: 'ENUM', constraints: ['Off', 'Courtesy', 'Night', 'On']]]
        command 'setSwitchState', [[name: 'Switch State', type: 'ENUM', constraints: ['off', 'on']]]
        command 'setRoomLevel', [[name: 'Room Level', type: 'NUMBER']]
        command 'setRoomColorTemperature', [[name: 'Room Color Temperature', type: 'NUMBER']]
        command 'setMetaLightSwitchState', [[name: 'Switch State', type: 'ENUM', constraints: ['off', 'on']]]
        command 'setMetaLightLevel', [[name: 'MetaLight Level', type: 'NUMBER']]
        command 'setMetaLightColorTemperature', [[name: 'MetaLight Color Temperature', type: 'NUMBER']]
        command 'setCustomLightingState', [[name: 'Custom Lighting State', type: 'ENUM', constraints: ['off', 'on']]]
        command 'activateCustomLighting'
        command 'clearCustomLighting'
        command 'setCourtesySwitchState', [[name: 'Courtesy Switch State', type: 'ENUM', constraints: ['off', 'on']]]
        command 'setEngagedSwitchState', [[name: 'Engaged Switch State', type: 'ENUM', constraints: ['off', 'on']]]
        command 'setAsleepSwitchState', [[name: 'Asleep Switch State', type: 'ENUM', constraints: ['off', 'on']]]
        command 'setNightLightingState', [[name: 'Night Lighting State', type: 'ENUM', constraints: ['off', 'on']]]
        command 'setNightLightingTimeoutMinutes', [[name: 'Timeout minutes', type: 'NUMBER']]
        command 'activateNightLighting', [[name: 'Timeout minutes', type: 'NUMBER']]
        command 'clearNightLighting'
        command 'setLockedSwitchState', [[name: 'Locked Switch State', type: 'ENUM', constraints: ['off', 'on']]]
        command 'setEngagedSwitchLabel', [[name: 'Engaged Switch Label', type: 'STRING']]
        command 'setAsleepSwitchLabel', [[name: 'Asleep Switch Label', type: 'STRING']]
        command 'setLockedSwitchLabel', [[name: 'Locked Switch Label', type: 'STRING']]
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
    if (device.currentValue('colorTemperature') == null) {
        sendEvent(name: 'colorTemperature', value: 2700, unit: 'K')
    }
    if (device.currentValue('lock') == null) {
        sendEvent(name: 'lock', value: 'unlocked')
    }
    if (device.currentValue('roomState') == null) {
        sendEvent(name: 'roomState', value: 'Off')
    }
    if (device.currentValue('lightingIntent') == null) {
        sendEvent(name: 'lightingIntent', value: 'Off')
    }
    if (device.currentValue('metaLightSwitch') == null) {
        sendEvent(name: 'metaLightSwitch', value: 'off')
    }
    if (device.currentValue('metaLightLevel') == null) {
        sendEvent(name: 'metaLightLevel', value: 0, unit: '%')
    }
    if (device.currentValue('metaLightColorTemperature') == null) {
        sendEvent(name: 'metaLightColorTemperature', value: 2700, unit: 'K')
    }
    if (device.currentValue('customLighting') == null) {
        sendEvent(name: 'customLighting', value: 'off')
    }
    if (device.currentValue('courtesyEnabled') == null) {
        sendEvent(name: 'courtesyEnabled', value: 'on')
    }
    if (device.currentValue('engagedEnabled') == null) {
        sendEvent(name: 'engagedEnabled', value: 'off')
    }
    if (device.currentValue('asleepEnabled') == null) {
        sendEvent(name: 'asleepEnabled', value: 'off')
    }
    if (device.currentValue('nightLighting') == null) {
        sendEvent(name: 'nightLighting', value: 'off')
    }
    if (device.currentValue('nightLightingTimeoutMinutes') == null) {
        sendEvent(name: 'nightLightingTimeoutMinutes', value: 0)
    }
    if (device.currentValue('lockedEnabled') == null) {
        sendEvent(name: 'lockedEnabled', value: 'off')
    }
    if (device.currentValue('presenceActivity') == null) {
        sendEvent(name: 'presenceActivity', value: 0)
    }
    if (device.currentValue('lastPresenceActivity') == null) {
        sendEvent(name: 'lastPresenceActivity', value: 'Never')
    }
    createOrUpdateMetaLightDevice()
    createOrUpdateChildSwitchDevice('Courtesy')
    createOrUpdateChildSwitchDevice('Engaged')
    createOrUpdateChildSwitchDevice('Asleep')
    createOrUpdateChildSwitchDevice('Locked')
}

void on() {
    sendEvent(name: 'switch', value: 'on', type: 'digital', isStateChange: true)
}

void off() {
    sendEvent(name: 'switch', value: 'off', type: 'digital', isStateChange: true)
    sendEvent(name: 'level', value: 0, unit: '%', type: 'digital', isStateChange: true)
}

void setLevel(value) {
    Integer normalized = normalizeLevel(value)
    sendEvent(name: 'level', value: normalized, unit: '%', type: 'digital', isStateChange: true)
    sendEvent(name: 'switch', value: normalized > 0 ? 'on' : 'off', type: 'digital', isStateChange: true)
}

void setColorTemperature(value) {
    Integer normalized = normalizeColorTemperature(value)
    sendEvent(name: 'colorTemperature', value: normalized, unit: 'K', type: 'digital', isStateChange: true)
    setMetaLightColorTemperature(normalized)
}

void lock() {
    setLockedSwitchState('on')
}

void unlock() {
    setLockedSwitchState('off')
    setAsleepSwitchState('off')
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

void setRoomColorTemperature(value) {
    Integer normalized = normalizeColorTemperature(value)
    if ((device.currentValue('colorTemperature') ?: -1) as Integer != normalized) {
        sendEvent(name: 'colorTemperature', value: normalized, unit: 'K')
    }
}

void setMetaLightSwitchState(String value) {
    String normalized = value == 'on' ? 'on' : 'off'
    if (device.currentValue('metaLightSwitch') != normalized) {
        sendEvent(name: 'metaLightSwitch', value: normalized)
    }

    def child = metaLightDevice()
    if (!child) return
    child.setSwitchState(normalized)
}

void setMetaLightLevel(value) {
    Integer normalized = normalizeLevel(value)
    if ((device.currentValue('metaLightLevel') ?: -1) as Integer != normalized) {
        sendEvent(name: 'metaLightLevel', value: normalized, unit: '%')
    }

    def child = metaLightDevice()
    if (!child) return
    child.setRoomLevel(normalized)
}

void setMetaLightColorTemperature(value) {
    Integer normalized = normalizeColorTemperature(value)
    if ((device.currentValue('metaLightColorTemperature') ?: -1) as Integer != normalized) {
        sendEvent(name: 'metaLightColorTemperature', value: normalized, unit: 'K')
    }
    setRoomColorTemperature(normalized)

    def child = metaLightDevice()
    if (!child) return
    child.setRoomColorTemperature(normalized)
}

void setCustomLightingState(String value) {
    String normalized = value == 'on' ? 'on' : 'off'
    if (device.currentValue('customLighting') != normalized) {
        sendEvent(name: 'customLighting', value: normalized, isStateChange: true)
    }
}

void activateCustomLighting() {
    setCustomLightingState('on')
}

void clearCustomLighting() {
    setCustomLightingState('off')
}

void setCourtesySwitchState(String value) {
    setChildSwitchState('Courtesy', value, 'courtesyEnabled')
}

void setEngagedSwitchState(String value) {
    setChildSwitchState('Engaged', value, 'engagedEnabled')
}

void setAsleepSwitchState(String value) {
    setChildSwitchState('Asleep', value, 'asleepEnabled')
}

void setNightLightingState(String value) {
    String normalized = value == 'on' ? 'on' : 'off'
    if (device.currentValue('nightLighting') != normalized) {
        sendEvent(name: 'nightLighting', value: normalized, isStateChange: true)
    }
}

void setNightLightingTimeoutMinutes(value) {
    Integer minutes = normalizeLevel(value)
    if ((device.currentValue('nightLightingTimeoutMinutes') ?: -1) as Integer != minutes) {
        sendEvent(name: 'nightLightingTimeoutMinutes', value: minutes)
    }
}

void activateNightLighting(value) {
    Integer minutes = normalizeLevel(value)
    try {
        parent?.activateNightLightingFromDevice(minutes)
    } catch (Exception e) {
        log.warn "Could not request Night lighting activation: ${e.message}"
    }
}

void clearNightLighting() {
    try {
        parent?.clearNightLightingFromDevice()
    } catch (Exception e) {
        log.warn "Could not request Night lighting clear: ${e.message}"
    }
}

void setLockedSwitchState(String value) {
    setChildSwitchState('Locked', value, 'lockedEnabled')
    setLockAttribute(value == 'on' ? 'locked' : 'unlocked')
}

void setEngagedSwitchLabel(String value) {
    updateChildSwitchLabel('Engaged', value)
}

void setAsleepSwitchLabel(String value) {
    updateChildSwitchLabel('Asleep', value)
}

void setLockedSwitchLabel(String value) {
    updateChildSwitchLabel('Locked', value)
}

void componentOn(def childDevice) {
    setComponentSwitchFromChild(childDevice, 'on')
}

void componentOff(def childDevice) {
    setComponentSwitchFromChild(childDevice, 'off')
}

void setRoomState(String value) {
    List allowed = ['Off', 'Occupied', 'Engaged', 'Asleep', 'Locked']
    String normalized = allowed.contains(value) ? value : 'Off'
    if (device.currentValue('roomState') != normalized) {
        sendEvent(name: 'roomState', value: normalized)
    }
}

void setLightingIntent(String value) {
    List allowed = ['Off', 'Courtesy', 'Night', 'On']
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

private Integer normalizeColorTemperature(value) {
    Integer ct = 2700
    try {
        ct = value as Integer
    } catch (Exception ignored) {
        ct = 2700
    }
    return Math.max(Math.min(ct, 10000), 1500)
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

private void createOrUpdateChildSwitchDevice(String role) {
    String dni = childSwitchDni(role)
    String desiredLabel = childSwitchLabel(role)
    def child = getChildDevice(dni)

    if (!child) {
        child = addChildDevice('lundby', 'Simple Room Child Switch Device', dni, [
            name: desiredLabel,
            label: desiredLabel,
            isComponent: true
        ])
        log.info "Created component ${role} switch: ${desiredLabel}"
    } else if (child.label != desiredLabel) {
        child.setLabel(desiredLabel)
        log.info "Updated component ${role} switch label: ${desiredLabel}"
    }

    child.setSwitchState((device.currentValue(attributeForRole(role)) ?: defaultSwitchStateForRole(role)) as String)
}

private def metaLightDevice() {
    def child = getChildDevice(metaLightDni())
    if (!child) {
        createOrUpdateMetaLightDevice()
        child = getChildDevice(metaLightDni())
    }
    return child
}

private def childSwitchDevice(String role) {
    def child = getChildDevice(childSwitchDni(role))
    if (!child) {
        createOrUpdateChildSwitchDevice(role)
        child = getChildDevice(childSwitchDni(role))
    }
    return child
}

private String metaLightDni() {
    return "${device.deviceNetworkId}-MetaLight"
}

private void setChildSwitchState(String role, String value, String attributeName) {
    String normalized = value == 'on' ? 'on' : 'off'
    if (device.currentValue(attributeName) != normalized) {
        sendEvent(name: attributeName, value: normalized)
    }

    def child = childSwitchDevice(role)
    if (child) {
        child.setSwitchState(normalized)
    }
}

private void setLockAttribute(String value) {
    String normalized = value == 'locked' ? 'locked' : 'unlocked'
    if (device.currentValue('lock') != normalized) {
        sendEvent(name: 'lock', value: normalized)
    }
}

private void setComponentSwitchFromChild(def childDevice, String value) {
    String role = roleFromDni(childDevice?.deviceNetworkId)
    if (!role) return

    setChildSwitchState(role, value, attributeForRole(role))
}

private void updateChildSwitchLabel(String role, String value) {
    if (value?.trim()) {
        state["${role}Label"] = value.trim()
    }
    createOrUpdateChildSwitchDevice(role)
}

private String childSwitchLabel(String role) {
    String configured = state["${role}Label"]?.toString()
    if (configured) return configured
    return "${device.displayName ?: device.name} ${role}"
}

private String defaultSwitchStateForRole(String role) {
    return role == 'Courtesy' ? 'on' : 'off'
}

private String attributeForRole(String role) {
    if (role == 'Courtesy') return 'courtesyEnabled'
    if (role == 'Engaged') return 'engagedEnabled'
    if (role == 'Asleep') return 'asleepEnabled'
    if (role == 'Locked') return 'lockedEnabled'
    return null
}

private String roleFromDni(String dni) {
    if (dni == childSwitchDni('Courtesy')) return 'Courtesy'
    if (dni == childSwitchDni('Engaged')) return 'Engaged'
    if (dni == childSwitchDni('Locked')) return 'Locked'
    return null
}

private String childSwitchDni(String role) {
    return "${device.deviceNetworkId}-${role}"
}
