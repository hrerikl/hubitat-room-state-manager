/*****************************************************************************************
 * Simple Room Meta Light Device - Driver
 *
 * Install as Drivers Code file:
 *   Name: Simple Room Meta Light Device
 *   Namespace: lundby
 *
 * This device is the automation-facing effective room light:
 *   switch/level/colorTemperature are published by the Room State child app
 *   manual on/off/setLevel/setColorTemperature commands are intentionally ignored
 *****************************************************************************************/

metadata {
    definition(
        name: 'Simple Room Meta Light Device',
        namespace: 'lundby',
        author: 'Erik Lundby / ChatGPT'
    ) {
        capability 'Actuator'
        capability 'Switch'
        capability 'SwitchLevel'
        capability 'ColorTemperature'
        capability 'Sensor'

        command 'setSwitchState', [[name: 'Switch State', type: 'ENUM', constraints: ['off', 'on']]]
        command 'setRoomLevel', [[name: 'Room Level', type: 'NUMBER']]
        command 'setRoomColorTemperature', [[name: 'Room Color Temperature', type: 'NUMBER']]
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
}

void on() {
    log.debug 'Ignoring manual on; this device is driven by the Room State app'
}

void off() {
    log.debug 'Ignoring manual off; this device is driven by the Room State app'
}

void setLevel(value) {
    log.debug 'Ignoring manual setLevel; this device is driven by the Room State app'
}

void setColorTemperature(value) {
    log.debug 'Ignoring manual setColorTemperature; this device is driven by the Room State app'
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
