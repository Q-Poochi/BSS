<#
.SYNOPSIS
    Cai dung phien ban Android NDK ma Flutter SDK dang yeu cau (hien tai 28.2.13676358) vao Android SDK.

.DESCRIPTION
    Chay duoc tren ca Windows PowerShell 5.1 (powershell.exe) va PowerShell 7 (pwsh).
    (Giong bootstrap_platforms.ps1: KHONG dung $PSScriptRoot trong gia tri mac dinh cua param vi
    PS 5.1 de no rong khi script co [CmdletBinding()].)

    VI SAO CAN SCRIPT NAY
    Tu Android Studio 2026.1, lenh `sdkmanager` cu chi con la shim: no in canh bao
    "The SDK Manager CLI tool (sdkmanager) is deprecated. Android CLI will be used instead."
    roi chuyen tiep sang "Android CLI" moi. CLI moi khong hieu cu phap `ndk;<version>`, nen khi
    Gradle tu dong cai NDK (auto SDK download) thi that bai:

        Package ndk not found.
        Package 28.2.13676358 not found.
        org.gradle.api.GradleException: Android sdkmanager did not install NDK 28.2.13676358 into ...

    Script nay KHONG dung sdkmanager. No tai truc tiep goi NDK chinh thuc tu dl.google.com,
    lay URL + kich thuoc + SHA-1 tu `repository2-3.xml` (dung nguon du lieu ma SDK Manager doc),
    kiem tra SHA-1 roi giai nen bang `tar.exe` vao `<AndroidSDK>\ndk\<version>`.
    Ket qua giong het viec bam chon NDK trong SDK Manager cua Android Studio.

.PARAMETER NdkVersion
    Phien ban NDK can cai. Bo trong = doc tu Flutter SDK dang dung
    (packages/flutter_tools/gradle/src/main/kotlin/FlutterExtension.kt -> `val ndkVersion`).

.PARAMETER FlutterSdk
    Duong dan Flutter SDK. Mac dinh: doc tu android/local.properties (flutter.sdk).

.PARAMETER AndroidSdkDir
    Duong dan Android SDK. Mac dinh: android/local.properties (sdk.dir) -> ANDROID_HOME ->
    ANDROID_SDK_ROOT -> %LOCALAPPDATA%\Android\Sdk.

.PARAMETER DryRun
    Chi in ra phien ban/URL/SHA-1 se dung roi thoat, khong tai.

.PARAMETER KeepZip
    Giu lai file zip da tai trong %TEMP% (mac dinh xoa sau khi cai xong).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tool/install_ndk.ps1
