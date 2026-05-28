/**
 * Simple Home Dev - Developer App
 *
 *   Name: Simple Home Dev
 *   Namespace: lundby
 *
 * Developer-only update surface for a Simple Home development hub.
 */

import groovy.json.JsonOutput

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
            input "reinitializeAfterUpdate", "bool", title: "Reinitialize Simple Home and child apps after update", defaultValue: false, required: true
            if (reinitializeAfterUpdate == true) {
                input "reinitializeSimpleHomeSwitch", "capability.switch", title: "Reinitialize Simple Home switch", required: true
            }
            input "matchNow", "button", title: "Match Installed Simple Home Code"
            input "updateNow", "button", title: "Update Simple Home"
            input "retryAttempts", "number", title: "Source availability attempts", defaultValue: 5, required: true
            input "retryDelaySeconds", "number", title: "Seconds between availability attempts", defaultValue: 10, required: true
        }

        section("Self Update") {
            input "devAppSourceUrl", "text", title: "Simple Home Dev source URL", defaultValue: "https://raw.githubusercontent.com/hrerikl/hubitat-room-state-manager/main/apps/SimpleHomeDev.groovy", required: true
            input "devAppCodeId", "text", title: "Simple Home Dev App Code ID", required: false
            input "updateDevAppNow", "button", title: "Update Simple Home Dev"
            paragraph "Detected Simple Home Dev App Code ID: ${detectedDevAppCodeId() ?: 'not found'}"
            paragraph "The manual App Code ID is an override. Leave it blank to use the detected ID."
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
            paragraph "Matched components: ${matchedComponentCount()}"
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
    if (buttonName == "matchNow") {
        Map result = matchSimpleHome("app button")
        log.info "${app.label}: ${result.message}"
    }
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
    Map manifest = loadPackageManifest()
    if (!manifest) {
        return updateResult(false, "Package manifest was not available after ${availabilityAttempts()} attempts.", reason)
    }

    if (!login()) {
        return updateResult(false, "Hub security login failed.", reason)
    }

    Map matches = currentMatches()
    if (!matchesValidForManifest(matches, manifest)) {
        Map matchResult = matchManifest(manifest)
        if (matchResult.success != true) {
            return updateResult(false, "Simple Home match failed.", reason, matchResult.detail?.toString())
        }
        matches = currentMatches()
    }

    Map manifestUpdateResult = updateFromManifest(manifest, matches)
    if (manifestUpdateResult.success != true) {
        return updateResult(false, "Simple Home update failed.", reason, manifestUpdateResult.detail?.toString())
    }

    Map reinitializeResult = reinitializeAfterUpdate == true ? reinitializeSimpleHomeParent() : [success: true, detail: "Reinitialize skipped."]
    Boolean success = reinitializeResult.success == true
    String detail = "${manifestUpdateResult.detail}\n${reinitializeResult.detail}"
    return updateResult(success, success ? "Simple Home manifest update completed." : "Simple Home manifest update completed with reinitialize failure.", reason, detail)
}

private Map matchSimpleHome(String reason) {
    Map manifest = loadPackageManifest()
    if (!manifest) {
        return updateResult(false, "Package manifest was not available after ${availabilityAttempts()} attempts.", reason)
    }

    if (!login()) {
        return updateResult(false, "Hub security login failed.", reason)
    }

    Map matchResult = matchManifest(manifest)
    if (matchResult.success != true) {
        return updateResult(false, "Simple Home match failed.", reason, matchResult.detail?.toString())
    }

    return updateResult(true, "Simple Home match completed.", reason, matchResult.detail?.toString())
}

