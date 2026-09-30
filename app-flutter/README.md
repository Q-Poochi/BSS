# BSS Companion — App Flutter

App điện thoại đi kèm hệ thống **BSS (Blind Spot System)**: nhận bản tin cảnh báo điểm mù do
firmware ESP32-S3 (Seeed XIAO) đẩy lên qua BLE và hiển thị cho người dùng.

> **Quan hệ với `app-android/`**: thư mục `app-android/` (Kotlin + Jetpack Compose) hiện
> **chưa dùng** và vẫn được giữ nguyên trong repo. `app-flutter/` là bản Flutter thay thế —
> cùng giao thức BLE, cùng quy tắc thiết kế (app chỉ **hiển thị** mức cảnh báo do firmware
> quyết định), khác công nghệ UI.
>
> Thư mục `lib/` ở gốc repo là của **PlatformIO (firmware C++)**, không liên quan tới
> `app-flutter/lib/` (Dart).

```
ESP32-S3 (GATT Server)  --notify 4 byte-->  app-flutter (GATT Client)  -->  UI
```

---

## 1. Cấu trúc thư mục

```
app-flutter/
├── pubspec.yaml                     # Package + dependency (flutter_blue_plus ^1.32.12)
├── analysis_options.yaml            # flutter_lints + 2 rule bổ sung
├── .gitignore                       # Dart/Flutter + Android/iOS artifacts
├── README.md                        # File này
├── lib/
│   ├── main.dart                    # Sở hữu BleService, MaterialApp -> ScanScreen
│   ├── models/
│   │   └── obstacle_event.dart      # "Hợp đồng dữ liệu": giải mã payload 4 byte + UrgencyLevel
│   ├── services/
│   │   └── ble_service.dart         # Quét / kết nối / subscribe notify -> Stream
│   └── screens/
│       ├── scan_screen.dart         # Màn hình 1: quét & kết nối
│       └── monitor_screen.dart      # Màn hình 2: hiển thị khoảng cách + mức cảnh báo
├── test/
│   └── obstacle_event_test.dart     # 11 unit test cho bộ giải mã payload
└── tool/
    ├── bootstrap_platforms.ps1      # Sinh android/ + ios/ (flutter create) rồi vá quyền BLE
    ├── bootstrap_platforms.cmd      # Wrapper: chạy được từ cmd.exe / Explorer, khỏi lo ExecutionPolicy
    ├── install_ndk.ps1              # Cài NDK 28.2.13676358 (Flutter đòi) — xem §2.3
    └── patch_android_manifest.ps1   # Vá quyền BLE vào AndroidManifest.xml (idempotent)
```

`android/`, `ios/`, `.metadata` **chưa có** trong repo vì máy dựng project không cài Flutter
SDK — chúng được sinh ra ở bước 2 bên dưới.

---

## 2. Chạy lần đầu

### 2.1. Cài Flutter SDK (nếu máy chưa có)

```powershell
# Kiểm tra
flutter --version
```

**Trạng thái máy dev này:** Flutter SDK **đã cài** tại `C:\flutter` (Flutter 3.47.5 stable, Dart
3.13.4 — gõ `flutter --version` trong terminal mở mới là thấy). Máy khác nếu chưa có thì cài bằng
git (khuyến nghị, sau này `flutter upgrade` được — Git đã có sẵn ở `C:\Program Files\Git\cmd\git.exe`):

```powershell
git clone -b stable --depth 1 https://github.com/flutter/flutter.git C:\flutter
$env:Path = "C:\flutter\bin;$env:Path"   # thêm vĩnh viễn vào PATH nếu muốn
flutter --version
flutter doctor -v
```

Phần **Android thì máy này đã sẵn sàng**, không cần cài thêm:

