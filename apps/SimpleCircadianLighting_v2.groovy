/**
 * Simple Circadian Lighting v2 - Child App
 *
 * Consumes an outdoor light sensor and publishes a house reference level/CT
 * to a selected color-temperature bulb.
 */

definition(
    name: 'Simple Circadian Lighting v2',
    namespace: 'lundby',
    author: 'Erik Lundby / ChatGPT',
    description: 'Publishes a circadian reference level and color temperature.',
    category: 'Convenience',
    parent: 'lundby:Simple Room State Manager v2',
    singleInstance: false,
    iconUrl: '',
    iconX2Url: ''
)

preferences {
    page(name: 'mainPage', title: 'Simple Circadian Lighting', install: true, uninstall: true) {
        section('Devices') {
            input 'outdoorLightSensor', 'capability.illuminanceMeasurement', title: 'Outdoor light sensor', multiple: false, required: true
            input 'referenceBulb', 'capability.colorTemperature', title: 'Reference color temperature bulb', multiple: false, required: true
        }

        section('Schedule') {
            input 'publishIntervalMinutes', 'enum', title: 'Publish interval', options: ['5', '10', '15', '30', '60'], defaultValue: '15', required: true
            input 'publishNow', 'button', title: 'Update reference now'
        }

        section('Reference limits') {
            input 'minReferenceLevel', 'number', title: 'Minimum reference level', defaultValue: 5, required: true
            input 'maxReferenceLevel', 'number', title: 'Maximum reference level', defaultValue: 100, required: true
            input 'outdoorLuxForMaxLevel', 'number', title: 'Outdoor lux for maximum level', defaultValue: 40000, required: true
            input 'minReferenceCT', 'number', title: 'Minimum reference color temperature', defaultValue: 2200, required: true
            input 'maxReferenceCT', 'number', title: 'Maximum reference color temperature', defaultValue: 6500, required: true
        }

        section('Debug') {
            input 'debugLogging', 'bool', title: 'Enable debug logging', defaultValue: true, required: true
        }
    }
}

// -------------------- Lifecycle --------------------

def installed() {
    log.info "Installed ${app.label}"
    initialize()
}

def updated() {
    log.info "Updated ${app.label}"
    unsubscribe()
    unschedule()
    initialize()
}

def reinitializeFromParent() {
    log.info "Reinitializing ${app.label} from parent"
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    subscribe(parent.recoveryDevice(), 'switch.on', recoverSimpleHomeHandler)
    schedulePublishing()
    publishReference('initialize')
}

def appButtonHandler(String buttonName) {
    if (buttonName == 'publishNow') {
        publishReference('manual update')
    }
}

def recoverSimpleHomeHandler(evt) {
    publishReference('Simple Home recovery')
}

// -------------------- Publishing --------------------

def publishReference(String reason = 'schedule') {
    if (!outdoorLightSensor || !referenceBulb) {
        log.warn "${app.label}: Outdoor light sensor and reference bulb are required."
        return
    }

    Integer outdoorLux = currentInteger(outdoorLightSensor, 'outdoorLux', currentInteger(outdoorLightSensor, 'illuminance', 0))
    Integer outdoorCT = currentInteger(outdoorLightSensor, 'outdoorCT', minCt())
    Integer targetLevel = referenceLevel(outdoorLux)
    Integer targetCT = clampInteger(outdoorCT, minCt(), maxCt())

    debug "Publishing reference for ${reason}: outdoorLux=${outdoorLux}, outdoorCT=${outdoorCT}, level=${targetLevel}, ct=${targetCT}"
    publishToReferenceBulb(targetCT, targetLevel)
}

private void publishToReferenceBulb(Integer ct, Integer level) {
    Integer currentLevel = currentInteger(referenceBulb, 'level', -1)
    Integer currentCT = currentInteger(referenceBulb, 'colorTemperature', -1)

    if (currentLevel == level && currentCT == ct) {
        debug "Reference bulb already at level=${level}, ct=${ct}; skipping command"
        return
    }

    try {
        referenceBulb.setColorTemperature(ct, level)
    } catch (Exception ignored) {
        try {
            referenceBulb.setColorTemperature(ct)
            referenceBulb.setLevel(level)
        } catch (Exception e) {
            log.warn "${app.label}: Could not publish reference bulb level/CT: ${e.message}"
        }
    }
}

private Integer referenceLevel(Integer outdoorLux) {
    BigDecimal minLevel = minLevel()
    BigDecimal maxLevel = maxLevel()
    BigDecimal lux = clampDecimal((outdoorLux ?: 0) as BigDecimal, 0G, 120000G)
    BigDecimal fullLux = clampDecimal((outdoorLuxForMaxLevel ?: 40000) as BigDecimal, 1G, 120000G)
    BigDecimal ratio = clampDecimal(lux / fullLux, 0G, 1G)
    BigDecimal shaped = Math.sqrt(ratio as Double) as BigDecimal
    BigDecimal level = minLevel + ((maxLevel - minLevel) * shaped)

    return clampInteger(level.setScale(0, BigDecimal.ROUND_HALF_UP) as Integer, minLevel as Integer, maxLevel as Integer)
}

// -------------------- Scheduling --------------------

private void schedulePublishing() {
    switch ((publishIntervalMinutes ?: '15').toString()) {
        case '5':
            runEvery5Minutes(publishReference)
            break
        case '10':
            runEvery10Minutes(publishReference)
            break
        case '30':
            runEvery30Minutes(publishReference)
            break
        case '60':
            runEvery1Hour(publishReference)
            break
        default:
            runEvery15Minutes(publishReference)
            break
    }
}

// -------------------- Helpers --------------------

private Integer currentInteger(def device, String attributeName, Integer fallback) {
    try {
        def value = device?.currentValue(attributeName)
        return value == null ? fallback : value as Integer
    } catch (Exception ignored) {
        return fallback
    }
}

private Integer minCt() {
    return clampInteger(settingInteger(minReferenceCT, 2200), 1500, 10000)
}

private Integer maxCt() {
    return clampInteger(settingInteger(maxReferenceCT, 6500), minCt(), 10000)
}

private Integer minLevel() {
    return clampInteger(settingInteger(minReferenceLevel, 5), 1, 100)
}

private Integer maxLevel() {
    return clampInteger(settingInteger(maxReferenceLevel, 100), minLevel(), 100)
}

private Integer settingInteger(value, Integer fallback) {
    try {
        return value == null ? fallback : value as Integer
    } catch (Exception ignored) {
        return fallback
    }
}

private Integer clampInteger(Integer value, Integer min, Integer max) {
    return Math.max(Math.min(value ?: min, max), min)
}

private BigDecimal clampDecimal(BigDecimal value, BigDecimal min, BigDecimal max) {
    BigDecimal safeValue = value == null ? min : value
    if (safeValue < min) return min
    if (safeValue > max) return max
    return safeValue
}

private void debug(String message) {
    if (debugLogging != false) {
        log.debug "${app.label}: ${message}"
    }
}
