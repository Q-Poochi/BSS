<#
.SYNOPSIS
    Vá quyền BLE vào AndroidManifest.xml của app Flutter (chạy lại nhiều lần không bị nhân đôi).

.DESCRIPTION
    flutter_blue_plus cần các quyền BLE khai báo trong manifest CỦA APP:
      - Android 12 (API 31) trở lên: BLUETOOTH_SCAN (neverForLocation) + BLUETOOTH_CONNECT.
        `neverForLocation` để Google Play không coi app là "theo dõi vị trí".
      - Android 11 trở xuống: BLUETOOTH + BLUETOOTH_ADMIN + ACCESS_FINE_LOCATION
        (hệ thống bắt buộc có quyền vị trí mới trả kết quả quét BLE).

.PARAMETER ManifestPath
    Đường dẫn file AndroidManifest.xml (thường là android/app/src/main/AndroidManifest.xml).

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tool/patch_android_manifest.ps1 -ManifestPath android/app/src/main/AndroidManifest.xml
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ManifestPath
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $ManifestPath)) {
    throw "Khong tim thay manifest: $ManifestPath"
}

$manifest = [System.IO.File]::ReadAllText($ManifestPath)

if ($manifest -match 'android\.permission\.BLUETOOTH_SCAN') {
    Write-Host "[=] $ManifestPath da co quyen BLE - bo qua."
    return
}

$permissionBlock = @'
<!-- ===== BSS: quyen cho BLE Central (dien thoai quet/ket noi ESP32-S3) ===== -->
    <!-- Android 12 (API 31) tro len -->
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <!-- Android 11 tro xuong: bat buoc co quyen vi tri moi quet duoc BLE -->
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
    <!-- required=false: may khong co BLE van cai duoc app (bao loi luc chay) -->
    <uses-feature android:name="android.hardware.bluetooth_le" android:required="false" />

'@

$anchorIndex = $manifest.IndexOf('<application')
if ($anchorIndex -lt 0) {
    throw "$ManifestPath khong co the <application> - co dung la AndroidManifest.xml?"
}

$patched = $manifest.Substring(0, $anchorIndex) + $permissionBlock +
    $manifest.Substring($anchorIndex)

# Ghi lai KHONG kem BOM: aapt2 doc file XML co BOM co the bao loi.
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($ManifestPath, $patched, $utf8NoBom)

Write-Host "[+] Da them quyen BLE vao $ManifestPath"