| Thành phần | Trạng thái | Ghi chú |
|---|---|---|
| Android Studio | ✅ `C:\Program Files\Android\Android Studio` (build `AI-261.25134.95`) | Bản 2026.1 — đủ mới cho plugin Flutter |
| Android SDK | ✅ `C:\Users\ADMIN\AppData\Local\Android\Sdk` | Flutter **tự tìm ra** vì đây đúng đường dẫn mặc định `%USERPROFILE%\AppData\Local\Android\Sdk` → không cần đặt `ANDROID_HOME` |
| Platform / build-tools | ✅ `android-34`, `android-36`, `android-36.1` / `34.0.0`, `35.0.0`, `36.0.0` | Thiếu bản nào Gradle tự tải (licenses đã accept sẵn) |
| JDK | ✅ JDK 21 (JBR kèm Android Studio) | Đủ cho Gradle & Flutter 3.x |
| `cmdline-tools` | ✅ `cmdline-tools\latest` | Gradle tự tải trong lần build đầu; `sdkmanager` trong đó **đã bị deprecate** (xem §2.3) |
| Flutter SDK | ✅ `C:\flutter` — Flutter 3.47.5 stable (Dart 3.13.4) | Cài bằng `git clone` ở đầu §2.1 |
| NDK | ❌ chưa có | **Bắt buộc cho lần build đầu** → cài theo **§2.3** (`tool\install_ndk.ps1`) |

### 2.2. Sinh phần nền tảng (android/, ios/) + cài dependency

```powershell
cd app-flutter
powershell -ExecutionPolicy Bypass -File tool/bootstrap_platforms.ps1 -RunChecks
```

Hoặc ngắn hơn (chạy được cả từ `cmd.exe`, PowerShell, và bấm đúp trong Explorer):

```bat
tool\bootstrap_platforms.cmd -RunChecks
```

> ⚠️ **Đừng gõ `pwsh`**: máy dev này chỉ có **Windows PowerShell 5.1** (`powershell.exe`),
> không có PowerShell 7 (`pwsh`), nên `pwsh` sẽ báo
> `The term 'pwsh' is not recognized as the name of a cmdlet...`

Script sẽ, theo thứ tự:

1. Kiểm tra `flutter` trên `PATH`.
2. **Sao lưu** `pubspec.yaml`, `analysis_options.yaml`, `.gitignore`, `README.md`, `lib/`,
   `test/`, `tool/` vào `%TEMP%` — để `flutter create` không ghi đè source đã viết.
3. `flutter create --empty --project-name smart_glasses_app --org com.bss --platforms=android,ios .`
4. Khôi phục source đã sao lưu.
5. Xoá `test/widget_test.dart` của template (file đó tham chiếu `MyApp()` không tồn tại ở app này).
6. Vá quyền BLE vào `android/app/src/main/AndroidManifest.xml` (gọi `patch_android_manifest.ps1`).
7. `flutter pub get` (và `flutter analyze` + `flutter test` nếu có `-RunChecks`).

### 2.3. Cài NDK (bắt buộc — cho lần build đầu)

`android/app/build.gradle.kts` khai báo `ndkVersion = flutter.ndkVersion`, mà **Flutter 3.47.5**
đòi NDK **`28.2.13676358`** (giá trị nằm ở `C:\flutter\packages\flutter_tools\gradle\src\main\kotlin\FlutterExtension.kt`
dòng 42). Máy này **chưa có NDK nào**, nên Gradle tự đi cài rồi **thất bại**:

```
Package ndk not found.
Package 28.2.13676358 not found.
> org.gradle.api.GradleException: Android sdkmanager did not install NDK 28.2.13676358 into ...
```

Nguyên nhân: từ **Android Studio 2026.1**, `sdkmanager` cũ chỉ còn là shim — nó in cảnh báo
*"The SDK Manager CLI tool (sdkmanager) is deprecated. Android CLI will be used instead."* rồi
chuyển sang **Android CLI** mới, mà CLI này không hiểu cú pháp `ndk;<version>`.

**Cách 1 — script có sẵn trong repo (khuyến nghị, có kiểm SHA-1):**

```powershell
cd app-flutter
powershell -ExecutionPolicy Bypass -File tool/install_ndk.ps1
```

Script tự làm hết: đọc phiên bản NDK mà Flutter đang đòi → tra `repository2-3.xml` của Google để
lấy URL + kích thước + SHA-1 → tải (≈713 MB) → **kiểm SHA-1** → giải nén bằng `tar.exe` (nhanh hơn
`Expand-Archive` nhiều) → đặt vào `%LOCALAPPDATA%\Android\Sdk\ndk\<version>` → kiểm
`source.properties` + `llvm-strip.exe`. Chạy lại lần hai sẽ in `[=] NDK ... da co san` và thoát.
Thêm `-DryRun` để xem trước URL (không tải), `-KeepZip` để giữ file zip.

