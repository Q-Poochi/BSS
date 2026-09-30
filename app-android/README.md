# BSS Companion — App Android (Kotlin + Jetpack Compose)

App điện thoại đi kèm cho hệ thống **BSS (Blind Spot System)** — nhận bản tin cảnh báo điểm mù
do firmware ESP32-S3 trên Seeed XIAO đẩy lên qua BLE và hiển thị cho người dùng.

> Đây là **khung app (skeleton)** đã chạy được: quét – kết nối – subscribe notify – giải mã –
> hiển thị. Phần mở rộng (AI phân loại vật thể, foreground service, ghi log ra file…) được
> chừa sẵn điểm nối, xem mục [Điểm mở rộng](#-điểm-mở-rộng).

---

## 1. Công nghệ & môi trường

| Thành phần | Phiên bản | Ghi chú |
|---|---|---|
| Ngôn ngữ | Kotlin 2.2.21 | Không dùng Kotlin Multiplatform, chỉ Android native |
| UI | Jetpack Compose (BOM 2026.06.01) + Material 3 | 100% Compose, không có file XML layout |
| Kiến trúc | MVVM 1 chiều: `BLE → Repository → ViewModel → Compose UI` | UI là composable thuần (stateless) |
| Build | Gradle 8.13 + AGP 8.13.2, Kotlin DSL + Version Catalog | `gradle/libs.versions.toml` |
| JDK | 17 (compile) / JDK 21 để chạy Gradle | Android Studio JBR 21 |
| SDK | `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26` | minSdk 26 vì dùng `java.time` + Adaptive Icon |
| BuildConfig field | `BLE_DEVICE_NAME = "SmartGlasses-OB"` | Sinh tự động, không hard-code trong source |

### Nền tảng BLE đã xử lý
- **Android 12 (API 31) trở lên**: `BLUETOOTH_SCAN` (`neverForLocation`) + `BLUETOOTH_CONNECT`.
- **Android 11 trở xuống**: `BLUETOOTH`, `BLUETOOTH_ADMIN` + `ACCESS_FINE_LOCATION`
  (hệ thống bắt buộc có quyền vị trí mới cho quét BLE).
- Callback `onCharacteristicChanged` được override **cả 2 overload** để chạy đúng trên API < 33 và ≥ 33.

---

## 2. Cấu trúc thư mục

```
app-android/
├── settings.gradle.kts              # Định nghĩa project + repository (google, mavenCentral)
├── build.gradle.kts                 # Khai báo plugin dùng chung (apply false)
├── gradle.properties                # JVM args (UTF-8), build cache, AndroidX
├── local.properties                 # sdk.dir — KHÔNG commit (đã gitignore)
├── gradlew / gradlew.bat            # Gradle Wrapper 8.13
├── gradle/
│   ├── libs.versions.toml           # Version Catalog (nguồn version duy nhất)
│   └── wrapper/                     # gradle-wrapper.jar + .properties
└── app/                             # Module ứng dụng
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml  # Quyền BLE, uses-feature bluetooth_le, Activity
        │   ├── res/                 # strings / colors / themes / icon (Adaptive Icon vector)
        │   └── java/com/bss/companion/
        │       ├── MainActivity.kt          # Single-activity: xin quyền → giao cho Compose
        │       ├── ble/
        │       │   ├── BssBleContract.kt    # UUID + bố cục payload (đối chiếu config.h)
        │       │   ├── UrgencyLevel.kt      # 4 mức cảnh báo 0..3
        │       │   ├── ObstacleEvent.kt     # Data class + bộ giải mã 4 byte
        │       │   ├── BleConnectionState.kt# Máy trạng thái kết nối (sealed interface)
        │       │   └── BssBleManager.kt     # Scan / connect / subscribe / RSSI / Flow
        │       ├── data/
        │       │   └── ObstacleEventRepository.kt  # Bản mới nhất + lịch sử + thống kê
        │       └── ui/
        │           ├── BssApp.kt            # Bọc theme + collect state
        │           ├── BlindSpotScreen.kt   # Màn hình chính (stateless)
        │           ├── BssViewModel.kt      # Gom 6 luồng dữ liệu → BssUiState
        │           ├── BlindSpotScreenPreview.kt # @Preview trong Android Studio
        │           ├── components/          # ConnectionPanel, DistancePanel, HistorySection
        │           └── theme/               # Color, Theme, Type (+ màu theo mức cảnh báo)
        └── test/java/com/bss/companion/ble/
            └── ObstacleEventParserTest.kt   # 8 unit test cho bộ giải mã payload
```

---

## 3. Đặc tả giao thức BLE (khớp tuyệt đối với firmware)

| Hạng mục | Giá trị | Nguồn phía firmware |
|---|---|---|
| Tên thiết bị | `SmartGlasses-OB` | `config.h` → `BLE_DEVICE_NAME` |
| Service UUID | `4fafc201-1fb5-459e-8fcc-c5c9c331914b` | `config.h` → `SERVICE_UUID` |
| Characteristic UUID | `beb5483e-36e1-4688-b7f5-ea07361b26a8` | `config.h` → `CHARACTERISTIC_UUID` |
| CCCD | `00002902-0000-1000-8000-00805f9b34fb` | `BLE2902` |
| Thuộc tính | NOTIFY-only (ESP32 chỉ đẩy, không nhận lệnh) | `ble_service.cpp` |
| Payload | 4 byte, little-endian | `ble_notify()` |

### Bố cục 4 byte

| Byte | Ý nghĩa | Giá trị |
|---|---|---|
| 0 | `distance_mm` (LSB) | 0…255 |
| 1 | `distance_mm` (MSB) | 0…39 |
| 2 | `urgency_level` | 0 = An toàn, 1 = Chú ý, 2 = Cảnh báo, 3 = Khẩn cấp |
| 3 | `object_class` | 0 = unknown (chờ tầng AI/CV) |

`distance_mm = byte0 | (byte1 << 8)` — giá trị `9990` (999.0 cm) là dấu hiệu `pulseIn()`
timeout trên firmware, app hiển thị `--.-` thay vì số vô nghĩa.

### Ngưỡng & thông số firmware (chỉ để đối chiếu, app không tự tính lại)
`THRESHOLD_CHU_Y_CM = 150` · `THRESHOLD_GAN_CM = 100` · `THRESHOLD_KHAN_CAP_CM = 50`
· `FILTER_WINDOW = 5` · `HYSTERESIS_CM = 10` · `SENSOR_READ_INTERVAL_MS = 100`

> **Nguyên tắc:** mức cảnh báo do **firmware quyết định** (đã qua lọc trung bình trượt + trễ
> hysteresis). App chỉ hiển thị — tránh việc app và động cơ rung báo hai trạng thái khác nhau.

---

## 4. Build & chạy

### 4.1. Mở bằng Android Studio
`File → Open…` → chọn thư mục `c:\Project\BSS\app-android` → đồng ý "Trust Project".
Studio sẽ tự dùng `local.properties` để tìm SDK và tải đúng bản Gradle 8.13 qua wrapper.

### 4.2. Build bằng dòng lệnh (Windows PowerShell)
```powershell
# Bắt buộc: trỏ JAVA_HOME vào JDK 17+ (ở đây dùng JBR đi kèm Android Studio)
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
Set-Location 'c:\Project\BSS\app-android'

.\gradlew.bat :app:assembleDebug        # APK debug
.\gradlew.bat :app:testDebugUnitTest    # Chạy 8 unit test của bộ giải mã payload
.\gradlew.bat :app:installDebug         # Cài thẳng lên máy đang cắm USB (adb)
.\gradlew.bat :app:assembleRelease      # APK release
```

APK debug nằm tại `app/build/outputs/apk/debug/app-debug.apk`.

### 4.3. Kiểm tra trên máy thật
1. Nạp firmware vào XIAO ESP32-S3 và cấp nguồn (kính bắt đầu quảng bá `SmartGlasses-OB`).
2. Cài app, mở lên và **cấp quyền Bluetooth** khi được hỏi.
3. Bấm **Quét thiết bị** → chọn `SmartGlasses-OB` trong danh sách → bấm **Kết nối**.
4. Đưa tay/vật cản qua lại trước cảm biến ToF: số đo, thanh tiến trình, màu mức cảnh báo,
   RSSI và bảng lịch sử sẽ cập nhật theo thời gian thực.

### 4.4. Kết quả đã kiểm chứng trên máy dựng khung này
| Hạng mục | Kết quả |
|---|---|
| `:app:assembleDebug` | **BUILD SUCCESSFUL** — 39 task, không có warning biên dịch |
| APK tạo ra | `app-debug.apk` ≈ 11.07 MB |
| `:app:testDebugUnitTest` | **8/8 PASS**, 0 failure, 0 error (`ObstacleEventParserTest`) |
| SDK Platform 36 | Đã được AGP tự động cài vào `%LOCALAPPDATA%\Android\Sdk\platforms\android-36` |
| Gradle wrapper | 8.13 (tái sử dụng distribution đã có trong `~/.gradle/wrapper/dists`) |

---

## 5. Luồng dữ liệu

```
ESP32-S3 (GATT Server, notify 4 byte)
        │  BLE notify
        ▼
BssBleManager            (tầng BLE: scan → connect → CCCD → giải mã)
        │  SharedFlow<ObstacleEvent>   +   StateFlow<BleConnectionState>
        ▼
ObstacleEventRepository  (bản mới nhất · lịch sử có lọc · thống kê phiên)
        │  StateFlow
        ▼
BssViewModel             (combine 6 luồng → StateFlow<BssUiState>)
        │
        ▼
BlindSpotScreen (Compose, stateless)  →  DistancePanel / ConnectionPanel / HistorySection
```

**Chống nhiễu & tiết kiệm pin phía app**
- Firmware đã tự giới hạn tần suất gửi (đổi mức / dịch chuyển > 10 cm / nhịp tim 1 giây).
- App lọc thêm một lớp: chỉ ghi vào **lịch sử** khi mức cảnh báo đổi hoặc khoảng cách lệch > 10 cm;
  lịch sử giữ tối đa 200 bản ghi, màn hình chỉ vẽ 20 dòng mới nhất.
- Quét tự dừng sau **15 giây**; RSSI chỉ poll mỗi **2 giây** khi đã kết nối.
- `collectAsStateWithLifecycle` → UI ngừng thu thập dữ liệu khi app xuống nền.

---

## 6. Điểm mở rộng

| Muốn thêm gì | Sửa ở đâu |
|---|---|
| Ghi log ra file CSV để phân tích | Thêm hàm trong `ObstacleEventRepository.onEvent()` |
| Rung chuông / Text-to-Speech khi mức 3 | Thêm `SoundPool`/`TextToSpeech` trong `BssApp` hoặc một `FeedbackManager` mới |
| Phân loại vật thể (AI/CV) | Firmware điền `object_class` (byte 3) — app đã có sẵn `objectLabel` để hiển thị |
| Đổi UUID / tên thiết bị | Sửa `BssBleContract` (nhớ đồng bộ với `include/config.h`) |
| Chạy nền khi tắt màn hình | Thêm `ForegroundService` (`connectedDevice` type) gọi vào `BssBleManager` |
| Đổi giao diện | Chỉ sửa `ui/theme/*` và `ui/components/*`, không cần chạm tầng BLE |

---

## 7. Lưu ý khi commit

- `local.properties` chứa `sdk.dir` của máy cá nhân → **không commit** (đã có trong `.gitignore`).
- `gradle/wrapper/gradle-wrapper.jar` thì **nên commit** để máy khác clone về chạy được ngay.
- `app/build/`, `.gradle/` đã được gitignore.

## 8. Liên kết tài liệu

- Kiến trúc firmware: `docs/BSS_Firmware_Architecture_Report.md` (v1.1)
- Firmware gốc: `src/` + `include/config.h` tại thư mục cha của repo.
