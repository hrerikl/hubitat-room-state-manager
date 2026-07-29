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
        attribute 'lastFetchStatus', 'string'
        attribute 'dataSource', 'string'
    }
}

preferences {
    input 'latitude', 'text', title: 'Latitude', defaultValue: "${location?.latitude ?: ''}", required: true
    input 'longitude', 'text', title: 'Longitude', defaultValue: "${location?.longitude ?: ''}", required: true
    input 'pollIntervalMinutes', 'enum', title: 'Poll interval', options: ['5', '10', '15', '30', '60'], defaultValue: '10', required: true
    input 'valleyFilterStrength', 'number', title: 'Valley/shade low-sun filter strength percent', defaultValue: 0, required: true
    input 'cloudFilterStrength', 'number', title: 'Additional cloudy low-sun filter strength percent', defaultValue: 40, required: true
    input 'lowSunWindowMinutes', 'number', title: 'Low-sun filter window minutes after sunrise and before sunset', defaultValue: 120, required: true
    input 'maxCloudCtBoost', 'number', title: 'Maximum cloud color-temperature boost', defaultValue: 200, required: true
    input 'ctRoundingStep', 'enum', title: 'Round color temperature to nearest', options: ['1', '50', '100', '200'], defaultValue: '100', required: true
    input 'staleDataMinutes', 'number', title: 'Minutes before Open-Meteo data is stale and local fallback is published. Blank or 0 disables.', defaultValue: 60, required: false
    input 'debugLogging', 'bool', title: 'Enable debug logging', defaultValue: true, required: true
}

void installed() {
    initialize()
    refresh()
}

void updated() {
    unschedule()
    initialize()
}

void initialize() {
    ensureInitialState()
    schedulePolling()
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
            daily        : 'sunrise,sunset',
            timezone     : 'auto',
            forecast_days: 1
        ],
        timeout: 20
    ]

    try {
        asynchttpGet('parseMeteoResponse', params)
    } catch (Exception e) {
        log.warn "Simple Open-Meteo Outdoor Light Device: refresh failed: ${e.message}"
        handleFetchFailure("refresh failed: ${e.message}")
    }
}

