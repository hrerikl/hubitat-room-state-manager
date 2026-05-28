$ErrorActionPreference = 'Stop'

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$configPath = Join-Path $projectRoot '.hubitat-dev.json'

if (-not (Test-Path $configPath)) {
    throw "Missing .hubitat-dev.json. Copy .hubitat-dev.example.json and set simpleHomeDevUpdateUrl from the Simple Home Dev app."
}

$config = Get-Content $configPath -Raw | ConvertFrom-Json
$updateUrl = [string]$config.simpleHomeDevUpdateUrl
if ([string]::IsNullOrWhiteSpace($updateUrl)) {
    throw '.hubitat-dev.json must set simpleHomeDevUpdateUrl.'
}

$delaySeconds = 10
if ($config.rawAvailabilityDelaySeconds) {
    $delaySeconds = [int]$config.rawAvailabilityDelaySeconds
}

Push-Location $projectRoot
try {
    & (Join-Path $projectRoot 'scripts\gradle.ps1') check packageHubitat verifyHubitatPackage
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle verification failed with exit code $LASTEXITCODE."
    }

    $status = & 'C:\Program Files\Git\cmd\git.exe' status --short
    if ($status) {
        throw "Working tree has uncommitted changes. Commit before deploying:`n$status"
    }

    & 'C:\Program Files\Git\cmd\git.exe' push origin main
    if ($LASTEXITCODE -ne 0) {
        throw "git push failed with exit code $LASTEXITCODE."
    }

    if ($delaySeconds -gt 0) {
        Start-Sleep -Seconds $delaySeconds
    }

    $response = Invoke-RestMethod -Method Post -Uri $updateUrl -TimeoutSec 600
    $response | ConvertTo-Json -Depth 10

    if ($response.success -ne $true) {
        throw "Simple Home Dev update failed: $($response.message) $($response.detail)"
    }
} finally {
    Pop-Location
}
