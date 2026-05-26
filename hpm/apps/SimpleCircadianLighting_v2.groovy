/**
 * Simple Circadian Lighting v2 - Child App
 *
 * Consumes an outdoor light sensor and publishes a house reference level/CT
 * to a selected color-temperature bulb. Optionally publishes a house-wide
 * Outdoor Lighting Enabled switch using lux hysteresis.
 */

definition(
    name: 'Simple Circadian Lighting v2',
    namespace: 'lundby',
    author: 'Erik Lundby / ChatGPT',
    description: 'Publishes a circadian reference level and color temperature.',
    category: 'Convenience',
    parent: 'lundby:Simple Home',
    singleInstance: false,
    iconUrl: '',
    iconX2Url: ''
)

preferences {
    page(name: 'mainPage', title: 'Simple Circadian Lighting', install: true, uninstall: true) {
        section('Devices') {
            input 'outdoorLightSensor', 'capability.illuminanceMeasurement', title: 'Outdoor light sensor', multiple: false, required: true
            input 'referenceBulb', 'capability.colorTemperature', title: 'Reference color temperature bulb', multiple: false, required: true
            input 'outdoorLightingEnabledSwitch', 'capability.switch', title: 'Outdoor lighting enabled switch, optional', multiple: false, required: false
        }

        section('Schedule') {
            input 'publishIntervalMinutes', 'enum', title: 'Publish interval', options: ['5', '10', '15', '30', '60'], defaultValue: '15', required: true
            input 'publishNow', 'button', title: 'Update reference now'
        }

        section('Reference limits') {
            input 'dayModeName', 'enum', title: 'Day mode', options: locationModeOptions(), defaultValue: 'Day', required: true
            input 'eveningModeName', 'enum', title: 'Evening mode', options: locationModeOptions(), defaultValue: 'Evening', required: true
            input 'nightModeName', 'enum', title: 'Night mode', options: locationModeOptions(), defaultValue: 'Night', required: true
            input 'dayMinimumLevel', 'number', title: 'Day minimum level', defaultValue: 35, required: true
            input 'eveningMinimumLevel', 'number', title: 'Evening minimum level', defaultValue: 20, required: true
            input 'nightReferenceLevel', 'number', title: 'Night level', defaultValue: 5, required: true
            input 'outdoorLuxForMaxLevel', 'number', title: 'Outdoor lux for maximum level', defaultValue: 40000, required: true
            input 'minReferenceCT', 'number', title: 'Minimum reference color temperature', defaultValue: 2200, required: true
            input 'maxReferenceCT', 'number', title: 'Maximum reference color temperature', defaultValue: 6500, required: true
        }

        section('Outdoor lighting enable') {
            input 'outdoorLightingEnableLux', 'number', title: 'Turn outdoor lighting switch on below this lux', defaultValue: 250, required: true
            input 'outdoorLightingDisableLux', 'number', title: 'Turn outdoor lighting switch off above this lux', defaultValue: 700, required: true
        }

        section('Debug') {
            input 'debugLogging', 'bool', title: 'Enable debug logging', defaultValue: true, required: true
            paragraph 'Simple Home can suppress or force debug logging from the parent app.'
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
    if (!circadianLightingAllowed()) {
        markDuplicateForDelete('Circadian Lighting')
        log.error "${app.label}: Duplicate Circadian Lighting app. Delete this child app and keep the primary Circadian Lighting app."
        return
    }

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

def getReferenceBulb() {
    return settings?.referenceBulb
}

def getRoomStateAppName() {
    return null
}

def getRoomLightingAppName() {
    return null
}

def getHouseIntentLightingAppName() {
    return null
}

def getModeManagerAppName() {
    return null
}

def getCircadianLightingAppName() {
    return 'Simple Circadian Lighting v2'
}

def getRoomProfile() {
    return null
}

def getConfiguredRoomName() {
    return null
}

def getManagedRoomDeviceLabel() {
    return null
}

private Boolean circadianLightingAllowed() {
    try {
        return parent.circadianLightingAllowed(app.id) != false
    } catch (Throwable e) {
        log.warn "${app.label}: Could not validate Circadian Lighting uniqueness: ${e.message}"
        return true
    }
}

private void markDuplicateForDelete(String duplicateType) {
    String desired = "Duplicate-Delete ${duplicateType}"
    try {
        if (app.label != desired) app.updateLabel(desired)
    } catch (Exception e) {
        log.warn "${app.label}: Could not rename duplicate app: ${e.message}"
    }
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
    publishOutdoorLightingEnabled(outdoorLux)
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

private void publishOutdoorLightingEnabled(Integer outdoorLux) {
    if (!outdoorLightingEnabledSwitch) return

    Integer enableLux = outdoorEnableLux()
    Integer disableLux = outdoorDisableLux(enableLux)
    String currentSwitch = outdoorLightingEnabledSwitch.currentValue('switch')?.toString() ?: 'off'

    if ((outdoorLux ?: 0) <= enableLux) {
        setOutdoorLightingSwitch('on', currentSwitch, outdoorLux, enableLux, disableLux)
    } else if ((outdoorLux ?: 0) >= disableLux) {
        setOutdoorLightingSwitch('off', currentSwitch, outdoorLux, enableLux, disableLux)
    } else {
        debug "Outdoor lighting switch unchanged at ${currentSwitch}; outdoorLux=${outdoorLux}, enable<=${enableLux}, disable>=${disableLux}"
    }
}

private void setOutdoorLightingSwitch(String target, String currentSwitch, Integer outdoorLux, Integer enableLux, Integer disableLux) {
    if (currentSwitch == target) {
        debug "Outdoor lighting switch already ${target}; outdoorLux=${outdoorLux}, enable<=${enableLux}, disable>=${disableLux}"
        return
    }

    try {
        if (target == 'on') {
            outdoorLightingEnabledSwitch.on()
        } else {
            outdoorLightingEnabledSwitch.off()
        }
        debug "Set outdoor lighting switch ${target}; outdoorLux=${outdoorLux}, enable<=${enableLux}, disable>=${disableLux}"
    } catch (Exception e) {
        log.warn "${app.label}: Could not set outdoor lighting enabled switch ${target}: ${e.message}"
    }
}

private Integer referenceLevel(Integer outdoorLux) {
    if (currentModeName() == (nightModeName ?: 'Night').toString()) {
        return clampInteger(settingInteger(nightReferenceLevel, 5), 1, 100)
    }

    BigDecimal minLevel = modeMinimumLevel()
    BigDecimal maxLevel = 100G
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

private Integer outdoorEnableLux() {
    return clampInteger(settingInteger(outdoorLightingEnableLux, 250), 0, 120000)
}

private Integer outdoorDisableLux(Integer enableLux) {
    Integer disableLux = clampInteger(settingInteger(outdoorLightingDisableLux, 700), 0, 120000)
    return Math.max(disableLux, enableLux)
}

private Integer modeMinimumLevel() {
    if (currentModeName() == (eveningModeName ?: 'Evening').toString()) {
        return clampInteger(settingInteger(eveningMinimumLevel, 20), 1, 100)
    }
    return clampInteger(settingInteger(dayMinimumLevel, 35), 1, 100)
}

private String currentModeName() {
    return location?.mode?.toString()
}

private Map locationModeOptions() {
    try {
        return location?.modes?.collectEntries { mode ->
            String name = mode?.name?.toString() ?: mode?.toString()
            [(name): name]
        } ?: [:]
    } catch (Exception e) {
        log.warn "${app.label}: Could not load Location Modes: ${e.message}"
        return [:]
    }
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
    if (debugEnabled(debugLogging != false)) {
        log.debug "${app.label}: ${message}"
    }
}