**Cách 2 — bằng giao diện Android Studio:** `Tools → SDK Manager → SDK Tools` → tick
**NDK (Side by side)** → *Show Package Details* → tick đúng **28.2.13676358** → *Apply*.

Kiểm tra nhanh: `%LOCALAPPDATA%\Android\Sdk\ndk\28.2.13676358\source.properties` phải có
`Pkg.Revision = 28.2.13676358`. Trên máy này build đã **chạy thành công** sau khi cài
(`build\app\outputs\flutter-apk\app-debug.apk`, build lần đầu ~7 phút, các lần sau nhanh hơn).

### 2.4. Chạy trên điện thoại

> ⚠️ **Lần build đầu tiên cần NDK `28.2.13676358`** — nếu chưa cài, Gradle sẽ báo
> `Android sdkmanager did not install NDK 28.2.13676358`. Xem **§2.3** để cài (1 lệnh).

```powershell
flutter devices          # cắm điện thoại Android, bật USB debugging
flutter run              # hoặc: flutter run -d <device-id>
```

Luồng sử dụng: app tự quét ngay khi mở → danh sách thiết bị **đã lọc theo Service UUID của
kính** → chạm vào `SmartGlasses-OB` → màn hình theo dõi đổi màu nền theo mức cảnh báo.

### 2.5. Chạy bằng Android Studio

