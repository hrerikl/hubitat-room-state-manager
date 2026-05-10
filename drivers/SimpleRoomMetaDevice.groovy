/*****************************************************************************************
 * Simple Room Meta Device - Driver
 *
 * Install as Drivers Code file 1:
 *   Name: Simple Room Meta Device
 *   Namespace: lundby
 *
 * This device is the public Room device:
 *   switch = on/off for dashboard/control use
 *   roomState = Off | Occupied | Engaged | Locked
 *   lightingIntent = Off | Courtesy | On
 *****************************************************************************************/

metadata {
    definition(
        name: 'Simple Room Meta Device',
        namespace: 'lundby',
        author: 'Erik Lundby / ChatGPT'
    ) {
        capability 'Actuator'
        capability 'Switch'
        capability 'Sensor'

        attribute 'roomState', 'enum', ['Off', 'Occupied', 'Engaged', 'Locked']
        attribute 'lightingIntent', 'enum', ['Off', 'Courtesy', 'On']
        attribute 'presenceActivity', 'number'
        attribute 'lastPresenceActivity', 'string'

        command 'setRoomState', [[name: 'Room State', type: 'ENUM', constraints: ['Off', 'Occupied', 'Engaged', 'Locked']]]
        command 'setLightingIntent', [[name: 'Lighting Intent', type: 'ENUM', constraints: ['Off', 'Courtesy', 'On']]]
        command 'setSwitchState', [[name: 'Switch State', type: 'ENUM', constraints: ['off', 'on']]]
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
    if (device.currentValue('roomState') == null) {
        sendEvent(name: 'roomState', value: 'Off')
    }
    if (device.currentValue('lightingIntent') == null) {
        sendEvent(name: 'lightingIntent', value: 'Off')
    }
    if (device.currentValue('presenceActivity') == null) {
        sendEvent(name: 'presenceActivity', value: 0)
    }
    if (device.currentValue('lastPresenceActivity') == null) {
        sendEvent(name: 'lastPresenceActivity', value: 'Never')
    }
}

void on() {
    sendEvent(name: 'switch', value: 'on', type: 'digital')
}

void off() {
    sendEvent(name: 'switch', value: 'off', type: 'digital')
}

void setSwitchState(String value) {
    String normalized = value == 'on' ? 'on' : 'off'
    if (device.currentValue('switch') != normalized) {
        sendEvent(name: 'switch', value: normalized)
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
