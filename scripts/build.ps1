[CmdletBinding()]
param(
    [string[]]$Tasks = @('testDebugUnitTest', 'assembleDebug'),
    [switch]$Online
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$gradleHome = if ($env:NOTESHADOW_GRADLE_HOME) {
    $env:NOTESHADOW_GRADLE_HOME
} elseif (Test-Path -LiteralPath 'F:\GradleHome') {
    'F:\GradleHome'
} else {
    Join-Path $env:SystemDrive 'GradleHome'
}

if ($projectRoot -match '[^\x00-\x7F]') {
    throw "The project path must contain ASCII characters only. Current path: $projectRoot"
}
if ($gradleHome -match '[^\x00-\x7F]') {
    throw "The Gradle cache path must contain ASCII characters only. Current path: $gradleHome"
}

New-Item -ItemType Directory -Path $gradleHome -Force | Out-Null
$env:GRADLE_USER_HOME = $gradleHome

$gradleArgs = @($Tasks) + @('--no-daemon', '--max-workers=1')
if (-not $Online) {
    $gradleArgs += '--offline'
}

Push-Location $projectRoot
try {
    & (Join-Path $projectRoot 'gradlew.bat') @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}