Điểm dễ sai nhất: **mở sai cấp thư mục**. Gốc repo `C:\Project\BSS` là project PlatformIO
(firmware C++), không phải project Flutter — phải mở đúng thư mục `app-flutter\` (nơi có
`pubspec.yaml`).

> **Trạng thái máy này**: Android Studio (2026.1) + Android SDK + JDK 21 **đã có sẵn** (bảng ở
> mục 2.1) ⇒ chỉ cần 4 việc: cài Flutter SDK → cài plugin Flutter → trỏ *Flutter SDK path* →
> chạy script bootstrap ở bước (b), rồi bấm ▶.

**a) Cài plugin + trỏ Flutter SDK**

1. Android Studio ≥ Hedgehog (2023.1). `File → Settings → Plugins` → cài **Flutter**
   (tự kéo theo **Dart**) → Restart IDE.
2. `File → Settings → Languages & Frameworks → Flutter` → **Flutter SDK path** = `C:\flutter`
   (thư mục chứa `bin\flutter.bat`). Không thấy mục "Flutter" ⇒ plugin ở bước 1 chưa cài xong.
3. `File → Settings → Tools → Terminal`: *Shell path* mặc định là `cmd.exe` — trong terminal của
   IDE hãy gọi `powershell -ExecutionPolicy Bypass -File tool\bootstrap_platforms.ps1 -RunChecks`,
   hoặc ngắn hơn `tool\bootstrap_platforms.cmd -RunChecks`. Máy này **không có `pwsh`**
   (PowerShell 7) nên đừng gõ `pwsh`.

**b) Sinh `android/` trước khi mở project**

Android Studio **không** build/run được khi thiếu `android/` + `.metadata` (repo hiện chưa có).
Mở terminal tích hợp (`Alt+F12`) **tại thư mục `app-flutter`** rồi chạy:

```powershell
powershell -ExecutionPolicy Bypass -File tool/bootstrap_platforms.ps1 -RunChecks
# hoac:  tool\bootstrap_platforms.cmd -RunChecks
```

Nếu đã trót mở project trước đó và Android Studio đang báo *Gradle sync failed* / không thấy
thiết bị: sau khi script chạy xong, chọn `File → Sync Project with Gradle Files` (hoặc đóng rồi
mở lại project). Cấu hình build do template Flutter sinh ra, không cần chỉnh tay.

**c) Mở project đúng cách**

`File → Open` → chọn `C:\Project\BSS\app-flutter` → (nếu IDE hỏi) *Trust Project* + chờ
`Dart Analysis` chạy xong. Android Studio tự gọi `flutter pub get`; nếu không, chạy tay
`flutter pub get`. Cửa sổ mở ở chế độ "Android project" (chỉ thấy Gradle, không thấy
`main.dart`) nghĩa là mở sai cấp thư mục.

**d) Chọn thiết bị**

> ⚠️ **Android Emulator không có phần cứng BLE** nên app luôn báo Bluetooth không khả dụng và
> danh sách quét rỗng. Bắt buộc dùng **điện thoại thật**.

- Điện thoại: `Cài đặt → Giới thiệu về điện thoại → nhấn 7 lần Số bản dựng` →
  `Tùy chọn nhà phát triển → Gỡ lỗi USB`; cắm cáp → bấm **Cho phép** ở hộp thoại RSA.
- Kiểm tra: `flutter devices` (hoặc `adb devices` — cần *Android SDK Platform-Tools* trong
  SDK Manager). Thiết bị sẽ tự hiện trong dropdown cạnh nút ▶.
- Máy không hiện: đổi cáp (nhiều cáp chỉ sạc được), đổi chế độ USB sang *Truyền tệp*,
  `flutter doctor -v` xem mục *Android toolchain*.

**e) Bấm chạy**

| Việc | Cách làm |
|---|---|
| Chạy app | Chọn **main.dart** + thiết bị ở thanh công cụ → ▶ (`Shift+F10`) |
| Debug Dart | 🐞 (`Shift+F9`) — đặt breakpoint được trong `lib/` |
| Hot Reload (giữ state) | Nút ⚡ hoặc `Ctrl+\` — sửa UI xong lưu file (`Ctrl+S`) rồi bấm |
| Hot Restart (reset state) | Nút ↻ hoặc `Ctrl+Shift+\` — dùng khi sửa `initState`/`dispose` |
| Xem log app | Tab **Run** (chứa `debugPrint('[BSS] ...')`), hoặc `View → Tool Windows → Logcat` |
| Đo cảm giác trễ thật | `Run → Edit Configurations → Additional run args`: `--profile` (debug mode chậm hơn) |

Nếu dropdown **không có "main.dart"**: chuột phải `lib/main.dart` → `Run 'main.dart'`, Android
Studio sẽ tự tạo run configuration cho lần sau. Lần build đầu Gradle tải dependency khá lâu
(5–15 phút); app cài xong sẽ hỏi quyền **Thiết bị gần đây** → phải bấm *Cho phép*.

**f) Lỗi thường gặp riêng khi dùng Android Studio**

| Triệu chứng | Nguyên nhân | Cách sửa |
|---|---|---|
| Mở project là *Gradle sync failed* ngay | Chưa có `android/` | Chạy bước (b) rồi `Sync Project with Gradle Files` |
| Không có mục Flutter trong Settings | Plugin chưa cài/ chưa restart | Bước (a) |
| ▶ mờ, "No devices found" | Emulator (không có BLE) hoặc chưa bật USB debugging | Dùng điện thoại thật, bước (d) |
| Nút ▶ báo *Error: No pubspec.yaml file found* | Mở sai thư mục | Mở lại ở `app-flutter\` (bước c) |
| `target of URI doesn't exist: 'package:flutter_blue_plus/...'` | Chưa `pub get` / cache bẩn | `flutter clean` → `flutter pub get` → `File → Invalidate Caches` |
| Quét được nhưng danh sách trống | Chưa cấp quyền, Android ≤ 11 tắt GPS, hoặc kính đang bị máy khác giữ kết nối | Mục 3 (quyền) + tắt BLE của máy kia |
| Sửa code mà bấm ⚡ không đổi | File chưa lưu (hot reload không tự lưu) | `Ctrl+S` rồi bấm lại; cần reset state thì dùng ↻ |

> Nếu bạn muốn build app Kotlin (`app-android/`) bằng Android Studio thì đó là **project khác**:
> `File → Open` → `C:\Project\BSS\app-android`. Hai app nên mở ở **hai cửa sổ Android Studio
> riêng**, không mở lồng nhau.

---

## 3. Nền tảng & quyền

| Hạng mục | Giá trị | Ghi chú |
|---|---|---|
| Flutter | 3.x (Dart >= 3.0) | Ràng buộc khai báo trong `pubspec.yaml` |
| Package BLE | `flutter_blue_plus ^1.32.12` → resolve `1.36.8` | Dòng **1.x** vì API dùng lớp `Guid` |
| Android `minSdk` | 21 trở lên (mặc định của Flutter) | Plugin không hỗ trợ thấp hơn |
| Android `compileSdk` | Theo template Flutter | Cần ≥ 34 để build plugin mới |
| iOS | 12+ | Cần thêm key `Info.plist` nếu build iOS |
| Vai trò BLE | Central (điện thoại) — Peripheral là ESP32-S3 | Không dùng Bluetooth Classic |

