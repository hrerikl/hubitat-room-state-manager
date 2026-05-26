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

private Boolean isPhysicalEvent(evt) {
    return eventType(evt) == "physical"
}

private Boolean isDigitalEvent(evt) {
    return eventType(evt) == "digital"
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

private Boolean debugEnabled(Boolean localDebugSetting) {
    try {
        if (parent) return parent.simpleHomeDebugEnabled(localDebugSetting == true) == true
    } catch (Throwable ignored) {
        // Fall through to the child app's local setting.
    }
    return localDebugSetting == true
}

def getSimpleHomeAppName() {
    try {
        return app?.name?.toString()
    } catch (Throwable ignored) {
        return null
    }
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

private String currentLocationModeName() {
    try {
        return location?.mode?.toString()
    } catch (Exception ignored) {
        return null
    }
}

private String formatTime(Long epochMs) {
    if (!epochMs) return "unknown"
    return new Date(epochMs).format("yyyy-MM-dd HH:mm:ss", location.timeZone)
}

private Integer minutesRoundedUp(Integer seconds) {
    return Math.max(Math.ceil((seconds ?: 0) / 60.0) as Integer, 1)
}

private String htmlEscape(value) {
    return "${value ?: ''}"
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
        .replace("'", "&#39;")
}
