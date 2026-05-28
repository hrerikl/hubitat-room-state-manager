/**
 * Simple Home Dev - Developer App
 *
 *   Name: Simple Home Dev
 *   Namespace: lundby
 *
 * Developer-only update surface for a Simple Home development hub.
 */

import groovy.json.JsonOutput
import java.net.URLEncoder

definition(
    name: "Simple Home Dev",
    namespace: "lundby",
    author: "Erik Lundby",
    description: "Developer-only Simple Home update and diagnostics surface.",
    category: "Convenience",
    importUrl: "https://raw.githubusercontent.com/hrerikl/hubitat-room-state-manager/main/apps/SimpleHomeDev.groovy",
    iconUrl: "https://raw.githubusercontent.com/hrerikl/hubitat-room-state-manager/main/assets/simple-home-dev.png",
    iconX2Url: "https://raw.githubusercontent.com/hrerikl/hubitat-room-state-manager/main/assets/simple-home-dev.png",
    oauth: true,
    singleInstance: true
)

preferences {
    page(name: "mainPage", title: "Simple Home Dev", install: true, uninstall: true) {
        section("Warning") {
            paragraph "Developer tool. Use on a dev hub or after removing Simple Home from HPM management. Mixing HPM stable updates with SimpleHomeDev dev-branch updates may overwrite code unexpectedly."
        }

        section("Update") {
            input "manifestUrl", "text", title: "Simple Home package manifest URL", defaultValue: "https://raw.githubusercontent.com/hrerikl/hubitat-room-state-manager/main/packageManifest.json", required: true
            input "bundleUrl", "text", title: "Simple Home bundle ZIP URL", defaultValue: "https://github.com/hrerikl/hubitat-room-state-manager/releases/latest/download/SimpleHome.zip", required: true
            input "updateNow", "button", title: "Update Simple Home"
            input "retryAttempts", "number", title: "Bundle availability attempts", defaultValue: 5, required: true
            input "retryDelaySeconds", "number", title: "Seconds between availability attempts", defaultValue: 10, required: true
        }

        section("Self Update") {
            input "devAppSourceUrl", "text", title: "Simple Home Dev source URL", defaultValue: "https://raw.githubusercontent.com/hrerikl/hubitat-room-state-manager/main/apps/SimpleHomeDev.groovy", required: true
            input "devAppCodeId", "text", title: "Simple Home Dev App Code ID", required: false
            input "updateDevAppNow", "button", title: "Update Simple Home Dev"
            paragraph "Self-update requires the Apps Code ID for this app, not the installed app instance ID."
        }

        section("Hub Access") {
            input "sslEnabled", "bool", title: "Hub SSL enabled", defaultValue: false, required: true
            input "hubSecurity", "bool", title: "Hub security enabled", defaultValue: false, required: true, submitOnChange: true
            if (hubSecurity) {
                input "hubUsername", "string", title: "Hub security username", required: true
                input "hubPassword", "password", title: "Hub security password", required: true
            }
        }

        section("Automation") {
            paragraph "Endpoint: ${updateEndpointUrl()}"
        }

        section("Status") {
            paragraph "Last manifest: ${state.lastManifestPackageName ?: 'Unknown'} ${state.lastManifestVersion ?: ''}"
            paragraph state.lastUpdateStatus ?: "No update run yet."
            paragraph state.lastUpdateDetail ?: ""
        }

        section("Debug") {
            input "debugLogging", "bool", title: "Enable debug logging", defaultValue: true, required: true
        }
    }
}