Quyền mà **app** phải khai báo trong `android/app/src/main/AndroidManifest.xml`
(`tool/patch_android_manifest.ps1` tự thêm, chạy lại nhiều lần không bị nhân đôi):

```xml
<!-- Android 12 (API 31) trở lên -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<!-- Android 11 trở xuống: bắt buộc có quyền vị trí mới quét được BLE -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
<uses-feature android:name="android.hardware.bluetooth_le" android:required="false" />
```

**Xin quyền lúc chạy**: plugin Android của `flutter_blue_plus` tự gọi
`requestPermissions` (nó implements `RequestPermissionsResultListener`) nên app **không cần**
`permission_handler`. Nếu máy vẫn từ chối, vào *Cài đặt → Ứng dụng → quyền Thiết bị gần đây*
bật tay rồi mở lại app.

**iOS** (nếu cần): thêm vào `ios/Runner/Info.plist`, nếu thiếu app sẽ bị kill ngay khi gọi BLE.

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>Ứng dụng dùng Bluetooth để nhận cảnh báo điểm mù từ kính thông minh.</string>
<key>NSBluetoothPeripheralUsageDescription</key>
<string>Ứng dụng dùng Bluetooth để nhận cảnh báo điểm mù từ kính thông minh.</string>
```

---

## 4. Đặc tả giao thức BLE (khớp tuyệt đối với firmware)

| Hạng mục | Giá trị | Nguồn phía firmware |
|---|---|---|
| Tên thiết bị | `SmartGlasses-OB` | `config.h` → `BLE_DEVICE_NAME` |
| Service UUID | `4fafc201-1fb5-459e-8fcc-c5c9c331914b` | `config.h` → `SERVICE_UUID` |
| Characteristic UUID | `beb5483e-36e1-4688-b7f5-ea07361b26a8` | `config.h` → `CHARACTERISTIC_UUID` |
| Thuộc tính | NOTIFY (ESP32 chỉ đẩy, không nhận lệnh) | `ble_service.cpp` |
| CCCD | `00002902-…-00805f9b34fb` (do stack tự ghi) | `BLE2902` |
| Payload | 4 byte, little-endian | `ble_notify()` |

### Bố cục 4 byte

| Byte | Ý nghĩa | Giá trị |
|---|---|---|
| 0 | `distance_mm` (LSB) | 0…255 |
| 1 | `distance_mm` (MSB) | 0…39 |
| 2 | `urgency_level` | 0 = an toàn, 1 = chú ý, 2 = cảnh báo, 3 = khẩn cấp |
| 3 | `object_class` | 0 = unknown (dành sẵn cho AI/CV) |

`distance_mm = byte0 | (byte1 << 8)`. Giá trị **9990 mm** nghĩa là cảm biến ngoài tầm đo
(`pulseIn` timeout) → app hiển thị "Ngoài tầm đo".

### Ngưỡng (firmware quyết định, đã có lọc trung bình 5 mẫu + hysteresis 10 cm)

| Mức | Khoảng cách | PWM motor rung | Nền UI |
|---|---|---|---|
| 0 — AN TOÀN | d > 150 cm | 0 | xanh `#2E7D32` |
| 1 — CHÚ Ý | 100 < d ≤ 150 cm | 80 | vàng `#F9A825` |
| 2 — CẢNH BÁO | 50 < d ≤ 100 cm | 160 | cam `#EF6C00` |
| 3 — KHẨN CẤP | 0 < d ≤ 50 cm | 255 | đỏ `#C62828` |

> App **không** tự tính lại mức từ khoảng cách. Nếu app tự tính, app và motor trên kính có
> thể báo hai trạng thái khác nhau vì firmware đã lọc + hysteresis.

---

## 5. Kiến trúc & luồng dữ liệu

```
ESP32-S3 ──notify──► flutter_blue_plus ──onValueReceived──► BleService
                                                              │  ObstacleEvent.fromBytes()
                                                              ▼
                                                    Stream<ObstacleEvent>
                                                              │
                                       StreamBuilder / listen ▼
                                                    widgets (ScanScreen, MonitorScreen)
```