void parseMeteoResponse(resp, data) {
    try {
        Integer status = integerValue(resp?.getStatus(), 0)
        if (status != 200) {
            handleFetchFailure("Open-Meteo status ${status}")
            return
        }

        Map responseData = resp?.json ?: resp?.data ?: [:]
        Map current = responseData?.current ?: [:]
        BigDecimal cloud = decimalValue(current.cloud_cover, 0G)
        BigDecimal radiation = decimalValue(current.shortwave_radiation, 0G)
        Integer code = integerValue(current.weather_code, 0)
        Date sunrise = localDateTime(responseData?.daily?.sunrise?.getAt(0))
        Date sunset = localDateTime(responseData?.daily?.sunset?.getAt(0))
        Integer lux = calculateOutdoorLux(radiation, cloud, sunrise, sunset)
        Integer ct = calculateOutdoorCT(lux, cloud, code, sunrise, sunset)
        String condition = skyCondition(code, cloud)
        String updated = new Date().format('yyyy-MM-dd HH:mm:ss', location.timeZone)

        publishOutdoorLight(lux, ct, cloud, radiation, code, condition, updated, 'Open-Meteo', 'ok')
        state.lastSuccessfulFetchAt = now()
        state.lastSunrise = sunrise?.time
        state.lastSunset = sunset?.time

        debug "Open-Meteo updated: lux=${lux}, ct=${ct}, cloud=${cloud}%, radiation=${radiation}, condition=${condition}, sunrise=${sunrise}, sunset=${sunset}"
    } catch (Exception e) {
        log.warn "Simple Open-Meteo Outdoor Light Device: async response failed: ${e.message}"
        handleFetchFailure("response failed: ${e.message}")
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
    if (device.currentValue('lastFetchStatus') == null) sendEvent(name: 'lastFetchStatus', value: 'Never')
    if (device.currentValue('dataSource') == null) sendEvent(name: 'dataSource', value: 'Initial')
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

private void publishOutdoorLight(Integer lux, Integer ct, BigDecimal cloud, BigDecimal radiation, Integer code, String condition, String updated, String source, String status) {
    publishValue('illuminance', lux as Integer, 'lx')
    publishValue('outdoorLux', lux as Integer, 'lx')
    publishValue('outdoorCT', ct as Integer, 'K')
    publishValue('cloudCover', cloud.setScale(0, BigDecimal.ROUND_HALF_UP) as Integer, '%')
    publishValue('solarRadiation', radiation.setScale(1, BigDecimal.ROUND_HALF_UP), 'W/m2')
    publishValue('weatherCode', code as Integer, null)
    publishValue('skyCondition', condition, null)
    publishValue('lastUpdated', updated, null)
    publishValue('dataSource', source, null)
    publishValue('lastFetchStatus', status, null)
}

private void handleFetchFailure(String status) {
    publishValue('lastFetchStatus', status, null)
    if (!staleFallbackEnabled()) {
        debug "Open-Meteo failure; keeping last values because stale fallback is disabled: ${status}"
        return
    }

    Long lastSuccess = longValue(state.lastSuccessfulFetchAt, 0L)
    Long staleMs = staleDataMinutesValue() * 60000L
    if (lastSuccess && now() - lastSuccess < staleMs) {
        debug "Open-Meteo failure; keeping recent values: ${status}"
        return
    }

    Long sunriseMs = anchorStoredTimeToToday(state.lastSunrise)
    Long sunsetMs = anchorStoredTimeToToday(state.lastSunset)
    BigDecimal daylight = daylightCurve(now(), sunriseMs, sunsetMs)
    Integer lux = clampInteger((daylight * 70000G).setScale(0, BigDecimal.ROUND_HALF_UP) as Integer, 0, 70000)
    Integer ct = roundToStep(clampInteger((2200G + (4300G * daylight)).setScale(0, BigDecimal.ROUND_HALF_UP) as Integer, 2200, 6500), ctStep())
    String updated = new Date().format('yyyy-MM-dd HH:mm:ss', location.timeZone)

    publishOutdoorLight(lux, ct, 0G, 0G, 0, 'Local Fallback', updated, 'Local Fallback', status)
    debug "Published local fallback after stale Open-Meteo data: lux=${lux}, ct=${ct}, status=${status}"
}

// Stored sunrise/sunset come from the last successful fetch; after an outage
// crosses midnight their dates are in the past and daylightCurve would return
// 0 all day (perpetual night). Re-anchor the stored time-of-day onto today.
private Long anchorStoredTimeToToday(def storedMs) {
    Long stored = longValue(storedMs, 0L)
    if (!stored) return null

    Calendar storedCal = Calendar.getInstance(location.timeZone)
    storedCal.setTimeInMillis(stored)
    Calendar today = Calendar.getInstance(location.timeZone)
    today.set(Calendar.HOUR_OF_DAY, storedCal.get(Calendar.HOUR_OF_DAY))
    today.set(Calendar.MINUTE, storedCal.get(Calendar.MINUTE))
    today.set(Calendar.SECOND, storedCal.get(Calendar.SECOND))
    today.set(Calendar.MILLISECOND, 0)
    return today.getTimeInMillis()
}

private Integer calculateOutdoorLux(BigDecimal radiation, BigDecimal cloud, Date sunrise, Date sunset) {
    BigDecimal safeRadiation = (radiation ?: 0G) < 0G ? 0G : (radiation ?: 0G)
    BigDecimal lux = safeRadiation * 120G * valleyLuxMultiplier(now(), cloud, adjustedTime(sunrise, 0), adjustedTime(sunset, 0))
    return clampInteger(lux.setScale(0, BigDecimal.ROUND_HALF_UP) as Integer, 0, 120000)
}

private Integer calculateOutdoorCT(Integer lux, BigDecimal cloud, Integer weatherCode, Date sunrise, Date sunset) {
    if ((lux ?: 0) < 50) return 2200

    BigDecimal daylight = daylightCurve(now(), adjustedTime(sunrise, 0), adjustedTime(sunset, 0))
    BigDecimal cloudBoost = clampDecimal(cloud ?: 0G, 0G, 100G) * (maxCloudBoost() as BigDecimal) / 100G
    BigDecimal ct = 2200G + (4300G * daylight) + cloudBoost + (weatherCodeCtBoost(weatherCode) as BigDecimal)

    return roundToStep(clampInteger(ct.setScale(0, BigDecimal.ROUND_HALF_UP) as Integer, 2200, 7000), ctStep())
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

private Date localDateTime(value) {
    if (value == null) return null

    try {
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm")
        format.setTimeZone(location.timeZone)
        return format.parse(value.toString())
    } catch (Exception ignored) {
        return null
    }
}

private Long adjustedTime(Date date, value) {
    if (!date) return null
    Integer minutes = integerValue(value, 0)
    return date.time + (minutes * 60000L)
}

private BigDecimal daylightCurve(Long currentMs, Long sunriseMs, Long sunsetMs) {
    if (!currentMs || !sunriseMs || !sunsetMs || sunsetMs <= sunriseMs) {
        return fallbackDaylightCurve()
    }
    if (currentMs <= sunriseMs || currentMs >= sunsetMs) return 0G

    BigDecimal progress = ((currentMs - sunriseMs) as BigDecimal) / ((sunsetMs - sunriseMs) as BigDecimal)
    return Math.sin(Math.PI * (progress as Double)) as BigDecimal
}

private BigDecimal valleyLuxMultiplier(Long currentMs, BigDecimal cloud, Long sunriseMs, Long sunsetMs) {
    BigDecimal lowSun = lowSunWeight(currentMs, sunriseMs, sunsetMs)
    if (lowSun <= 0G) return 1G

    BigDecimal cloudRatio = clampDecimal(cloud ?: 0G, 0G, 100G) / 100G
    BigDecimal valleyStrength = clampDecimal(integerValue(valleyFilterStrength, 0) as BigDecimal, 0G, 100G)
    BigDecimal cloudStrength = clampDecimal(integerValue(cloudFilterStrength, 40) as BigDecimal, 0G, 100G)
    BigDecimal attenuation = lowSun * (valleyStrength + (cloudStrength * cloudRatio)) / 100G
    return clampDecimal(1G - attenuation, 0.1G, 1G)
}

private BigDecimal lowSunWeight(Long currentMs, Long sunriseMs, Long sunsetMs) {
    Integer windowMinutes = clampInteger(integerValue(lowSunWindowMinutes, 120), 1, 360)
    Long windowMs = windowMinutes * 60000L

    if (!currentMs || !sunriseMs || !sunsetMs || sunsetMs <= sunriseMs) return 0G

    if (currentMs >= sunriseMs && currentMs <= sunriseMs + windowMs) {
        return 1G - (((currentMs - sunriseMs) as BigDecimal) / (windowMs as BigDecimal))
    }
    if (currentMs <= sunsetMs && currentMs >= sunsetMs - windowMs) {
        return 1G - (((sunsetMs - currentMs) as BigDecimal) / (windowMs as BigDecimal))
    }
    return 0G
}

private BigDecimal fallbackDaylightCurve() {
    Calendar cal = Calendar.getInstance(location.timeZone)
    BigDecimal hour = (cal.get(Calendar.HOUR_OF_DAY) as BigDecimal) + ((cal.get(Calendar.MINUTE) as BigDecimal) / 60G)
    BigDecimal dayProgress = clampDecimal((hour - 6G) / 12G, 0G, 1G)
    return Math.sin(Math.PI * (dayProgress as Double)) as BigDecimal
}

private Integer maxCloudBoost() {
    return clampInteger(integerValue(maxCloudCtBoost, 200), 0, 1000)
}

private Integer weatherCodeCtBoost(Integer code) {
    Integer safeCode = code ?: 0
    if (safeCode >= 95) return 350
    if (safeCode >= 71 && safeCode <= 86) return 300
    if (safeCode >= 51 && safeCode <= 67) return 200
    if ([45, 48].contains(safeCode)) return 150
    return 0
}

private Integer ctStep() {
    return clampInteger(integerValue(ctRoundingStep, 100), 1, 500)
}

private Boolean staleFallbackEnabled() {
    return staleDataMinutesValue() > 0
}

private Integer staleDataMinutesValue() {
    return clampInteger(integerValue(staleDataMinutes, 60), 0, 1440)
}

private Integer roundToStep(Integer value, Integer step) {
    Integer safeStep = Math.max(step ?: 1, 1)
    Integer safeValue = value ?: 0
    Integer rounded = Math.round(safeValue / safeStep) as Integer
    return rounded * safeStep
}

private Integer clampInteger(Integer value, Integer min, Integer max) {
    return Math.max(Math.min(value ?: min, max), min)
}

private Long longValue(value, Long fallback) {
    try {
        return value == null ? fallback : value as Long
    } catch (Exception ignored) {
        return fallback
    }
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
