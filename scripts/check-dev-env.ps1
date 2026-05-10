$ErrorActionPreference = 'Stop'

function Test-Tool {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Name,

        [Parameter(Mandatory = $true)]
        [string] $InstallHint
    )

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($null -eq $command) {
        [pscustomobject]@{
            Tool = $Name
            Status = 'missing'
            Detail = $InstallHint
        }
        return
    }

    [pscustomobject]@{
        Tool = $Name
        Status = 'found'
        Detail = $command.Source
    }
}

$results = @(
    Test-Tool -Name 'java' -InstallHint 'Install a JDK 11+ and reopen your terminal.'
    Test-Tool -Name 'gradle' -InstallHint 'Install Gradle, or install it with a package manager after Java is available.'
)

$results | Format-Table -AutoSize

if ($results.Status -contains 'missing') {
    exit 1
}