- `BleService` là **nơi duy nhất** trong app import `flutter_blue_plus`. UI chỉ biết:
  `scanResults` (Stream), `connectionState` (Stream), `state` (giá trị hiện tại),
  `startScan()`, `connect(device)`, `disconnect()`, `events`.
- `main.dart` tạo **một** `BleService` duy nhất và truyền xuống các màn hình (không tạo trong
  `build()`, không dùng singleton toàn cục) → dễ thay bằng mock khi viết test widget.
- Payload sai định dạng bị `BleService` bắt lỗi và **bỏ qua gói đó** (đếm vào
  `droppedPayloads`), không làm chết luồng BLE.
- Quét dùng `withServices: [bssServiceUuid]` → OS lọc ở tầng hệ thống, danh sách không lẫn
  thiết bị lạ.

### Điểm mở rộng đã chừa sẵn

| Muốn thêm | Sửa ở đâu |
|---|---|
| Ghi log ra file / CSV | Thêm một `listen` trên `BleService.events` trong `main.dart` |
| Cảnh báo bằng âm thanh/rung điện thoại | Nghe `events` + `UrgencyLevel.isAlert` |
| Biểu đồ khoảng cách theo thời gian | Lưu N bản tin gần nhất trong một `ValueNotifier` ở tầng screen |
| Chạy nền (foreground service) | Thay `BleService` bằng bản dùng `flutter_foreground_task` + `flutter_blue_plus` background mode |
| Hiển thị pin/RSSI của kính | Thêm characteristic mới ở firmware, thêm hằng UUID tương ứng |

---

## 6. So sánh với bản Kotlin (`app-android/`)

| Hạng mục | `app-android/` (Kotlin) | `app-flutter/` (bản này) |
|---|---|---|
| UI | Jetpack Compose + Material 3 | Flutter widget (Material 3) |
| Kiến trúc | MVVM: BLE → Repository → ViewModel → Compose | BLE Service → Stream → StatefulWidget |
| Tầng BLE | `BssBleManager` (`Flow`, callback thủ công) | `BleService` (`Stream` của flutter_blue_plus) |
| Trạng thái | `sealed interface BleConnectionState` | `enum BleConnState` |
| Lịch sử/thống kê | Có (`ObstacleEventRepository`) | Chưa có (xem "Điểm mở rộng") |
| Unit test | 8 test (`ObstacleEventParserTest`) | 11 test (`obstacle_event_test.dart`) |
| Build | Gradle + AGP (đã build được trên máy này) | Cần cài Flutter SDK |

Điểm chung bắt buộc giữ: UUID, bố cục 4 byte, ngưỡng 150/100/50 cm, quy tắc "app chỉ hiển thị".

---

## 7. Những chỗ đã sửa/thêm so với code bạn gửi (và lý do)

Code bạn gửi giữ nguyên kiến trúc và hợp đồng dữ liệu; các thay đổi dưới đây đều **bắt buộc
hoặc có lợi rõ ràng** để app chạy được thật trên thiết bị:

