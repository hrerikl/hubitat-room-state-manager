$ErrorActionPreference = 'Stop'

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$localGradle = Join-Path $projectRoot '.tools\gradle-8.10.2\bin\gradle.bat'

if (-not $env:JAVA_HOME) {
    $adoptiumRoot = 'C:\Program Files\Eclipse Adoptium'
    if (Test-Path $adoptiumRoot) {
        $jdk = Get-ChildItem $adoptiumRoot -Directory |
            Where-Object { $_.Name -like 'jdk-*' } |
            Sort-Object Name -Descending |
            Select-Object -First 1

        if ($jdk) {
            $env:JAVA_HOME = $jdk.FullName
        }
    }
}

if ($env:JAVA_HOME) {
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

if (Test-Path $localGradle) {
    & $localGradle @args
    exit $LASTEXITCODE
}

& gradle @args
exit $LASTEXITCODE
