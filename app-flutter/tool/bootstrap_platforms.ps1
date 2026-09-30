<#
.SYNOPSIS
    Sinh thư mục nền tảng (android/, ios/) cho app Flutter này rồi tự vá quyền BLE.

.DESCRIPTION
    Chay duoc tren ca Windows PowerShell 5.1 (powershell.exe) va PowerShell 7 (pwsh).

    Thư mục app-flutter/ chỉ chứa phần SOURCE (lib/, test/, tool/, pubspec.yaml...) vì máy
    tạo project không có Flutter SDK. Script chạy `flutter create` để sinh android/ + ios/,
    nhưng TRƯỚC ĐÓ sao lưu toàn bộ file do người viết để flutter create không ghi đè.

    Các bước:
      1. Kiểm tra `flutter` có trên PATH.
      2. Sao lưu pubspec.yaml, analysis_options.yaml, .gitignore, README.md, lib/, test/, tool/.
      3. flutter create --empty --project-name smart_glasses_app --org com.bss --platforms=android,ios .
      4. Khôi phục file đã sao lưu (ghi đè template vừa sinh).
      5. Xoá test/widget_test.dart của template (nó tham chiếu MyApp không tồn tại ở app này).
      6. Vá quyền BLE vào android/app/src/main/AndroidManifest.xml.
      7. flutter pub get (thêm -RunChecks để chạy luôn flutter analyze + flutter test).

.PARAMETER RunChecks
    Chạy thêm `flutter analyze` và `flutter test` sau khi cài dependency.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tool/bootstrap_platforms.ps1 -RunChecks
#>
[CmdletBinding()]
param(
    [string]$ProjectDir,
    [string]$ProjectName = 'smart_glasses_app',
    [string]$Org = 'com.bss',
    [string[]]$Platforms = @('android', 'ios'),
    [switch]$RunChecks
)

$ErrorActionPreference = 'Stop'

# QUAN TRONG (Windows PowerShell 5.1): khi script co [CmdletBinding()], bien tu dong
# $PSScriptRoot RONG trong bieu thuc gia tri mac dinh cua param (PowerShell 7 moi sua).
# Vi vay KHONG duoc viet `[string]$ProjectDir = (Split-Path -Parent $PSScriptRoot)` o tren:
# PS 5.1 se bao "Split-Path : Cannot bind argument to parameter 'Path' because it is an
# empty string." Duong dan duoc tinh o day - sau khi bind tham so - va co fallback cho ca
# PowerShell 5.1 lan 7.
$ScriptDir = $PSScriptRoot
if (-not $ScriptDir -and $MyInvocation.MyCommand.Path) {
    $ScriptDir = Split-Path -Parent -Path $MyInvocation.MyCommand.Path
}
if (-not $ScriptDir) { $ScriptDir = (Get-Location).Path }

# Mac dinh: thu muc cha cua tool/ (tuc la app-flutter/).
if (-not $ProjectDir) { $ProjectDir = Split-Path -Parent -Path $ScriptDir }
$ProjectDir = (Resolve-Path -LiteralPath $ProjectDir).Path
Write-Host "[i] Project: $ProjectDir"

# ---------- 1. Kiểm tra Flutter SDK ----------
$flutterCmd = Get-Command flutter -ErrorAction SilentlyContinue
if (-not $flutterCmd) {
    # Dung Write-Host + exit 1 (khong dung `throw`) de thong bao chi in MOT lan, khong
    # bi PowerShell boc lai kem tien to "powershell : ..." kho doc.
    Write-Host @'
Khong tim thay `flutter` tren PATH.

Cai Flutter SDK roi mo lai terminal:
  1. Tai SDK:        https://docs.flutter.dev/get-started/install/windows
  2. Giai nen vao:   C:\flutter        (dung dat trong Program Files)
  3. Them vao PATH:  C:\flutter\bin
  4. Kiem tra:       flutter doctor -v
  5. Quay lai day:   powershell -ExecutionPolicy Bypass -File tool/bootstrap_platforms.ps1 -RunChecks
'@
    exit 1
}
Write-Host "[i] Flutter: $($flutterCmd.Source)"

# ---------- 2. Sao lưu source do người viết ----------
$protected = @(
    'pubspec.yaml',
    'analysis_options.yaml',
    '.gitignore',
    'README.md',
    'lib',
    'test',
    'tool'
)
$backup = Join-Path $env:TEMP ('bss_flutter_backup_' + (Get-Date -Format 'yyyyMMdd_HHmmss'))
New-Item -ItemType Directory -Path $backup -Force | Out-Null

foreach ($item in $protected) {
    $source = Join-Path $ProjectDir $item
    if (Test-Path -LiteralPath $source) {
        Copy-Item -LiteralPath $source -Destination $backup -Recurse -Force
        Write-Host "[i] Sao luu $item"
    }
}

# ---------- 3..7: tạo platform shell rồi khôi phục source ----------
Push-Location $ProjectDir
try {
    Write-Host '[i] flutter create ...'
    & flutter create --empty --project-name $ProjectName --org $Org `
        --platforms ($Platforms -join ',') .
    if ($LASTEXITCODE -ne 0) { throw "flutter create that bai (exit $LASTEXITCODE)" }

    foreach ($item in $protected) {
        $saved = Join-Path $backup $item
        if (Test-Path -LiteralPath $saved) {
            Copy-Item -LiteralPath $saved -Destination $ProjectDir -Recurse -Force
        }
    }
    Write-Host '[i] Da khoi phuc source (lib/, test/, pubspec.yaml...)'

    $templateTest = Join-Path $ProjectDir 'test\widget_test.dart'
    if ((Test-Path -LiteralPath $templateTest) -and
        (Select-String -LiteralPath $templateTest -Pattern 'MyApp' -Quiet)) {
        Remove-Item -LiteralPath $templateTest -Force
        Write-Host '[-] Da xoa test/widget_test.dart cua template'
    }

    $manifest = Join-Path $ProjectDir 'android\app\src\main\AndroidManifest.xml'
    & (Join-Path $ScriptDir 'patch_android_manifest.ps1') -ManifestPath $manifest

    Write-Host '[i] flutter pub get ...'
    & flutter pub get
    if ($LASTEXITCODE -ne 0) { throw "flutter pub get that bai (exit $LASTEXITCODE)" }

    if ($RunChecks) {
        Write-Host '[i] flutter analyze ...'
        & flutter analyze
        Write-Host '[i] flutter test ...'
        & flutter test
    }
}
finally {
    Pop-Location
}

Write-Host ''
Write-Host "[OK] Xong. Ban sao luu: $backup"
Write-Host 'Buoc tiep theo:'
Write-Host '  flutter devices'
Write-Host '  flutter run            # cam dien thoai Android bat USB debugging'
Write-Host 'Kiem tra: android/app/src/main/AndroidManifest.xml co BLUETOOTH_SCAN/BLUETOOTH_CONNECT.'