| # | File | Thay đổi | Lý do |
|---|---|---|---|
| 1 | `main.dart` | `BleService` được tạo trong `State` (không phải trong `build()`) và `dispose()` có huỷ | `StatelessWidget.build()` chạy lại mỗi lần có gì thay đổi → tạo service mới, rò rỉ kết nối BLE, mất stream đang nghe |
| 2 | `services/ble_service.dart` | Bỏ `await` vô nghĩa trong `startScan()`; thêm `Timer` timeout + `stopScan()` để đưa trạng thái về `disconnected` | Từ flutter_blue_plus 1.15, `startScan()` trả về **ngay** sau khi gửi lệnh quét (không chờ hết timeout) → nếu không tự hẹn giờ, UI treo ở trạng thái "đang quét" mãi |
| 3 | `services/ble_service.dart` | `connect()` bọc `try/catch`, ném `BleException` có thông điệp tiếng Việt; `firstWhere` có `orElse` | Bản gốc để `firstWhere` ném `StateError` khó hiểu; nếu lỗi giữa đường thì app kẹt ở `connecting` |
| 4 | `services/ble_service.dart` | Thêm `device.cancelWhenDisconnected(_notifySub!)` | Khuyến nghị chính thức của plugin: tránh listen trùng → cùng một bản tin bị phát 2 lần ở lần kết nối sau |
| 5 | `services/ble_service.dart` | Thêm `state` (giá trị hiện tại), `droppedPayloads`, cờ `_disposed` | Màn hình mới mở cần biết trạng thái ngay (stream không replay); đếm gói lỗi để chẩn đoán nhiễu |
| 6 | `screens/monitor_screen.dart` | `StatelessWidget` → `StatefulWidget` có cache `_lastEvent`, banner "mất kết nối", nút ngắt kết nối | Stream là **broadcast không replay**: mỗi rebuild mà không cache sẽ nhấp nháy về "Đang chờ dữ liệu..."; banner giúp phân biệt "kính im lặng" và "đã mất kết nối" |
| 7 | `screens/scan_screen.dart` | Thêm `stopScan()` trong `dispose()`, bắt lỗi khi quét/kết nối, màn hình rỗng có checklist, hiện RSSI, quay lại thì quét tiếp | Không dừng quét khi rời màn hình sẽ hao pin; người dùng cần biết vì sao không thấy kính |
| 8 | `models/obstacle_event.dart` | Thêm `receivedAt`, `distanceText`, `isOutOfRange`, `urgencyLabel`, `objectLabel` và lớp `UrgencyLevel` (nhãn + mô tả khớp app Kotlin) | Tầng UI không phải tự xử lý chuỗi/ngưỡng; `isOutOfRange` nhận diện giá trị bão hoà 9990 mm của `pulseIn` |
| 9 | `models/obstacle_event.dart` | Payload **dài hơn** 4 byte vẫn đọc được (chỉ lấy 4 byte đầu) | Firmware thêm trường mới (ví dụ `object_class` thật từ AI) thì app cũ không vỡ |
| 10 | `pubspec.yaml` | Thêm `dev_dependencies` (`flutter_test`, `flutter_lints`), `version: 0.1.0+1`, thêm `analysis_options.yaml`, `.gitignore`, `test/`, `tool/` | Để `flutter analyze`/`flutter test` chạy được; `environment.sdk` và `flutter_blue_plus: ^1.32.12` giữ nguyên như bạn viết |

Phần **giữ nguyên có chủ đích**: `ObstacleEvent.fromBytes` vẫn ném `FormatException` khi thiếu
byte (tầng BLE bắt lỗi và bỏ qua gói), UUID trùng tên/giá trị với firmware, `Guid` không đổi
sang `String`.

> ⚠️ **Bẫy cần biết**: từ flutter_blue_plus 1.28, `Guid.toString()` trả về dạng **ngắn**
> (16-bit) khi UUID thuộc base UUID của Bluetooth SIG, nên **đừng so sánh UUID bằng
> `toString()`** — hãy so sánh trực tiếp `s.uuid == bssServiceUuid` như code hiện tại
> (`Guid` đã override `operator ==` để so sánh `str128`).

---

## 8. Kiểm thử

```powershell
flutter analyze                     # lint + phân tích tĩnh
flutter test                        # 11 unit test cho bộ giải mã payload
flutter test test/obstacle_event_test.dart
```

`test/obstacle_event_test.dart` phủ:

| Nhóm | Nội dung |
|---|---|
| Giải mã | little-endian (1284 mm → 128.4 cm), byte 2 = urgency, byte 3 = class |
| Bền vững | thiếu byte → `FormatException`; payload > 4 byte vẫn đọc được |
| Biên | 9990 mm = ngoài tầm đo; 0 mm là dữ liệu hợp lệ |
| `UrgencyLevel` | nhãn 4 mức, `isAlert(0) == false`, byte lạ (>3) coi là an toàn (fail-safe) |
| Khác | `receivedAt` được gán, `toString()` đủ thông tin để đọc log |

> **Trạng thái trên máy hiện tại**: máy này **chưa cài Flutter SDK**, nên `flutter analyze`
> và `flutter test` **chưa được chạy** — chúng sẽ chạy tự động ở bước
> `tool/bootstrap_platforms.ps1 -RunChecks`. Bản Kotlin tương ứng (`app-android/`) đã build
> và test thành công trên cùng máy, nên khi đã có SDK thì Flutter cũng chạy được.

---

## 9. Xử lý sự cố

