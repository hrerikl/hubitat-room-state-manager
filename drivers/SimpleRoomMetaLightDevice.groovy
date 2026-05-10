/*****************************************************************************************
 * Simple Room Meta Light Device - Driver
 *
 * Install as Drivers Code file:
 *   Name: Simple Room Meta Light Device
 *   Namespace: lundby
 *
 * This device is the automation-facing effective room light:
 *   switch/level are published by the Room State child app
 *   manual on/off/setLevel commands are intentionally ignored
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
        capability 'Sensor'

        command 'setSwitchState', [[name: 'Switch State', type: 'ENUM', constraints: ['off', 'on']]]
        command 'setRoomLevel', [[name: 'Room Level', type: 'NUMBER']]
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

private Integer normalizeLevel(value) {
    Integer level = 0
    try {
        level = value as Integer
    } catch (Exception ignored) {
        level = 0
    }
    return Math.max(Math.min(level, 100), 0)
}
