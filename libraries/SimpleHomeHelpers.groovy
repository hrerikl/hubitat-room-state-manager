/* groovylint-disable IfStatementBraces, UnusedPrivateMethod */
library(
    base: "app",
    author: "Erik Lundby / ChatGPT",
    category: "Convenience",
    description: "Shared helper methods for Simple Home apps.",
    name: "SimpleHomeHelpers",
    namespace: "lundby",
    documentationLink: ""
)

private List asList(def value) {
    if (!value) return []
    return value instanceof List ? value : [value]
}

private Integer safeInteger(value, Integer fallback) {
    try {
        return value == null ? fallback : value as Integer
    } catch (Exception ignored) {
        return fallback
    }
}

private Long safeLong(value, Long fallback) {
    try {
        return value == null ? fallback : value as Long
    } catch (Exception ignored) {
        return fallback
    }
}

private Integer normalizedLevel(value) {
    return normalizedLevel(value, 50)
}

private Integer normalizedLevel(value, Integer fallback) {
    Integer level = fallback == null ? 0 : fallback
    try {
        level = value == null ? level : value as Integer
    } catch (Exception ignored) {
        level = fallback == null ? 0 : fallback
    }
    return Math.max(Math.min(level, 100), 0)
}

private Integer normalizedPercent(value, Integer fallback) {
    Integer percent = fallback
    try {
        percent = (value == null ? fallback : value) as Integer
    } catch (Exception ignored) {
        percent = fallback
    }
    return Math.max(Math.min(percent, 100), 0)
}

private Integer normalizedOffset(value, Integer fallback) {
    Integer offset = fallback
    try {
        offset = (value == null ? fallback : value) as Integer
    } catch (Exception ignored) {
        offset = fallback
    }
    return Math.max(Math.min(offset, 100), -100)
}

private Integer normalizedColorTemperature(value) {
    return normalizedColorTemperature(value, 2700)
}

private Integer normalizedColorTemperature(value, Integer fallback) {
    Integer ct = fallback == null ? 2700 : fallback
    try {
        ct = (value == null ? ct : value) as Integer
    } catch (Exception ignored) {
        ct = fallback == null ? 2700 : fallback
    }
    return Math.max(Math.min(ct, 10000), 1500)
}

private Boolean sameDevice(def first, def second) {
    if (!first || !second) return false
    return first.id?.toString() == second.id?.toString()
}

private Integer eventIntegerValue(evt) {
    try {
        return evt.value as Integer
    } catch (Exception ignored) {
        return null
    }
}

private String eventType(evt) {
    try {
        return evt.type?.toString() ?: ""
    } catch (Throwable ignored) {
        return ""
    }
}

private List normalizeIdList(def rawIds) {
    if (!rawIds) return []

    List ids = rawIds instanceof List ? rawIds : [rawIds]
    return ids
        .collectMany { raw ->
            String text = "${raw}".trim()
            if (text.startsWith("[") && text.endsWith("]")) {
                return text.substring(1, text.length() - 1).split(",").collect { it.trim() }
            }
            return [text]
        }
        .findAll { it }
        .unique()
}
