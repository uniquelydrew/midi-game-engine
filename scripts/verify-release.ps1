[CmdletBinding()]
param(
    [string]$ApkPath = "app/build/outputs/apk/release/app-release.apk",
    [string]$AabPath = "app/build/outputs/bundle/release/app-release.aab"
)

$ErrorActionPreference = "Stop"

function Find-SdkTool([string]$Name) {
    $sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else {
        Join-Path $env:LOCALAPPDATA "Android\Sdk"
    }
    $tool = Get-ChildItem (Join-Path $sdkRoot "build-tools") -Filter $Name -Recurse -File |
        Sort-Object FullName -Descending |
        Select-Object -First 1
    if (-not $tool) { throw "Android SDK tool not found: $Name" }
    return $tool.FullName
}

if (-not (Test-Path -LiteralPath $ApkPath)) { throw "APK not found: $ApkPath" }

$apksigner = Find-SdkTool "apksigner.bat"
$aapt2 = Find-SdkTool "aapt2.exe"

& $apksigner verify --verbose $ApkPath
$badging = (& $aapt2 dump badging $ApkPath) -join "`n"
$permissions = (& $aapt2 dump permissions $ApkPath) -join "`n"

if ($badging -notmatch "package: name='com\.example\.midigameengine'") {
    throw "Unexpected application ID in APK. Set the final Play application ID before upload."
}
if ($badging -notmatch "targetSdkVersion:'36'") { throw "APK does not target API 36." }
if ($badging -notmatch "minSdkVersion:'24'") { throw "APK minimum SDK changed unexpectedly." }
if ($badging -notmatch "application-label:'MIDI Game Engine'") { throw "Unexpected application label." }

$forbiddenPermissions = @(
    "android.permission.INTERNET",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.MANAGE_EXTERNAL_STORAGE"
)
foreach ($permission in $forbiddenPermissions) {
    if ($permissions -match [regex]::Escape($permission)) {
        throw "Unexpected broad permission in APK: $permission"
    }
}

if (Test-Path -LiteralPath $AabPath) {
    $jarsigner = Get-Command jarsigner -ErrorAction SilentlyContinue
    if ($jarsigner) {
        & $jarsigner.Source -verify -verbose -certs $AabPath
    } else {
        Write-Warning "jarsigner is not on PATH; skipped AAB signature verification."
    }
}

Write-Host "Release verification passed: $ApkPath"