private Map updateSimpleHomeDev(String reason) {
    String codeId = configuredDevAppCodeId()
    if (!codeId) {
        return updateResult(false, "Simple Home Dev App Code ID was not configured or detected.", reason)
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

private String configuredDevAppCodeId() {
    String manual = devAppCodeId?.toString()?.trim()
    return manual ?: detectedDevAppCodeId()
}

private String detectedDevAppCodeId() {
    Map apps = installedCodeByKey("app")
    return apps["lundby:Simple Home Dev"]?.toString() ?: apps["name:Simple Home Dev"]?.toString()
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

private Map matchManifest(Map manifest) {
    Map appCode = installedCodeByKey("app")
    Map driverCode = installedCodeByKey("driver")
    Map libraryCode = installedCodeByKey("library")
    Map matches = [apps: [:], drivers: [:], files: [:]]
    List missing = []

    asList(manifest.apps).each { item ->
        String key = componentKey(item)
        String id = appCode[key]
        if (id) {
            matches.apps[item.id?.toString()] = id
        } else {
            missing << "app ${item.namespace}.${item.name}"
        }
    }

    asList(manifest.drivers).each { item ->
        String key = componentKey(item)
        String id = driverCode[key]
        if (id) {
            matches.drivers[item.id?.toString()] = id
        } else {
            missing << "driver ${item.namespace}.${item.name}"
        }
    }

    asList(manifest.files).each { item ->
        String key = componentKey(item)
        String id = libraryCode[key] ?: libraryCode[nameKey(item)]
        if (id) {
            matches.files[item.id?.toString()] = id
        } else {
            missing << "library ${item.namespace}.${item.name}"
        }
    }

    state.simpleHomeDevMatches = matches
    if (missing) {
        return [success: false, detail: "Matched ${matchedComponentCount(matches)} component(s). Missing: ${missing.join(', ')}"]
    }

    return [success: true, detail: "Matched ${matchedComponentCount(matches)} component(s)."]
}

private Map updateFromManifest(Map manifest, Map matches) {
    List updated = []
    List failed = []

    asList(manifest.files).each { item ->
        updateManifestItem("library", item, matches.files, updated, failed)
    }
    asList(manifest.drivers).each { item ->
        updateManifestItem("driver", item, matches.drivers, updated, failed)
    }
    asList(manifest.apps).each { item ->
        updateManifestItem("app", item, matches.apps, updated, failed)
    }

    if (failed) {
        return [success: false, detail: "Updated ${updated.size()} component(s). Failed: ${failed.join(', ')}"]
    }

    return [success: true, detail: "Updated ${updated.size()} component(s): ${updated.join(', ')}"]
}

private Map reinitializeSimpleHomeParent() {
    def dev = reinitializeSimpleHomeSwitch
    if (!dev) {
        return [success: false, detail: "Reinitialize failed: no Reinitialize Simple Home switch selected."]
    }

    try {
        dev.on()
        return [success: true, detail: "Triggered ${dev.displayName ?: 'Reinitialize Simple Home switch'}."]
    } catch (Throwable e) {
        log.warn "${app.label}: Could not trigger Reinitialize Simple Home switch ${dev?.displayName ?: dev?.id}: ${e.message}"
        return [success: false, detail: "Reinitialize failed: ${e.message}"]
    }
}

private void updateManifestItem(String kind, Map item, Map matchMap, List updated, List failed) {
    String manifestId = item.id?.toString()
    String codeId = matchMap[manifestId]?.toString()
    String label = "${kind} ${item.namespace}.${item.name}".toString()
    if (!codeId) {
        failed << "${label} not matched"
        return
    }

    String source = loadTextWithRetry(item.location?.toString(), label)
    if (!source) {
        failed << "${label} source unavailable"
        return
    }

    Boolean success = false
    if (kind == "app") {
        success = updateAppCode(codeId, source)
    } else if (kind == "driver") {
        success = updateDriverCode(codeId, source)
    } else if (kind == "library") {
        success = updateLibraryCode(codeId, source)
    }

    if (success) {
        updated << "${item.name}"
    } else {
        failed << "${label} update failed"
    }
}

private Map currentMatches() {
    return state.simpleHomeDevMatches instanceof Map ? state.simpleHomeDevMatches : [apps: [:], drivers: [:], files: [:]]
}

private Boolean matchesValidForManifest(Map matches, Map manifest) {
    return asList(manifest.apps).every { matches.apps[it.id?.toString()] } &&
        asList(manifest.drivers).every { matches.drivers[it.id?.toString()] } &&
        asList(manifest.files).every { matches.files[it.id?.toString()] }
}

private Integer matchedComponentCount(Map matches = null) {
    Map current = matches ?: currentMatches()
    return safeInteger(current.apps?.size(), 0) + safeInteger(current.drivers?.size(), 0) + safeInteger(current.files?.size(), 0)
}

private Map installedCodeByKey(String kind) {
    List entries = installedCodeList(kind)
    Map results = [:]
    entries.each { item ->
        String key = componentKey(item)
        String nameOnlyKey = nameKey(item)
        String id = item.id?.toString()
        if (key && id && !results[key]) results[key] = id
        if (nameOnlyKey && id && !results[nameOnlyKey]) results[nameOnlyKey] = id
    }
    return results
}

private List installedCodeList(String kind) {
    if (kind == "app") return getAppList()
    if (kind == "driver") return getDriverList()
    if (kind == "library") return getLibraryList()
    return []
}

private String componentKey(def item) {
    String namespace = item?.namespace?.toString()?.trim()
    String name = item?.name?.toString()?.trim() ?: item?.title?.toString()?.trim()
    return namespace && name ? "${namespace}:${name}" : null
}

private String nameKey(def item) {
    String name = item?.name?.toString()?.trim() ?: item?.title?.toString()?.trim()
    return name ? "name:${name}" : null
}

private List getAppList() {
    return hub2CodeList("/hub2/userAppTypes", "installed apps")
}

private List getDriverList() {
    return hub2CodeList("/hub2/userDeviceTypes", "installed drivers")
}

private List getLibraryList() {
    List libraries = hub2CodeList("/hub2/userLibraryTypes", "installed libraries")
    if (libraries) return libraries

    libraries = hub2CodeList("/hub2/userLibraries", "installed libraries")
    if (libraries) return libraries

    return hub2CodeList("/hub2/userLibTypes", "installed libraries")
}

private List hub2CodeList(String path, String description) {
    try {
        List result = []
        httpGet([
            uri             : baseUrl(),
            path            : path,
            headers         : ["Cookie": state.cookie],
            timeout         : 300,
            ignoreSSLIssues : true
        ]) { resp ->
            asList(resp?.data).each { item ->
                result << [
                    id       : firstText(item?.id, item?.typeId, item?.libraryTypeId, item?.appTypeId, item?.deviceTypeId),
                    title    : firstText(item?.name, item?.title),
                    name     : firstText(item?.name, item?.title),
                    namespace: firstText(item?.namespace, item?.nameSpace)
                ]
            }
        }
        debug "Loaded ${result.size()} ${description} from ${path}${result ? ": ${result.take(3)}" : ""}."
        return result
    } catch (Exception e) {
        log.warn "${app.label}: Could not retrieve ${description}: ${e.message}"
        return []
    }
}

private String firstText(Object... values) {
    for (Object value : values) {
        String text = value?.toString()?.trim()
        if (text) return text
    }
    return null
}

private Boolean updateDriverCode(String codeId, String source) {
    try {
        Boolean result = false
        httpPost([
            uri                 : baseUrl(),
            path                : "/driver/ajax/update",
            requestContentType  : "application/x-www-form-urlencoded",
            headers             : ["Connection": "keep-alive", "Cookie": state.cookie],
            body                : [id: codeId, version: driverCodeVersion(codeId), source: source],
            timeout             : 420,
            ignoreSSLIssues     : true
        ]) { resp ->
            result = resp?.data?.status == "success"
        }
        return result
    } catch (Exception e) {
        log.warn "${app.label}: Driver code update failed: ${e.message}"
        return false
    }
}

private String driverCodeVersion(String codeId) {
    return codeVersion("driver", codeId)
}

private Boolean updateLibraryCode(String codeId, String source) {
    try {
        Boolean result = false
        httpPost([
            uri                 : baseUrl(),
            path                : "/library/ajax/update",
            requestContentType  : "application/x-www-form-urlencoded",
            headers             : ["Connection": "keep-alive", "Cookie": state.cookie],
            body                : [id: codeId, version: libraryCodeVersion(codeId), source: source],
            timeout             : 420,
            ignoreSSLIssues     : true
        ]) { resp ->
            result = resp?.data?.status == "success"
        }
        return result
    } catch (Exception e) {
        log.warn "${app.label}: Library code update failed: ${e.message}"
        return false
    }
}

private String libraryCodeVersion(String codeId) {
    return codeVersion("library", codeId)
}

private String codeVersion(String kind, String codeId) {
    try {
        String result = ""
        httpGet([
            uri                 : baseUrl(),
            path                : "/${kind}/ajax/code",
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
        log.warn "${app.label}: Could not load ${kind} code version for ${codeId}: ${e.message}"
        return ""
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

private Map updateResult(Boolean success, String message, String reason, String detail = null) {
    String status = success ? "Success" : "Failed"
    String timestamp = new Date().format("yyyy-MM-dd h:mm:ss a", location.timeZone)
    state.lastUpdateStatus = "${status}: ${message}"
    state.lastUpdateDetail = "Last run ${timestamp} from ${reason}.${detail ? "\n${detail}" : ""}"
    if (success) {
        log.info "${app.label}: ${message}"
    } else {
        log.warn "${app.label}: ${message}"
    }
    return [success: success, message: message, reason: reason, timestamp: timestamp, detail: detail]
}

private Integer availabilityAttempts() {
    return Math.max(safeInteger(retryAttempts, 5), 1)
}

private Integer availabilityDelaySeconds() {
    return Math.max(safeInteger(retryDelaySeconds, 10), 1)
}

private List asList(def value) {
    if (value == null) return []
    return value instanceof List ? value : [value]
}

private Integer safeInteger(def value, Integer fallback) {
    try {
        return value == null ? fallback : value as Integer
    } catch (Exception ignored) {
        return fallback
    }
}

private String limitedText(def value) {
    String text = value?.toString() ?: ""
    return text.size() > 500 ? text.substring(0, 500) : text
}

private void debug(String message) {
    if (debugLogging == true) log.debug "${app.label}: ${message}"
}
