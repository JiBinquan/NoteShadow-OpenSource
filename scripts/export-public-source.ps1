[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Destination
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$destinationPath = [System.IO.Path]::GetFullPath($Destination)
$rootPath = [System.IO.Path]::GetFullPath($projectRoot)
$gitSafeRoot = $rootPath.Replace('\', '/')

if (Test-Path -LiteralPath $destinationPath) {
    throw "Destination already exists: $destinationPath"
}
if (-not $destinationPath.StartsWith($rootPath + [System.IO.Path]::DirectorySeparatorChar,
        [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Use a destination inside the project workspace, such as .public-export/source."
}

Push-Location $projectRoot
try {
    $dirty = & git -c "safe.directory=$gitSafeRoot" status --porcelain --untracked-files=no
    if ($LASTEXITCODE -ne 0 -or $dirty) {
        throw 'Commit tracked changes before exporting the public source snapshot.'
    }

    $tracked = & git -c "safe.directory=$gitSafeRoot" -c core.quotepath=false ls-files
    if ($LASTEXITCODE -ne 0) { throw 'Could not list tracked files.' }
    $excluded = @(
        'BUILD_STATUS.md',
        'app/src/main/assets/asr-quality/sensevoice.int8.onnx',
        'app/src/main/assets/asr-quality/silero_vad.onnx',
        'app/src/main/assets/asr-quality/tokens.txt',
        'app/src/main/assets/asr/model.int8.onnx',
        'app/src/main/assets/asr/self-test.wav',
        'app/src/main/assets/asr/tokens.txt'
    )
    $excludedSet = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::OrdinalIgnoreCase)
    foreach ($path in $excluded) { [void]$excludedSet.Add($path) }

    foreach ($path in $tracked) {
        $normalized = $path.Replace('\', '/')
        if ($excludedSet.Contains($normalized)) { continue }
        if ($normalized -match '^app/src/main/assets/(asr|asr-quality|qwen3-int8)/') {
            throw "Unreviewed speech asset in tracked files: $normalized"
        }
        $source = Join-Path $projectRoot $path
        $target = Join-Path $destinationPath $path
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
            throw "Tracked source missing: $path (check Git LFS checkout)"
        }
        $parent = Split-Path -Parent $target
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
        Copy-Item -LiteralPath $source -Destination $target
    }

    $forbidden = Get-ChildItem -LiteralPath $destinationPath -Recurse -File |
        Where-Object { $_.Extension -in '.onnx', '.wav', '.apk', '.aab', '.jks', '.keystore' }
    if ($forbidden) {
        throw "Forbidden files in snapshot: $($forbidden.FullName -join ', ')"
    }
    Write-Host "Source-only snapshot ready: $destinationPath"
    Write-Host "No Git history was copied. Review it before creating a fresh public repository."
} finally {
    Pop-Location
}
