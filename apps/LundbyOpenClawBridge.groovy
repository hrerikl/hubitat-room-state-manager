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
            paragraph endpointIndexText()
        }

        section("Debug") {
            input "debugLogging", "bool", title: "Enable debug logging", defaultValue: true, required: true
        }
    }
}

mappings {
    path("/endpoints") {
        action: [
            GET : "apiEndpoints",
            POST: "apiEndpoints"
        ]
    }

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

def apiEndpoints() {
    render contentType: "application/json", data: JsonOutput.toJson([
        success  : true,
        timestamp: timestamp(),
        endpoints: endpointPayload()
    ])
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

private String endpointIndexText() {
    List rows = exposedEndpoints().collect { endpoint ->
        return "${endpoint.name} (${endpoint.method})\nPath: ${endpointLocalPath(endpoint.path)}\nLocal: ${endpointUrl(endpoint.path, false)}\nCloud: ${endpointUrl(endpoint.path, true)}"
    }
    return "Endpoints:\n${rows.join('\n\n')}"
}

private List<Map> endpointPayload() {
    return exposedEndpoints().collect { endpoint ->
        return [
            name       : endpoint.name,
            method     : endpoint.method,
            path       : endpoint.path,
            localPath  : endpointLocalPath(endpoint.path),
            description: endpoint.description,
            localUrl   : endpointUrl(endpoint.path, false),
            cloudUrl   : endpointUrl(endpoint.path, true)
        ]
    }
}

private List<Map> exposedEndpoints() {
    return [
        [
            name       : "Endpoints",
            method     : "GET",
            path       : "/endpoints",
            description: "List OpenClaw Bridge endpoints."
        ],
        [
            name       : "Status",
            method     : "GET",
            path       : "/status",
            description: "Return bridge availability and version."
        ]
    ]
}

private String endpointUrl(String path, Boolean cloud) {
    String normalizedPath = path?.startsWith("/") ? path : "/${path ?: ''}"
    String base = cloud ? cloudApiBaseUrl() : localApiBaseUrl()
    return "${base}${normalizedPath}?access_token=${accessTokenText()}"
}

private String endpointLocalPath(String path) {
    String normalizedPath = path?.startsWith("/") ? path : "/${path ?: ''}"
    return "/apps/api/${app.id}${normalizedPath}?access_token=${accessTokenText()}"
}

private String localApiBaseUrl() {
    return getFullLocalApiServerUrl()
}

private String cloudApiBaseUrl() {
    return getFullApiServerUrl()
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
