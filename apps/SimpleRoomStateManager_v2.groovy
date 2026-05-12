/**
 * Simple Room State Manager v2 - Parent App
 *
 * Install as Apps Code:
 *   Name: Simple Room State Manager v2
 *   Namespace: lundby
 *
 * Provides parent container plus one-time setup helper for reciprocal neighbors. Neighbor relationships are stored by child app ID.
 */

definition(
    name: "Simple Room State Manager v2",
    namespace: "lundby",
    author: "Erik Lundby / ChatGPT",
    description: "Parent app for lightweight room state child apps.",
    category: "Convenience",
    singleInstance: true,
    iconUrl: "",
    iconX2Url: ""
)

preferences {
    page(name: "mainPage", title: "Simple Room State Manager v2", install: true, uninstall: true) {
        section("Rooms") {
            app(
                name: "childApps",
                appName: "Simple Room State Child v2",
                namespace: "lundby",
                title: "Add a room",
                multiple: true
            )
        }

        section("House mode") {
            app(
                name: "modeApps",
                appName: "Simple Mode Manager v2",
                namespace: "lundby",
                title: "Add mode manager",
                multiple: true
            )
        }
    }
}

def installed() {
    log.info "Installed Simple Room State Manager v2"
    initialize()
}

def updated() {
    log.info "Updated Simple Room State Manager v2"
    initialize()
}

def initialize() {
    // Parent currently has no persistent shared room graph.
}

/**
 * One-time setup helper.
 *
 * Called by a child app when the user presses:
 *   "Add this room back to selected neighbors"
 *
 * Example:
 *   Utility selects Media Room as a neighbor.
 *   Pressing the button in Utility asks the parent to add Utility's Room device
 *   to Media Room's neighbor list once.
 *
 * After this runs, each child app continues to own its own neighbor list.
 */
def addThisRoomToSelectedNeighbors(sourceChildAppId) {
    def sourceChild = childApps?.find { childAppId(it) == "${sourceChildAppId}" }

    if (!sourceChild) {
        log.warn "Simple Room State Manager v2: Reciprocal neighbor setup failed. Could not find source child app ${sourceChildAppId}."
        return
    }

    String sourceRoomLabel = sourceChild.getManagedRoomDeviceLabel()
    List selectedNeighborChildIds = normalizeIdList(sourceChild.getSelectedNeighborChildAppIds())

    if (!selectedNeighborChildIds) {
        log.info "${sourceChild.label}: No selected neighbor rooms to update."
        return
    }

    Integer changed = 0
    Integer matched = 0

    childApps?.each { targetChild ->
        String targetChildAppId = childAppId(targetChild)

        if (targetChildAppId == "${sourceChildAppId}") {
            return
        }

        if (selectedNeighborChildIds.contains(targetChildAppId)) {
            matched++
            Boolean added = targetChild.addNeighborChildAppId(sourceChildAppId.toString())
            if (added) {
                changed++
                log.info "${sourceChild.label}: Added ${sourceRoomLabel} as reciprocal neighbor to ${targetChild.label}."
            } else {
                log.info "${sourceChild.label}: ${sourceRoomLabel} was already a neighbor of ${targetChild.label}, or could not be added."
            }
        }
    }

    if (matched == 0) {
        log.warn "${sourceChild.label}: No child app matched the selected neighbor room IDs."
    } else {
        log.info "${sourceChild.label}: Reciprocal neighbor setup complete. Matched ${matched} selected neighbor room(s), updated ${changed}."
    }
}

// -------------------- Room Child Registry --------------------

Map neighborRoomOptions(def requestingChildAppId) {
    try {
        Map opts = [:]
        childApps?.each { child ->
            String id = child?.id?.toString()
            if (id == "${requestingChildAppId}") return

            try {
                String label = child.getManagedRoomDeviceLabel()
                if (id && label) {
                    opts[(id)] = label
                }
            } catch (Throwable ignored) {
                // Ignore non-room child apps.
            }
        }
        return opts.sort { it.value }
    } catch (Exception e) {
        log.warn "Simple Room State Manager v2: Could not build neighbor room options: ${e.message}"
        return [:]
    }
}

Map roomStateChildOptions(def requestingChildAppId) {
    return neighborRoomOptions(requestingChildAppId)
}

Map roomStateChildInfo(def childAppId) {
    def child = childApps?.find { it?.id?.toString() == "${childAppId}" }
    if (!child) return [:]

    try {
        return [
            id           : child.id?.toString(),
            label        : child.getManagedRoomDeviceLabel(),
            hubitatRoomId: child.getHubitatRoomId(),
            hubitatRoom  : child.getHubitatRoomName(),
            roomName     : child.getConfiguredRoomName()
        ]
    } catch (Throwable ignored) {
        return [:]
    }
}

def roomStateChildRoomDevice(def childAppId) {
    def child = childApps?.find { it?.id?.toString() == "${childAppId}" }
    if (!child) return null

    try {
        return child.getManagedRoomDevice()
    } catch (Throwable ignored) {
        return null
    }
}

List neighborRoomDevicesForChildIds(def selectedChildIds) {
    List ids = normalizeIdList(selectedChildIds)
    if (!ids) return []

    try {
        List allChildren = childApps ?: []
        List matchedChildren = allChildren.findAll { child -> ids.contains(child?.id?.toString()) }
        List devices = matchedChildren.collect { child ->
                try {
                    child.getManagedRoomDevice()
                } catch (Throwable ignored) {
                    null
                }
            }.findAll { it != null }

        log.debug "Simple Room State Manager v2: neighbor child IDs=${ids.join(', ')}, available children=${allChildren.collect { it?.id }.join(', ') ?: 'none'}, matched children=${matchedChildren.collect { it?.id }.join(', ') ?: 'none'}, resolved devices=${devices*.displayName?.join(', ') ?: 'none'}"
        return devices
    } catch (Exception e) {
        log.warn "Simple Room State Manager v2: Could not resolve neighbor room devices: ${e.message}"
        return []
    }
}

private String childAppId(def child) {
    if (!child) return null

    return child.id?.toString()
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
