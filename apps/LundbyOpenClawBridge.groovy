/**
 * Lundby OpenClaw Bridge - Developer App
 *
 *   Name: Lundby OpenClaw Bridge
 *   Namespace: lundby
 *
 * Lightweight OAuth surface for agent-facing diagnostics and future bridge methods.
 * This app is intentionally not packaged with Simple Home.
 */

import groovy.json.JsonOutput

definition(
    name: "Lundby OpenClaw Bridge",
    namespace: "lundby",
    author: "Erik Lundby",
    description: "Lightweight OAuth bridge for Lundby OpenClaw agent integration.",
    category: "Convenience",
    iconUrl: "https://raw.githubusercontent.com/hrerikl/hubitat-room-state-manager/main/assets/simple-home-dev.png",
    iconX2Url: "https://raw.githubusercontent.com/hrerikl/hubitat-room-state-manager/main/assets/simple-home-dev.png",
    oauth: true,
    singleInstance: true
)

preferences {
    page(name: "mainPage", title: "Lundby OpenClaw Bridge", install: true, uninstall: true) {
        section("Bridge") {
            paragraph "Agent-facing OAuth bridge. Keep this app narrow: diagnostics and helper surfaces, not core Simple Home behavior."
            paragraph "Local status endpoint: ${statusEndpointUrl()}"
            paragraph "Cloud status endpoint: ${cloudStatusEndpointUrl()}"
        }

        section("Debug") {
            input "debugLogging", "bool", title: "Enable debug logging", defaultValue: true, required: true
        }
    }
}

mappings {
    path("/status") {
        action: [
            GET : "apiStatus",
            POST: "apiStatus"
        ]
    }
}

def installed() {
    log.info "Installed ${app.label}"
    initialize()
}

def updated() {
    log.info "Updated ${app.label}"
    initialize()
}

def initialize() {
    ensureAccessToken()
}

def appButtonHandler(String buttonName) {
    debug "Ignoring app button ${buttonName}"
}

def apiStatus() {
    Map result = [
        success  : true,
        app      : [
            id       : app.id?.toString(),
            label    : app.label?.toString(),
            name     : app.name?.toString(),
            namespace: "lundby"
        ],
        version  : "0.1.0",
        timestamp: timestamp(),
        message  : "Lundby OpenClaw Bridge is available."
    ]
    render contentType: "application/json", data: JsonOutput.toJson(result)
}

private String statusEndpointUrl() {
    return "${apiBaseUrl()}/status?access_token=${accessTokenText()}"
}

private String cloudStatusEndpointUrl() {
    return "${getApiServerUrl()}/${app.id}/status?access_token=${accessTokenText()}"
}

private String apiBaseUrl() {
    return getFullLocalApiServerUrl()
}

private String accessTokenText() {
    return state.accessToken ?: "enable-oauth-and-save"
}

private void ensureAccessToken() {
    try {
        if (!state.accessToken) {
            createAccessToken()
        }
    } catch (e) {
        debug "Could not create OAuth token yet: ${e.message}"
    }
}

private String timestamp() {
    return new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone)
}

private void debug(String message) {
    if (debugLogging == true) {
        log.debug "${app.label}: ${message}"
    }
}
