/**
 * Simple Room State Manager v2 - Parent App
 *
 * Install as Apps Code:
 *   Name: Simple Room State Manager v2
 *   Namespace: lundby
 *
 * Provides parent container plus one-time setup helper for reciprocal neighbors.
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
    def sourceChild = childApps?.find { "${it.id}" == "${sourceChildAppId}" }

    if (!sourceChild) {
        log.warn "Simple Room State Manager v2: Reciprocal neighbor setup failed. Could not find source child app ${sourceChildAppId}."
        return
    }

    String sourceRoomDeviceId = sourceChild.getManagedRoomDeviceId()
    String sourceRoomLabel = sourceChild.getManagedRoomDeviceLabel()
    List selectedNeighborIds = sourceChild.getSelectedNeighborRoomDeviceIds() ?: []

    if (!sourceRoomDeviceId) {
        log.warn "${sourceChild.label}: Cannot add reciprocal neighbors because this room's meta-device was not found."
        return
    }

    if (!selectedNeighborIds) {
        log.info "${sourceChild.label}: No selected neighbor rooms to update."
        return
    }

    Integer changed = 0
    Integer matched = 0

    childApps?.each { targetChild ->
        if ("${targetChild.id}" == "${sourceChildAppId}") {
            return
        }

        String targetRoomDeviceId = targetChild.getManagedRoomDeviceId()
        if (targetRoomDeviceId && selectedNeighborIds.contains(targetRoomDeviceId.toString())) {
            matched++
            Boolean added = targetChild.addNeighborRoomDeviceById(sourceRoomDeviceId)
            if (added) {
                changed++
                log.info "${sourceChild.label}: Added ${sourceRoomLabel} as reciprocal neighbor to ${targetChild.label}."
            } else {
                log.info "${sourceChild.label}: ${sourceRoomLabel} was already a neighbor of ${targetChild.label}, or could not be added."
            }
        }
    }

    if (matched == 0) {
        log.warn "${sourceChild.label}: No child app matched the selected neighbor room devices. Make sure selected neighbors are Room meta-devices created by Simple Room State Child v2."
    } else {
        log.info "${sourceChild.label}: Reciprocal neighbor setup complete. Matched ${matched} selected neighbor room(s), updated ${changed}."
    }
}