#>
[CmdletBinding()]
param(
    [string]$NdkVersion,
    [string]$FlutterSdk,
    [string]$AndroidSdkDir,
    [switch]$DryRun,
    [switch]$KeepZip
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$ScriptDir = $PSScriptRoot
if (-not $ScriptDir -and $MyInvocation.MyCommand.Path) {
    $ScriptDir = Split-Path -Parent -Path $MyInvocation.MyCommand.Path
}
if (-not $ScriptDir) { $ScriptDir = (Get-Location).Path }
$ProjectDir = Split-Path -Parent -Path $ScriptDir
$localProps = Join-Path $ProjectDir 'android\local.properties'

function Read-LocalProperty([string]$name) {
    if (-not (Test-Path -LiteralPath $localProps)) { return $null }
    $m = Select-String -LiteralPath $localProps -Pattern ("^{0}\s*=\s*(.+)$" -f [regex]::Escape($name))
    if (-not $m) { return $null }
    # local.properties ghi '\\' cho duong dan Windows -> tra ve '\'.
    return ($m.Matches[0].Groups[1].Value.Trim() -replace '\\\\', '\')
}

# ---------- 1. Xac dinh Android SDK ----------
if (-not $AndroidSdkDir) {
    $AndroidSdkDir = Read-LocalProperty 'sdk.dir'
    if (-not $AndroidSdkDir) { $AndroidSdkDir = $env:ANDROID_HOME }
    if (-not $AndroidSdkDir) { $AndroidSdkDir = $env:ANDROID_SDK_ROOT }
    if (-not $AndroidSdkDir) { $AndroidSdkDir = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
}
if (-not (Test-Path -LiteralPath $AndroidSdkDir)) {
    Write-Host "Khong tim thay Android SDK: $AndroidSdkDir"
    exit 1
}
Write-Host "[i] Android SDK: $AndroidSdkDir"

# ---------- 2. Xac dinh Flutter SDK ----------
if (-not $FlutterSdk) { $FlutterSdk = Read-LocalProperty 'flutter.sdk' }
if (-not $FlutterSdk) {
    $f = Get-Command flutter -ErrorAction SilentlyContinue
    if ($f) { $FlutterSdk = Split-Path -Parent -Path (Split-Path -Parent -Path $f.Source) }
}
if ($FlutterSdk) { Write-Host "[i] Flutter SDK: $FlutterSdk" }

# ---------- 3. Xac dinh phien ban NDK ma Flutter yeu cau ----------
if (-not $NdkVersion) {
    $extFile = $null
    if ($FlutterSdk) {
        $extFile = Join-Path $FlutterSdk 'packages\flutter_tools\gradle\src\main\kotlin\FlutterExtension.kt'
    }
    if ($extFile -and (Test-Path -LiteralPath $extFile)) {
        $m = Select-String -LiteralPath $extFile -Pattern 'val ndkVersion:\s*String\s*=\s*"([^"]+)"'
        if ($m) { $NdkVersion = $m.Matches[0].Groups[1].Value }
    }
}
if (-not $NdkVersion) {
    Write-Host @'
Khong xac dinh duoc phien ban NDK can cai (khong doc duoc FlutterExtension.kt).

Chay lai voi tham so -NdkVersion, vi du:
  powershell -ExecutionPolicy Bypass -File tool/install_ndk.ps1 -NdkVersion 28.2.13676358

(Phien ban can cai nam trong android/app/build.gradle.kts: ndkVersion = flutter.ndkVersion;
gia tri do o <FlutterSDK>\packages\flutter_tools\gradle\src\main\kotlin\FlutterExtension.kt)
'@
    exit 1
}
Write-Host "[i] NDK can cai (Flutter yeu cau): $NdkVersion"

$ndkRoot = Join-Path $AndroidSdkDir 'ndk'
$target = Join-Path $ndkRoot $NdkVersion
$targetProps = Join-Path $target 'source.properties'

if (Test-Path -LiteralPath $targetProps) {
    $rev = (Select-String -LiteralPath $targetProps -Pattern '^Pkg\.Revision\s*=\s*(.+)$').Matches[0].Groups[1].Value.Trim()
    if ($rev -eq $NdkVersion) {
        Write-Host "[=] NDK $NdkVersion da co san tai $target - khong can cai lai."
        exit 0
    }
    Write-Host "[!] $target da ton tai nhung Pkg.Revision = $rev (khac $NdkVersion) - se cai de."
}

# ---------- 4. Tra cuu goi NDK trong danh muc chinh thuc cua Google ----------
$repoUrl = 'https://dl.google.com/android/repository/repository2-3.xml'
Write-Host "[i] Doc danh muc goi cua Google: $repoUrl"
$xml = (New-Object System.Net.WebClient).DownloadString($repoUrl)

$block = [regex]::Match(
    $xml,
    ('<remotePackage path="ndk;{0}">(?<b>.*?)</remotePackage>' -f [regex]::Escape($NdkVersion)),
    'Singleline')
if (-not $block.Success) {
    Write-Host "Khong thay goi 'ndk;$NdkVersion' trong danh muc cua Google."
    exit 1
}
$archive = [regex]::Match(
    $block.Groups['b'].Value,
    '<archive>\s*<complete>\s*<size>(?<size>\d+)</size>\s*<checksum type="sha1">(?<sha1>[0-9a-fA-F]+)</checksum>\s*<url>(?<url>[^<]+)</url>\s*</complete>\s*<host-os>windows</host-os>\s*</archive>',
    'Singleline')
if (-not $archive.Success) {
    Write-Host "Goi 'ndk;$NdkVersion' khong co ban cho Windows."
    exit 1
}
$zipName = $archive.Groups['url'].Value
$zipUrl = "https://dl.google.com/android/repository/$zipName"
$zipSize = [int64]$archive.Groups['size'].Value
$zipSha1 = $archive.Groups['sha1'].Value.ToLower()
Write-Host ("[i] Goi: {0} - {1} MB - sha1 {2}" -f $zipName, [math]::Round($zipSize / 1MB, 1), $zipSha1)

if ($DryRun) {
    Write-Host "[dry-run] Se tai: $zipUrl"
    Write-Host "[dry-run] Se giai nen vao: $target"
    exit 0
}

# ---------- 5. Tai goi NDK ----------
$zipPath = Join-Path $env:TEMP $zipName
if ((Test-Path -LiteralPath $zipPath) -and ((Get-Item -LiteralPath $zipPath).Length -eq $zipSize)) {
    Write-Host "[i] Da co san file tai: $zipPath"
} else {
    Write-Host ("[i] Dang tai {0} MB - co the mat vai phut..." -f [math]::Round($zipSize / 1MB, 1))
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    (New-Object System.Net.WebClient).DownloadFile($zipUrl, $zipPath)
    $sw.Stop()
    Write-Host ("[i] Tai xong sau {0:N0} giay" -f $sw.Elapsed.TotalSeconds)
}

$actualSize = (Get-Item -LiteralPath $zipPath).Length
if ($actualSize -ne $zipSize) {
    Write-Host ("Kich thuoc file tai ve khong dung: {0} (mong doi {1})" -f $actualSize, $zipSize)
    exit 1
}
$actualSha1 = (Get-FileHash -LiteralPath $zipPath -Algorithm SHA1).Hash.ToLower()
if ($actualSha1 -ne $zipSha1) {
    Write-Host ("SHA-1 khong khop: {0} (mong doi {1})" -f $actualSha1, $zipSha1)
    exit 1
}
Write-Host "[i] SHA-1 OK: $actualSha1"

# ---------- 6. Giai nen bang tar.exe (nhanh hon Expand-Archive rat nhieu) ----------
$tar = Join-Path $env:SystemRoot 'System32\tar.exe'
if (-not (Test-Path -LiteralPath $tar)) { $tar = 'tar.exe' }
$stage = Join-Path $env:TEMP ('bss_ndk_stage_' + $NdkVersion)
if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
New-Item -ItemType Directory -Path $stage -Force | Out-Null
Write-Host "[i] Giai nen vao $stage (co the mat 1-2 phut)..."
& $tar -xf $zipPath -C $stage
if ($LASTEXITCODE -ne 0) {
    Write-Host "tar.exe that bai (exit $LASTEXITCODE)"
    exit 1
}

$inner = Get-ChildItem -LiteralPath $stage -Directory | Select-Object -First 1
if (-not $inner) {
    Write-Host "Khong thay thu muc NDK trong $stage sau khi giai nen."
    exit 1
}

# ---------- 7. Dat vao <AndroidSDK>\ndk\<version> ----------
if (-not (Test-Path -LiteralPath $ndkRoot)) { New-Item -ItemType Directory -Path $ndkRoot -Force | Out-Null }
if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
Move-Item -LiteralPath $inner.FullName -Destination $target
Write-Host "[i] Da dat NDK vao $target"

$props = Join-Path $target 'source.properties'
if (-not (Test-Path -LiteralPath $props)) {
    Write-Host "Thieu $props - goi NDK khong hop le."
    exit 1
}
$rev = (Select-String -LiteralPath $props -Pattern '^Pkg\.Revision\s*=\s*(.+)$').Matches[0].Groups[1].Value.Trim()
if ($rev -ne $NdkVersion) {
    Write-Host ("Pkg.Revision = {0}, khong khop {1}" -f $rev, $NdkVersion)
    exit 1
}
$strip = Join-Path $target 'toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-strip.exe'
if (-not (Test-Path -LiteralPath $strip)) {
    Write-Host "Thieu llvm-strip.exe - NDK giai nen khong day du."
    exit 1
}

# ---------- 8. Don dep ----------
Remove-Item -LiteralPath $stage -Recurse -Force
if (-not $KeepZip) { Remove-Item -LiteralPath $zipPath -Force -ErrorAction SilentlyContinue }

Write-Host ''
Write-Host "[OK] NDK $NdkVersion da san sang: $target"
Write-Host 'Buoc tiep theo:'
Write-Host '  cd app-flutter'
Write-Host '  flutter run       # hoac bam Run trong Android Studio'
