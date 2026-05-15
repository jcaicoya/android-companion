param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
$distDir = Join-Path $root "dist"
$releasesFile = Join-Path $root "releases.json"
$staging = Join-Path $root "_staging_release"

$appName = "under_attack_permission_android"
$apkBaseName = "under_attack_permission_android"
$apkSource = Join-Path $root "app\build\outputs\apk\release\app-release.apk"

function Invoke-Git {
    param(
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$Args
    )
    & git "-c" "safe.directory=*" -C $root @Args
}

function Test-GitRepo {
    Invoke-Git "rev-parse" "--is-inside-work-tree" 2>$null | Out-Null
    return ($LASTEXITCODE -eq 0)
}

$hasGit = Test-GitRepo
if ($hasGit) {
    $commitShort = Invoke-Git "rev-parse" "--short" "HEAD"
    $commitMsg = Invoke-Git "log" "-1" "--pretty=%s"
} else {
    $commitShort = "nogit"
    $commitMsg = "Unversioned Android package"
}

if (-not (Test-Path $releasesFile)) {
    [PSCustomObject]@{ releases = @() } | ConvertTo-Json -Depth 5 | Set-Content $releasesFile -Encoding UTF8
}

$data = Get-Content $releasesFile | ConvertFrom-Json
$releases = @($data.releases)
$last = if ($releases.Count -gt 0) { $releases[-1] } else { $null }

if (-not $Force -and $last -and $last.commit -eq $commitShort) {
    Write-Host "Already packaged as $($last.apk). Nothing to do."
    Write-Host "Use -Force to repackage the same commit."
    exit 0
}

$versionNum = if ($releases.Count -eq 0) { 0 } else { [int]$last.version + 1 }
$versionTag = "v{0:D2}" -f $versionNum
$apkName = "$apkBaseName-$versionTag.apk"
$apkPath = Join-Path $distDir $apkName

Write-Host ""
Write-Host "=== Packaging $versionTag ==="
Write-Host "  Commit : $commitShort - $commitMsg"
Write-Host "  Output : $apkName"
Write-Host ""

Push-Location $root
try {
    & ".\gradlew.bat" assembleRelease
} finally {
    Pop-Location
}
if ($LASTEXITCODE -ne 0) { Write-Error "Build failed."; exit 1 }

if (-not (Test-Path $apkSource)) {
    Write-Error "Release APK not found: $apkSource"
    exit 1
}

if (Test-Path $staging) { Remove-Item -LiteralPath $staging -Recurse -Force }
New-Item -ItemType Directory -Path $staging | Out-Null

$entryDate = Get-Date -Format "yyyy-MM-ddTHH:mm:ss"
$metadata = [PSCustomObject]@{
    app     = $appName
    version = $versionNum
    commit  = $commitShort
    date    = $entryDate
    message = $commitMsg
    apk     = $apkName
}

$metadata | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $staging "version.json") -Encoding UTF8
@(
    "app=$($metadata.app)"
    "version=$($metadata.version)"
    "commit=$($metadata.commit)"
    "date=$($metadata.date)"
    "message=$($metadata.message)"
    "apk=$($metadata.apk)"
) | Set-Content (Join-Path $staging "BUILD_INFO.txt") -Encoding UTF8

if (-not (Test-Path $distDir)) { New-Item -ItemType Directory -Path $distDir | Out-Null }
Copy-Item -LiteralPath $apkSource -Destination $apkPath -Force
Copy-Item (Join-Path $staging "version.json") (Join-Path $distDir "version.json") -Force
Copy-Item (Join-Path $staging "BUILD_INFO.txt") (Join-Path $distDir "BUILD_INFO.txt") -Force
Remove-Item -LiteralPath $staging -Recurse -Force

$entry = [PSCustomObject]@{
    version = $versionNum
    commit  = $commitShort
    date    = $entryDate
    message = $commitMsg
    apk     = $apkName
}
$releases += $entry
$data.releases = $releases
$data | ConvertTo-Json -Depth 5 | Set-Content $releasesFile -Encoding UTF8

if ($hasGit) {
    Write-Host ">> Tagging commit as $versionTag..."
    Invoke-Git "tag" $versionTag 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "   Note: tag $versionTag already exists, skipped."
    }
}

$sizeMB = [math]::Round((Get-Item $apkPath).Length / 1MB, 2)
Write-Host ""
Write-Host "=== Done ==="
Write-Host "  App     : $appName"
Write-Host "  Version : $versionTag"
Write-Host "  Commit  : $commitShort - $commitMsg"
Write-Host "  APK     : $apkName ($sizeMB MB)"
Write-Host "  Path    : $apkPath"
Write-Host ""
