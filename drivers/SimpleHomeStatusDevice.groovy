/*****************************************************************************************
 * Simple Home Status Device - Driver
 *
 * Install as Drivers Code:
 *   Name: Simple Home Status Device
 *   Namespace: lundby
 *
 * House-level status surface for dashboards. Created and owned by Simple Home.
 * Published to by Simple Home (mode) and Simple Circadian Lighting (circadian/weather).
 *****************************************************************************************/

metadata {
    definition(
        name: 'Simple Home Status Device',
        namespace: 'lundby',
        author: 'Erik Lundby'
    ) {
        capability 'Sensor'

        attribute 'locationMode', 'string'
        attribute 'circadianLevel', 'number'
        attribute 'circadianCT', 'number'
        attribute 'outdoorLightingEnabled', 'enum', ['on', 'off']
        attribute 'skyCondition', 'string'
        attribute 'circadianLastUpdated', 'string'
    }
}

void installed() {
    initialize()
}

void updated() {
    initialize()
}

void initialize() {
    if (device.currentValue('locationMode') == null) sendEvent(name: 'locationMode', value: 'Unknown')
    if (device.currentValue('circadianLevel') == null) sendEvent(name: 'circadianLevel', value: 0, unit: '%')
    if (device.currentValue('circadianCT') == null) sendEvent(name: 'circadianCT', value: 2700, unit: 'K')
    if (device.currentValue('outdoorLightingEnabled') == null) sendEvent(name: 'outdoorLightingEnabled', value: 'off')
    if (device.currentValue('skyCondition') == null) sendEvent(name: 'skyCondition', value: 'Unknown')
    if (device.currentValue('circadianLastUpdated') == null) sendEvent(name: 'circadianLastUpdated', value: 'Never')
}

void setLocationMode(String mode) {
    publishValue('locationMode', mode ?: 'Unknown')
}

void setCircadianLevel(Integer level) {
    publishValue('circadianLevel', level, '%')
}

void setCircadianCT(Integer ct) {
    publishValue('circadianCT', ct, 'K')
}

void setOutdoorLightingEnabled(String state) {
    publishValue('outdoorLightingEnabled', state == 'on' ? 'on' : 'off')
}

void setSkyCondition(String condition) {
    publishValue('skyCondition', condition ?: 'Unknown')
}

void setCircadianLastUpdated(String timestamp) {
    publishValue('circadianLastUpdated', timestamp ?: 'Never')
}

private void publishValue(String name, value, String unit = null) {
    if (device.currentValue(name)?.toString() == value?.toString()) return
    Map event = [name: name, value: value]
    if (unit) event.unit = unit
    sendEvent(event)
}
