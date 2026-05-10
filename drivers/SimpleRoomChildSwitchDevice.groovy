/*****************************************************************************************
 * Simple Room Child Switch Device - Component Driver
 *
 * Install as Drivers Code file:
 *   Name: Simple Room Child Switch Device
 *   Namespace: lundby
 *
 * Reusable component switch for Room Meta Device child controls such as
 * Courtesy, Locked, and Engaged.
 *****************************************************************************************/

metadata {
    definition(
        name: 'Simple Room Child Switch Device',
        namespace: 'lundby',
        author: 'Erik Lundby / ChatGPT'
    ) {
        capability 'Actuator'
        capability 'Switch'
        capability 'Sensor'

        command 'setSwitchState', [[name: 'Switch State', type: 'ENUM', constraints: ['off', 'on']]]
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
}

void on() {
    sendEvent(name: 'switch', value: 'on', type: 'digital')
    try {
        parent?.componentOn(device)
    } catch (Exception e) {
        log.warn "Could not notify parent of component on: ${e.message}"
    }
}

void off() {
    sendEvent(name: 'switch', value: 'off', type: 'digital')
    try {
        parent?.componentOff(device)
    } catch (Exception e) {
        log.warn "Could not notify parent of component off: ${e.message}"
    }
}

void setSwitchState(String value) {
    String normalized = value == 'on' ? 'on' : 'off'
    if (device.currentValue('switch') != normalized) {
        sendEvent(name: 'switch', value: normalized)
    }
}
