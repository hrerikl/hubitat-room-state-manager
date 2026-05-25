package lundby

class Util {
    static List asList(def value) {
        if (!value) return []
        return value instanceof List ? value : [value]
    }

    static Integer safeInteger(value, Integer fallback) {
        try {
            return value == null ? fallback : value as Integer
        } catch (Exception ignored) {
            return fallback
        }
    }

    static Long safeLong(value, Long fallback) {
        try {
            return value == null ? fallback : value as Long
        } catch (Exception ignored) {
            return fallback
        }
    }

    static Integer normalizedPercent(value, Integer fallback) {
        Integer percent = fallback
        try {
            percent = (value == null ? fallback : value) as Integer
        } catch (Exception ignored) {
            percent = fallback
        }
        return Math.max(Math.min(percent, 100), 0)
    }

    static Integer normalizedOffset(value, Integer fallback) {
        Integer offset = fallback
        try {
            offset = (value == null ? fallback : value) as Integer
        } catch (Exception ignored) {
            offset = fallback
        }
        return Math.max(Math.min(offset, 100), -100)
    }

    static Integer normalizedColorTemperature(value, Integer fallback) {
        Integer ct = fallback
        try {
            ct = (value == null ? fallback : value) as Integer
        } catch (Exception ignored) {
            ct = fallback
        }
        return Math.max(Math.min(ct, 10000), 1500)
    }

    static Integer positiveSeconds(value, Integer defaultSeconds) {
        Integer seconds = value ? value as Integer : defaultSeconds
        return seconds > 0 ? seconds : 1
    }

    static Integer minutesRoundedUp(Integer seconds) {
        Integer safeSeconds = positiveSeconds(seconds, 60)
        Integer wholeMinutes = (safeSeconds / 60) as Integer
        return safeSeconds % 60 == 0 ? wholeMinutes : wholeMinutes + 1
    }

    static Integer eventIntegerValue(evt) {
        try {
            return evt.value as Integer
        } catch (Exception ignored) {
            return null
        }
    }

    static String eventType(evt) {
        try {
            return evt.type?.toString() ?: ""
        } catch (Throwable ignored) {
            return ""
        }
    }
}