mappings {
    path("/update") {
        action: [
            GET : "apiUpdate",
            POST: "apiUpdate"
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
    if (buttonName == "updateNow") {
        Map result = updateSimpleHome("app button")
        log.info "${app.label}: ${result.message}"
    }
    if (buttonName == "updateDevAppNow") {
        Map result = updateSimpleHomeDev("app button")
        log.info "${app.label}: ${result.message}"
    }
}

def apiUpdate() {
    Map result = updateSimpleHome("api")
    render contentType: "application/json", data: JsonOutput.toJson(result)
}

private Map updateSimpleHome(String reason) {
    String url = bundleUrl?.toString()?.trim()
    if (!url) {
        return updateResult(false, "No bundle URL configured.", reason)
    }

    Map manifest = loadPackageManifest()
    if (!manifest) {
        return updateResult(false, "Package manifest was not available after ${availabilityAttempts()} attempts.", reason)
    }

    if (!login()) {
        return updateResult(false, "Hub security login failed.", reason)
    }

    Boolean available = waitForBundleAvailability(url)
    if (!available) {
        return updateResult(false, "Bundle URL was not available after ${availabilityAttempts()} attempts.", reason)
    }

    Boolean installed = installBundle(url)
    if (!installed) {
        return updateResult(false, "Bundle install failed.", reason)
    }

    return updateResult(true, "Simple Home bundle update completed.", reason)
}

private Map updateSimpleHomeDev(String reason) {
    String codeId = devAppCodeId?.toString()?.trim()
    if (!codeId) {
        return updateResult(false, "Simple Home Dev App Code ID is not configured.", reason)
    }

    String sourceUrl = devAppSourceUrl?.toString()?.trim()
    if (!sourceUrl) {
        return updateResult(false, "Simple Home Dev source URL is not configured.", reason)
    }

    String source = loadTextWithRetry(sourceUrl, "Simple Home Dev source")
    if (!source) {
        return updateResult(false, "Simple Home Dev source was not available after ${availabilityAttempts()} attempts.", reason)
    }

    if (!login()) {
        return updateResult(false, "Hub security login failed.", reason)
    }

    Boolean updated = updateAppCode(codeId, source)
    if (!updated) {
        return updateResult(false, "Simple Home Dev app code update failed.", reason)
    }

    return updateResult(true, "Simple Home Dev app code updated. Reopen the app before continuing.", reason)
}

private Boolean waitForBundleAvailability(String url) {
    Integer attempts = availabilityAttempts()
    Integer delaySeconds = availabilityDelaySeconds()

    for (Integer attempt = 1; attempt <= attempts; attempt++) {
        if (bundleUrlAvailable(url, attempt)) return true
        if (attempt < attempts) {
            debug "Bundle not available yet; retrying in ${delaySeconds} seconds."
            pauseExecution(delaySeconds * 1000)
        }
    }

    return false
}

private Map loadPackageManifest() {
    String url = manifestUrl?.toString()?.trim()
    if (!url) return null

    Integer attempts = availabilityAttempts()
    Integer delaySeconds = availabilityDelaySeconds()

    for (Integer attempt = 1; attempt <= attempts; attempt++) {
        Map manifest = packageManifestAvailable(url, attempt)
        if (manifest) return manifest
        if (attempt < attempts) {
            debug "Package manifest not available yet; retrying in ${delaySeconds} seconds."
            pauseExecution(delaySeconds * 1000)
        }
    }

    return null
}

private String loadTextWithRetry(String url, String description) {
    Integer attempts = availabilityAttempts()
    Integer delaySeconds = availabilityDelaySeconds()

    for (Integer attempt = 1; attempt <= attempts; attempt++) {
        String text = textAvailable(url, description, attempt)
        if (text) return text
        if (attempt < attempts) {
            debug "${description} not available yet; retrying in ${delaySeconds} seconds."
            pauseExecution(delaySeconds * 1000)
        }
    }

    return null
}

private String textAvailable(String url, String description, Integer attempt) {
    try {
        String result = null
        httpGet([uri: url, timeout: 30, textParser: true, ignoreSSLIssues: true]) { resp ->
            if (resp?.status >= 200 && resp?.status < 300) {
                result = resp?.data?.text?.toString()
            }
        }
        if (result) {
            debug "${description} available on attempt ${attempt}: ${url}"
            return result
        }
    } catch (Exception e) {
        debug "${description} check failed on attempt ${attempt}: ${e.message}"
    }

    return null
}

private Map packageManifestAvailable(String url, Integer attempt) {
    try {
        Map result = null
        httpGet([uri: url, timeout: 30, contentType: "application/json", ignoreSSLIssues: true]) { resp ->
            if (resp?.status >= 200 && resp?.status < 300) {
                result = resp?.data as Map
            }
        }
        if (result) {
            state.lastManifestVersion = result.version?.toString()
            state.lastManifestPackageName = result.packageName?.toString()
            debug "Package manifest available on attempt ${attempt}: ${result.packageName} ${result.version}"
            return result
        }
    } catch (Exception e) {
        debug "Package manifest check failed on attempt ${attempt}: ${e.message}"
    }

    return null
}

private Boolean updateAppCode(String codeId, String source) {
    try {
        Boolean result = false
        httpPost([
            uri                 : baseUrl(),
            path                : "/app/ajax/update",
            requestContentType  : "application/x-www-form-urlencoded",
            headers             : ["Connection": "keep-alive", "Cookie": state.cookie],
            body                : [id: codeId, version: appCodeVersion(codeId), source: source],
            timeout             : 420,
            ignoreSSLIssues     : true
        ]) { resp ->
            result = resp?.data?.status == "success"
        }
        return result
    } catch (Exception e) {
        log.warn "${app.label}: App code update failed: ${e.message}"
        return false
    }
}

private String appCodeVersion(String codeId) {
    try {
        String result = ""
        httpGet([
            uri                 : baseUrl(),
            path                : "/app/ajax/code",
            requestContentType  : "application/x-www-form-urlencoded",
            headers             : ["Cookie": state.cookie],
            query               : [id: codeId],
            timeout             : 300,
            ignoreSSLIssues     : true
        ]) { resp ->
            result = resp?.data?.version?.toString() ?: ""
        }
        return result
    } catch (Exception e) {
        log.warn "${app.label}: Could not load app code version for ${codeId}: ${e.message}"
        return ""
    }
}

private Boolean bundleUrlAvailable(String url, Integer attempt) {
    try {
        Integer statusCode = 0
        httpGet([uri: url, timeout: 30, ignoreSSLIssues: true]) { resp ->
            statusCode = safeInteger(resp?.status, 0)
        }
        if (statusCode >= 200 && statusCode < 300) {
            debug "Bundle URL available on attempt ${attempt}: ${url}"
            return true
        }
        debug "Bundle URL returned HTTP ${statusCode} on attempt ${attempt}: ${url}"
    } catch (Exception e) {
        debug "Bundle URL check failed on attempt ${attempt}: ${e.message}"
    }

    return false
}

private Boolean installBundle(String url) {
    if (location.hub.firmwareVersionString >= "2.3.8.108") {
        try {
            String encodedUrl = URLEncoder.encode(url, "UTF-8")
            Boolean result = false
            httpGet([
                uri             : "${baseUrl()}/bundle2/uploadZipFromUrl?url=${encodedUrl}&pwd=&private=false",
                headers         : ["Connection": "keep-alive", "Cookie": state.cookie],
                timeout         : 420,
                ignoreSSLIssues : true
            ]) { resp ->
                result = resp?.data?.success == true
            }
            return result
        } catch (Exception e) {
            log.warn "${app.label}: Bundle install failed: ${e.message}"
            return false
        }
    }

    try {
        Boolean result = false
        httpPost([
            uri             : baseUrl(),
            path            : "/bundle/uploadZipFromUrl",
            headers         : [
                "Accept"     : "*/*",
                "ContentType": "text/plain; charset=utf-8",
                "Connection" : "keep-alive",
                "Cookie"     : state.cookie
            ],
            body            : JsonOutput.toJson([url: url, installer: false, pwd: ""]),
            timeout         : 420,
            ignoreSSLIssues : true
        ]) { resp ->
            result = resp?.status >= 200 && resp?.status < 300
        }
        return result
    } catch (Exception e) {
        log.warn "${app.label}: Legacy bundle install failed: ${e.message}"
        return false
    }
}

private Boolean login() {
    if (hubSecurity != true) {
        state.cookie = ""
        return true
    }

    try {
        Boolean result = false
        httpPost([
            uri             : baseUrl(),
            path            : "/login",
            query           : [loginRedirect: "/"],
            body            : [username: hubUsername, password: hubPassword, submit: "Login"],
            textParser      : true,
            ignoreSSLIssues : true
        ]) { resp ->
            String bodyText = resp?.data?.text?.toString() ?: ""
            if (bodyText.contains("The login information you supplied was incorrect.")) {
                result = false
            } else {
                state.cookie = resp?.headers?.'Set-Cookie'?.split(";")?.getAt(0) ?: ""
                result = true
            }
        }
        return result
    } catch (Exception e) {
        log.warn "${app.label}: Hub security login failed: ${e.message}"
        return false
    }
}

private String baseUrl() {
    return sslEnabled == true ? "https://127.0.0.1:8443" : "http://127.0.0.1:8080"
}

private String updateEndpointUrl() {
    ensureAccessToken()
    String token = state.accessToken ?: "enable-oauth-and-save"
    return "${getFullLocalApiServerUrl()}/update?access_token=${token}"
}

private void ensureAccessToken() {
    try {
        if (!state.accessToken) {
            createAccessToken()
        }
    } catch (Throwable e) {
        debug "Could not create OAuth token yet: ${e.message}"
    }
}

private Map updateResult(Boolean success, String message, String reason) {
    String status = success ? "Success" : "Failed"
    String timestamp = new Date().format("yyyy-MM-dd h:mm:ss a", location.timeZone)
    state.lastUpdateStatus = "${status}: ${message}"
    state.lastUpdateDetail = "Last run ${timestamp} from ${reason}."
    if (success) {
        log.info "${app.label}: ${message}"
    } else {
        log.warn "${app.label}: ${message}"
    }
    return [success: success, message: message, reason: reason, timestamp: timestamp]
}

private Integer availabilityAttempts() {
    return Math.max(safeInteger(retryAttempts, 5), 1)
}

private Integer availabilityDelaySeconds() {
    return Math.max(safeInteger(retryDelaySeconds, 10), 1)
}

private Integer safeInteger(def value, Integer fallback) {
    try {
        return value == null ? fallback : value as Integer
    } catch (Exception ignored) {
        return fallback
    }
}

private void debug(String message) {
    if (debugLogging == true) log.debug "${app.label}: ${message}"
}
