/*****************************************************************************************
 * Simple Open-Meteo Outdoor Light Device - Driver
 *
 * Publishes outdoor light context from Open-Meteo for Simple Circadian Lighting.
 *****************************************************************************************/

metadata {
    definition(
        name: 'Simple Open-Meteo Outdoor Light Device',
        namespace: 'lundby',
        author: 'Erik Lundby / ChatGPT'
    ) {
        capability 'Sensor'
        capability 'Refresh'
        capability 'IlluminanceMeasurement'

        attribute 'outdoorLux', 'number'
        attribute 'outdoorCT', 'number'
        attribute 'cloudCover', 'number'
        attribute 'skyCondition', 'string'
        attribute 'solarRadiation', 'number'
        attribute 'weatherCode', 'number'
        attribute 'lastUpdated', 'string'
    }
}

preferences {
    input 'latitude', 'text', title: 'Latitude', defaultValue: "${location?.latitude ?: ''}", required: true
    input 'longitude', 'text', title: 'Longitude', defaultValue: "${location?.longitude ?: ''}", required: true
    input 'pollIntervalMinutes', 'enum', title: 'Poll interval', options: ['5', '10', '15', '30', '60'], defaultValue: '10', required: true
    input 'debugLogging', 'bool', title: 'Enable debug logging', defaultValue: true, required: true
}

void installed() {
    initialize()
}

void updated() {
    unschedule()
    initialize()
}

void initialize() {
    ensureInitialState()
    schedulePolling()
    refresh()
}

void refresh() {
    BigDecimal lat = decimalSetting(latitude)
    BigDecimal lon = decimalSetting(longitude)

    if (lat == null || lon == null) {
        log.warn 'Simple Open-Meteo Outdoor Light Device: Latitude and longitude are required.'
        return
    }

    Map params = [
        uri  : 'https://api.open-meteo.com/v1/forecast',
        query: [
            latitude     : lat,
            longitude    : lon,
            current      : 'cloud_cover,weather_code,shortwave_radiation',
            timezone     : 'auto',
            forecast_days: 1
        ],
        timeout: 20
    ]

    try {
        httpGet(params) { resp ->
            Map current = resp?.data?.current ?: [:]
            BigDecimal cloud = decimalValue(current.cloud_cover, 0G)
            BigDecimal radiation = decimalValue(current.shortwave_radiation, 0G)
            Integer code = integerValue(current.weather_code, 0)
            Integer lux = calculateOutdoorLux(radiation)
            Integer ct = calculateOutdoorCT(lux, cloud)
            String condition = skyCondition(code, cloud)
            String updated = new Date().format('yyyy-MM-dd HH:mm:ss', location.timeZone)

            publishValue('illuminance', lux, 'lx')
            publishValue('outdoorLux', lux, 'lx')
            publishValue('outdoorCT', ct, 'K')
            publishValue('cloudCover', cloud.setScale(0, BigDecimal.ROUND_HALF_UP) as Integer, '%')
            publishValue('solarRadiation', radiation.setScale(1, BigDecimal.ROUND_HALF_UP), 'W/m2')
            publishValue('weatherCode', code, null)
            publishValue('skyCondition', condition, null)
            publishValue('lastUpdated', updated, null)

            debug "Open-Meteo updated: lux=${lux}, ct=${ct}, cloud=${cloud}%, radiation=${radiation}, condition=${condition}"
        }
    } catch (Exception e) {
        log.warn "Simple Open-Meteo Outdoor Light Device: refresh failed: ${e.message}"
    }
}

private void ensureInitialState() {
    if (device.currentValue('illuminance') == null) sendEvent(name: 'illuminance', value: 0, unit: 'lx')
    if (device.currentValue('outdoorLux') == null) sendEvent(name: 'outdoorLux', value: 0, unit: 'lx')
    if (device.currentValue('outdoorCT') == null) sendEvent(name: 'outdoorCT', value: 2200, unit: 'K')
    if (device.currentValue('cloudCover') == null) sendEvent(name: 'cloudCover', value: 0, unit: '%')
    if (device.currentValue('solarRadiation') == null) sendEvent(name: 'solarRadiation', value: 0, unit: 'W/m2')
    if (device.currentValue('weatherCode') == null) sendEvent(name: 'weatherCode', value: 0)
    if (device.currentValue('skyCondition') == null) sendEvent(name: 'skyCondition', value: 'Unknown')
    if (device.currentValue('lastUpdated') == null) sendEvent(name: 'lastUpdated', value: 'Never')
}

private void schedulePolling() {
    switch ((pollIntervalMinutes ?: '10').toString()) {
        case '5':
            runEvery5Minutes(refresh)
            break
        case '15':
            runEvery15Minutes(refresh)
            break
        case '30':
            runEvery30Minutes(refresh)
            break
        case '60':
            runEvery1Hour(refresh)
            break
        default:
            runEvery10Minutes(refresh)
            break
    }
}

private Integer calculateOutdoorLux(BigDecimal radiation) {
    BigDecimal safeRadiation = (radiation ?: 0G) < 0G ? 0G : (radiation ?: 0G)
    BigDecimal lux = safeRadiation * 120G
    return clampInteger(lux.setScale(0, BigDecimal.ROUND_HALF_UP) as Integer, 0, 120000)
}

private Integer calculateOutdoorCT(Integer lux, BigDecimal cloud) {
    if ((lux ?: 0) < 50) return 2200

    Calendar cal = Calendar.getInstance(location.timeZone)
    BigDecimal hour = (cal.get(Calendar.HOUR_OF_DAY) as BigDecimal) + ((cal.get(Calendar.MINUTE) as BigDecimal) / 60G)
    BigDecimal dayProgress = clampDecimal((hour - 6G) / 12G, 0G, 1G)
    BigDecimal daylight = Math.sin(Math.PI * (dayProgress as Double)) as BigDecimal
    BigDecimal cloudBoost = clampDecimal(cloud ?: 0G, 0G, 100G) * 4G
    BigDecimal ct = 2200G + (4300G * daylight) + cloudBoost

    return clampInteger(ct.setScale(0, BigDecimal.ROUND_HALF_UP) as Integer, 2200, 7000)
}

private String skyCondition(Integer code, BigDecimal cloud) {
    if ([45, 48].contains(code)) return 'Fog'
    if ((code ?: 0) >= 71 && code <= 86) return 'Snow'
    if ((code ?: 0) >= 51 && code <= 67) return 'Rain'
    if ((code ?: 0) >= 95) return 'Thunderstorm'
    if ((cloud ?: 0G) >= 85G) return 'Cloudy'
    if ((cloud ?: 0G) >= 35G) return 'Partly Cloudy'
    return 'Clear'
}

private void publishValue(String name, value, String unit) {
    Map event = [name: name, value: value]
    if (unit) event.unit = unit
    if (device.currentValue(name)?.toString() != value?.toString()) {
        sendEvent(event)
    }
}

private BigDecimal decimalSetting(value) {
    try {
        if (value == null || value.toString().trim() == '') return null
        return value as BigDecimal
    } catch (Exception ignored) {
        return null
    }
}

private BigDecimal decimalValue(value, BigDecimal fallback) {
    try {
        return value == null ? fallback : value as BigDecimal
    } catch (Exception ignored) {
        return fallback
    }
}

private Integer integerValue(value, Integer fallback) {
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
        log.debug "Simple Open-Meteo Outdoor Light Device: ${message}"
    }
}