| Hiện tượng | Nguyên nhân thường gặp / Cách xử lý |
|---|---|
| Danh sách quét trống | Kính chưa cấp nguồn hoặc đang kết nối thiết bị khác; Bluetooth điện thoại tắt; chưa cấp quyền khi app hỏi; đứng quá xa. App đã lọc theo Service UUID nên **chỉ hiện đúng kính BSS** |
| Bấm kết nối báo lỗi quyền | Vào *Cài đặt → Ứng dụng → quyền "Thiết bị gần đây"* bật tay, rồi mở lại app |
| `MissingPluginException` khi vừa thêm package | Hot reload không đủ — phải **stop hẳn app rồi `flutter run` lại** (hoặc `flutter clean`) |
| `Package ndk not found.` / `Package 28.2.13676358 not found.` / `Android sdkmanager did not install NDK 28.2.13676358` | Máy chưa có NDK, mà `sdkmanager` cũ **đã bị Android Studio 2026.1 deprecate** nên Gradle không tự cài được | Cài NDK bằng `tool\install_ndk.ps1` (hoặc SDK Manager → *NDK (Side by side)* → `28.2.13676358`) — chi tiết ở **§2.3** |
| `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user` khi `flutter run`/`adb install` | Điện thoại **Xiaomi/HyperOS** (máy này: `24090RA29G`, Android 16, HyperOS V816) chặn cài qua USB khi màn hình **đang tắt/khoá** — dialog xác nhận không hiện được nên hệ thống coi như người dùng đã huỷ | Mở khoá màn hình rồi chạy lại; bật *Cài đặt → Tuỳ chọn nhà phát triển → **Cài đặt qua USB*** (và *Gỡ lỗi USB (cài đặt bảo mật)* nếu có); khi dialog hiện trên máy thì bấm **Cài đặt/Cho phép**. Kiểm tra nhanh: `adb shell dumpsys power` → `mWakefulness` phải khác `Dozing` |
| `Warning: SDK processing. This version only understands SDK XML versions up to 3 but an SDK XML file of version 4 was encountered` | `package.xml` trong SDK được ghi lại theo schema **v4** (`.../repo/repository2/04`) bởi `cmdline-tools` đời mới (bản **23.0** mà Gradle tự tải ở lần build đầu), trong khi `sdklib` đi kèm **AGP 9.1.0** chỉ hiểu tới v3 | **Vô hại** — chỉ là cảnh báo, build vẫn thành công (đã kiểm chứng trên máy này). Bỏ qua; sẽ hết khi Flutter nâng lên AGP có `sdklib` hiểu v4 |
| Build lần đầu chậm (5–8 phút) | Gradle tải distribution + AGP + dependency + engine Flutter | Bình thường. Các lần sau nhanh hơn nhiều (chỉ build lại phần đã đổi) |
| `pwsh : The term 'pwsh' is not recognized...` | Máy chỉ có Windows PowerShell 5.1, không có PowerShell 7 | Gọi `powershell -ExecutionPolicy Bypass -File <script>.ps1` hoặc dùng `tool\bootstrap_platforms.cmd -RunChecks` |
| `Split-Path : Cannot bind argument to parameter 'Path' because it is an empty string` | Bug ở bản script cũ: trên **PS 5.1**, `$PSScriptRoot` rỗng khi tính giá trị mặc định của `param(...)` nếu script có `[CmdletBinding()]` | Đã sửa (đường dẫn tính **sau** khi bind tham số, có fallback cho PS 5.1 & 7) — lấy lại bản script mới nhất |
| Luôn hiện "Ngoài tầm đo" (999.0 cm) | Cảm biến siêu âm không phản hồi: kiểm tra dây `TRIG_PIN`/`ECHO_PIN` và nguồn 5V của HC-SR04 |
| Số cm nhảy loạn | Bình thường với siêu âm; firmware đã lọc trung bình 5 mẫu + hysteresis 10 cm. Xem thêm RSSI trong màn hình quét |
| Muốn xem log BLE chi tiết | Thêm vào `main()`: `FlutterBluePlus.setLogLevel(LogLevel.verbose, color: false);` |
| Nâng lên `flutter_blue_plus` 2.x | Dòng 2.x **bỏ** lớp `Guid` (dùng `String` UUID) → phải sửa `ble_service.dart`. Nếu chưa muốn sửa, giữ `^1.32.12` |
| Chạy trên Windows/desktop | Gói này không hỗ trợ Windows; cần `flutter_blue_plus_winrt`. App này thiết kế cho Android/iOS |


