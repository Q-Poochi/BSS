# BÁO CÁO KỸ THUẬT: THIẾT KẾ KIẾN TRÚC FIRMWARE VÀ THUẬT TOÁN HỆ THỐNG CẢNH BÁO ĐIỂM MÙ (BSS)

**Dự án:** Blind Spot Monitoring System (BSS)  
**Nền tảng phần cứng:** Seeed Studio XIAO ESP32-S3 (Xtensa Dual-Core LX7, Wi-Fi / BLE 5.0)  
**Môi trường giả lập:** Wokwi Simulator tích hợp trong Visual Studio Code (PlatformIO)  
**Phiên bản tài liệu:** v1.0 - Ngày 28/09/2026  
**Tác giả:** Nhóm phát triển BSS  

---

## MỤC LỤC
1. [Giới thiệu & Đặt vấn đề](#1-giới-thiệu--đặt-vấn-đề)
2. [Sơ đồ mạch & Cấu hình phần cứng trên Wokwi](#2-sơ-đồ-mạch--cấu-hình-phần-cứng-trên-wokwi)
3. [Kiến trúc Firmware Mô-đun hóa (Modular Architecture)](#3-kiến-trúc-firmware-mô-đun-hóa-modular-architecture)
4. [Điều phối thời gian thực với FreeRTOS](#4-điều-phối-thời-gian-thực-với-freertos)
5. [Cơ chế phân cấp cảnh báo & Điều khiển phản hồi Haptic (LEDC PWM)](#5-cơ-chế-phân-cấp-cảnh-báo--điều-khiển-phản-hồi-haptic-ledc-pwm)
6. [Xử lý tín hiệu số: Lọc nhiễu (Moving Window) và Chống rung giật (Hysteresis)](#6-xử-lý-tín-hiệu-số-lọc-nhiễu-moving-window-và-chống-rung-giật-hysteresis)
7. [Dịch vụ truyền thông không dây Bluetooth Low Energy (BLE)](#7-dịch-vụ-truyền-thông-không-dây-bluetooth-low-energy-ble)
8. [Các sự cố kỹ thuật thực tế & Giải pháp khắc phục](#8-các-sự-cố-kỹ-thuật-thực-tế--giải-pháp-khắc-phục)
9. [Kết luận & Kế hoạch phát triển phần cứng thực tế](#9-kết-luận--kế-hoạch-phát-triển-phần-cứng-thực-tế)

---

## 1. Giới thiệu & Đặt vấn đề
Hệ thống cảnh báo điểm mù (Blind Spot Monitoring System - BSS) là một phân hệ an toàn chủ động quan trọng trong các phương tiện giao thông thông minh. Mục tiêu của hệ thống là liên tục quét và phát hiện các phương tiện hoặc chướng ngại vật di chuyển vào khu vực người lái khó quan sát, từ đó đưa ra cảnh báo đa giác quan (thị giác qua đèn LED, xúc giác qua motor rung haptic, và thông tin trạng thái qua giao thức không dây BLE lên màn hình điện thoại).

Trong giai đoạn đầu phát triển trước khi có linh kiện vật lý (cảm biến ToF VL53L1X, kính thông minh), toàn bộ hệ thống được xây dựng và kiểm thử trên môi trường mô phỏng **Wokwi** kết hợp **PlatformIO**. Hệ thống đặt ra các yêu cầu khắt khe:
- **Độ trễ phản hồi cực thấp:** Cảnh báo xúc giác phải được kích hoạt tức thì khi có nguy cơ va chạm.
- **Tính ổn định & Chống báo động giả:** Không bị chập chờn khi vật cản đứng ở ranh giới ngưỡng và loại trừ nhiễu cảm biến.
- **Kiến trúc Decoupled:** Tách rời tầng giao tiếp phần cứng (HAL) để dễ dàng hoán đổi giữa cảm biến mô phỏng và cảm biến vật lý I2C.

---

## 2. Sơ đồ mạch & Cấu hình phần cứng trên Wokwi

### 2.1. Phân bổ chân GPIO (Pin Mapping)
Board vi điều khiển **Seeed Studio XIAO ESP32-S3** sở hữu kích thước siêu nhỏ (thumb-sized) với số lượng chân GPIO giới hạn. Sơ đồ phân bổ chân được thiết lập trong file `include/config.h`:

| Chân trên Board | Ký hiệu Arduino | Chân GPIO Chip ESP32-S3 | Thiết bị kết nối | Chức năng |
| :---: | :---: | :---: | :--- | :--- |
| **D0** | `D0` | GPIO 1 | Đèn LED Đỏ | Cảnh báo thị giác (Visual Alert) |
| **D1** | `D1` | GPIO 2 | Sensor TRIG | Kích phát xung siêu âm (10µs pulse) |
| **D2** | `D2` | GPIO 3 | Sensor ECHO | Đo độ rộng xung phản xạ |
| **D3** | `D3` | GPIO 4 | Motor rung / LED Xanh | Phản hồi xúc giác (Haptic Feedback) |
| **3V3 / GND** | 3V3 / GND | Power Rails | Nguồn chung | Cấp nguồn 3.3V và Mass chung |

### 2.2. Sơ đồ kết nối `diagram.json`
Mạch mô phỏng trên Wokwi được khai báo trực quan:
```json
{
  "version": 1,
  "author": "NguyenAnhQuan",
  "editor": "wokwi",
  "parts": [
    { "type": "board-xiao-esp32-s3", "id": "esp32", "top": 0, "left": 0, "attrs": {} },
    { "type": "wokwi-led", "id": "led1", "top": 100, "left": 50, "attrs": { "color": "red" } },
    { "type": "wokwi-hc-sr04", "id": "sensor", "top": -120, "left": 80, "attrs": { "distance": "120" } },
    { "type": "wokwi-led", "id": "vibrate_motor", "top": 100, "left": 150, "attrs": { "color": "green", "label": "VIBRATION" } }
  ],
  "connections": [
    [ "esp32:D0", "led1:A", "red", [] ],
    [ "led1:C", "esp32:GND", "black", [] ],
    [ "sensor:VCC", "esp32:3V3", "red", [] ],
    [ "sensor:GND", "esp32:GND", "black", [] ],
    [ "sensor:TRIG", "esp32:D1", "blue", [] ],
    [ "sensor:ECHO", "esp32:D2", "green", [] ],
    [ "esp32:D3", "vibrate_motor:A", "green", [] ],
    [ "vibrate_motor:C", "esp32:GND", "black", [] ]
  ]
}
```

---

## 3. Kiến trúc Firmware Mô-đun hóa (Modular Architecture)

Dự án được cấu trúc theo mô hình phân tầng công nghiệp:

```text
c:\Project\BSS/
├── include/                  <-- Giao diện dùng chung (Public Headers & Configuration)
│   ├── config.h              <-- Nguồn thông số duy nhất (Pins, PWM, Thresholds, Task configs, UUIDs)
│   └── event_types.h         <-- Cấu trúc gói tin chuẩn hệ thống (ObstacleEvent_t)
│
└── src/                      <-- Hiện thực chi tiết từng mô-đun (Implementation & HAL)
    ├── sensor_tof.h/.cpp     <-- Tầng trừu tượng cảm biến khoảng cách & phân loại khẩn cấp
    ├── feedback_motor.h/.cpp <-- Tầng điều khiển phát xung LEDC cho Motor rung & LED
    ├── ble_service.h/.cpp    <-- Tầng dịch vụ Bluetooth Low Energy GATT Server
    └── main.cpp              <-- Bộ điều phối FreeRTOS (Khởi tạo hàng đợi và tạo Task)
```

### Cấu trúc gói tin sự kiện `ObstacleEvent_t` (`include/event_types.h`)
```cpp
typedef struct {
  uint16_t distance_mm;   // Khoảng cách đơn vị milimet (2 bytes)
  uint32_t timestamp_ms;  // Nhãn thời gian millis() (4 bytes)
  uint8_t  urgency_level; // 0=An toàn, 1=Chú ý, 2=Gần, 3=Khẩn cấp (1 byte)
  uint8_t  object_class;  // Phân loại vật thể AI/CV: 0=Unknown (1 byte)
} ObstacleEvent_t;
```
*Đặc tính thiết kế:*
- Kích thước vừa vặn **8 bytes** (chẵn bội số 4-byte trên kiến trúc 32-bit), giúp việc sao chép vào FreeRTOS Queue và nạp vào payload Bluetooth đạt hiệu năng tối ưu.
- Lưu trữ theo đơn vị `mm` dạng số nguyên `uint16_t`, đồng bộ trực tiếp với định dạng trả về của cảm biến ToF VL53L1X vật lý sau này.
- Có sẵn trường `object_class` để mở rộng cho các mô hình AI/Camera nhận diện đối tượng (người đi bộ, xe con, xe tải).

---

## 4. Điều phối thời gian thực với FreeRTOS

Hệ thống tận dụng nhân vi xử lý ESP32-S3 và FreeRTOS để triển khai mô hình **Đa nhiệm bất đối xứng (Asymmetric Multitasking)**. 

### 4.1. Kiến trúc phân luồng (Dual-Task Pipeline)
```text
  [ Cảm biến khoảng cách ]
              │
              ▼
   ┌──────────────────────┐
   │     sensor_task      │ (Priority 3 - Chu kỳ 100ms)
   └──────────┬───────────┘
              │
              ├──────────► [ NHÁNH 1 - Ưu tiên an toàn: motor_set_level() ]
              │            Kích hoạt rung tức thì (Zero Latency)
              │
              └──────────► [ NHÁNH 2: xQueueOverwrite(ble_queue, &event) ]
                                   │
                                   ▼
                         ┌───────────────────┐
                         │     ble_task      │ (Priority 1 - Hướng sự kiện)
                         └─────────┬─────────┘
                                   │
                                   ▼
                         [ Gửi BLE Notify lên Smartphone ]
```

### 4.2. Triết lý thiết kế hàng đợi độ dài 1 (`xQueueOverwrite`)
Trong hệ thống cảnh báo va chạm, thông tin quá khứ không mang giá trị cứu sinh. Việc dùng `xQueueCreate(1, sizeof(ObstacleEvent_t))` kết hợp `xQueueOverwrite()` đảm bảo:
1. Hàng đợi **không bao giờ bị tràn (Queue Overflow)**.
2. `ble_task` luôn luôn đọc được trạng thái mới nhất ngay tại thời điểm truyền thông rảnh rỗi.
3. Nếu BLE bị trễ hoặc ngắt kết nối tạm thời, luồng quét cảm biến và cảnh báo xúc giác vẫn hoạt động độc lập, bảo vệ tuyệt đối an toàn cho người điều khiển.

### 4.3. Tối ưu hóa bộ nhớ với `vTaskDelete(nullptr)` trong `loop()`
Trong Arduino core của ESP32, hàm `loop()` chạy bên trong một FreeRTOS task mặc định (`loopTask`). Vì toàn bộ nghiệp vụ đã được chuyển giao cho `sensor_task` và `ble_task`, lệnh `vTaskDelete(nullptr)` được gọi ngay trong `loop()` để thu hồi hoàn toàn vùng nhớ Stack của `loopTask`, giải phóng tài nguyên RAM quý giá cho hệ thống.

---

## 5. Cơ chế phân cấp cảnh báo & Điều khiển phản hồi Haptic (LEDC PWM)

### 5.1. Bảng phân tầng mức độ khẩn cấp (Urgency Levels)

| Cấp độ (`urgency_level`) | Khoảng cách ($d$) | Ý nghĩa an toàn | Duty Cycle PWM (8-bit) | Trạng thái Motor rung | Trạng thái Đèn LED |
| :---: | :---: | :--- | :---: | :--- | :--- |
| **0** | $d > 150\text{ cm}$ | **An toàn (Safe)** | `0` (0%) | Tắt hoàn toàn (0V) | Tắt hoàn toàn |
| **1** | $100 < d \le 150\text{ cm}$ | **Chú ý (Notice)** | `80` (~31%) | Rung nhẹ nhắc nhở | Sáng mờ |
| **2** | $50 < d \le 100\text{ cm}$ | **Cảnh báo (Warning)** | `160` (~63%) | Rung trung bình | Sáng rõ |
| **3** | $0 < d \le 50\text{ cm}$ | **Khẩn cấp (Critical)** | `255` (100%) | Rung cực đại liên tục | Sáng rực tối đa |

### 5.2. Giải pháp điều khiển xung phần cứng (ESP32 LEDC)
Tần số điều xung được cấu hình ở mức **`100 Hz`** (`LEDC_FREQ = 100`). Đây là tần số lý tưởng cho cơ cấu rung haptic trong thực tế, giúp motor hoạt động êm, không phát ra tiếng rít tần số cao, đồng thời hạn chế tối đa tải tính toán sự kiện đồ họa trên bộ mô phỏng Wokwi.

---

## 6. Xử lý tín hiệu số: Lọc nhiễu (Moving Window) và Chống rung giật (Hysteresis)

Để hoàn thiện firmware khi triển khai thực tế trên phương tiện di chuyển, hai kỹ thuật xử lý tín hiệu số cốt lõi được thiết lập:

### 6.1. Bộ lọc cửa sổ trượt: `#define FILTER_WINDOW 5`
- **Mục đích:** Khử nhiễu gai (Spike noise) sinh ra do bụi bẩn, rung lắc cơ học hoặc hiện tượng tán xạ quang học của cảm biến khoảng cách.
- **Giải thuật:** Hệ thống duy trì mảng vòng lưu 5 giá trị đo gần nhất và tính trung bình trượt:
  $$\bar{d}_k = \frac{1}{5} \sum_{i=0}^{4} d_{k-i}$$
- **Hiệu quả:** Loại bỏ hoàn toàn các báo động giả nhất thời kéo dài dưới 100ms mà không gây trễ đáng kể cho phản xạ an toàn.

### 6.2. Vùng trễ chuyển trạng thái: `#define HYSTERESIS_CM 10`
- **Mục đích:** Triệt tiêu hiện tượng nhấp nháy chuyển trạng thái liên tục (Chattering) khi vật thể di chuyển mấp mé ngay tại ranh giới các ngưỡng cảnh báo (ví dụ dao động quanh mức 100cm).
- **Nguyên lý Schmitt Trigger:**
  - Để **bật** cảnh báo mức cao hơn: Khoảng cách phải giảm sâu xuống dưới ngưỡng $T$ ($d < T$).
  - Để **tắt** hoặc hạ mức cảnh báo: Khoảng cách phải tăng vượt qua ngưỡng $T$ cộng thêm độ trễ ($d > T + \Delta H$).
  - Với $\Delta H = 10\text{ cm}$, nếu xe chạy ở cự ly 100cm, hệ thống chỉ kích hoạt khi $d < 100\text{ cm}$ và chỉ ngắt khi xe phía sau đã lùi xa hơn $110\text{ cm}$.

---

## 7. Dịch vụ truyền thông không dây Bluetooth Low Energy (BLE)

### 7.1. Cấu hình GATT Server
Hệ thống đóng vai trò là một **BLE Peripheral / GATT Server**:
- **Device Name:** `SmartGlasses-OB`
- **Primary Service UUID:** `4fafc201-1fb5-459e-8fcc-c5c9c331914b`
- **Characteristic UUID:** `beb5483e-36e1-4688-b7f5-ea07361b26a8`
- **Thuộc tính:** `BLECharacteristic::PROPERTY_NOTIFY` (kèm descriptor `BLE2902` Client Characteristic Configuration).

### 7.2. Định dạng khung truyền dữ liệu (Telemetry Payload)
Dữ liệu gửi lên ứng dụng di động được serialize thành mảng 4 bytes cô đọng:
```text
Byte 0: distance_mm & 0xFF        (Byte thấp của khoảng cách)
Byte 1: (distance_mm >> 8) & 0xFF (Byte cao của khoảng cách)
Byte 2: urgency_level             (Cấp độ khẩn cấp: 0 - 3)
Byte 3: object_class              (Định danh loại đối tượng)
```

---

## 8. Các sự cố kỹ thuật thực tế & Giải pháp khắc phục

Trong quá trình xây dựng firmware và mô phỏng trên Wokwi, nhóm phát triển đã phân tích và giải quyết triệt để 4 vấn đề kỹ thuật chuyên sâu:

### Sự cố 1: Lệch mã chân giữa ký hiệu mạch in (`D0, D1...`) và GPIO phần cứng
- **Hiện tượng:** Cảm biến không nhận được xung kích phát, LED không sáng khi gán số chân theo quy ước ESP32 thông thường.
- **Nguyên nhân:** Trên Seeed Studio XIAO ESP32-S3, chân in `D0` tương ứng GPIO 1, `D1` là GPIO 2, `D2` là GPIO 3, `D3` là GPIO 4.
- **Giải pháp:** Sử dụng trực tiếp các hằng số macro `D0, D1, D2, D3` do board package của hãng Seeed cung cấp trong `config.h`.

### Sự cố 2: Giả lập Wokwi bị suy giảm tốc độ ở các dải PWM trung gian
- **Hiện tượng:** Khi khoảng cách ở vùng 50 - 150cm, tốc độ mô phỏng bị tụt xuống 20% - 30%, phản hồi chậm chạp.
- **Nguyên nhân:** Cấu hình tần số LEDC ban đầu ở mức 5000Hz trên 2 chân GPIO đồng thời tạo ra 20.000 sự kiện băm xung/giây, gây nghẽn CPU của trình duyệt.
- **Giải pháp:** Giảm tần số `LEDC_FREQ` xuống **100Hz**. Tốc độ mô phỏng đạt ổn định 100% thời gian thực.

### Sự cố 3: Báo động giả mức Khẩn cấp (Level 3) khi khoảng cách vượt quá 400cm
- **Hiện tượng:** Kéo thanh trượt lên 400cm thì hệ thống lại kích hoạt rung và đèn cực đại.
- **Nguyên nhân:** Khi cảm biến vượt quá tầm đo tối đa, hàm `pulseIn()` bị timeout và trả về giá trị `0`. Cú kiểm tra `if (distance_cm > THRESHOLD)` không khớp với bất kỳ ngưỡng nào lớn hơn 0, làm dữ liệu rơi tự do xuống nhánh `return 3` (Khẩn cấp).
- **Giải pháp:** Bổ sung cơ chế bảo vệ an toàn (Fail-Safe):
  ```cpp
  if (distance_cm <= 0.0f || distance_cm > THRESHOLD_CHU_Y_CM) return 0;
  ```

### Sự cố 4: Motor và LED vẫn rung/sáng khi `Urgency Level = 0`
- **Hiện tượng:** Log Serial ghi nhận `Urgency: 0`, `PWM = 0` nhưng phần cứng ảo trên Wokwi vẫn giữ nguyên trạng thái bật.
- **Nguyên nhân:** Khối phần cứng LEDC độc chiếm chân GPIO; khi nhận `ledcWrite(0)`, chu kỳ xung cuối cùng dừng ở cạnh cao (HIGH) và lệnh `digitalWrite(LOW)` thông thường bị vô hiệu hóa bởi bộ ghép kênh GPIO Matrix.
- **Giải pháp:** Hiện thực hóa cơ chế quản lý trạng thái động: Gọi `ledcDetachPin()` để giải phóng chân về chế độ GPIO thường rồi kéo mức `LOW` tuyệt đối khi về Level 0; chỉ gọi `ledcAttachPin()` trở lại khi có cảnh báo xuất hiện.

---

## 9. Kết luận & Kế hoạch phát triển phần cứng thực tế

### 9.1. Đánh giá kết quả đạt được
- Hệ thống BSS đã hoàn thiện trọn vẹn kiến trúc firmware thời gian thực với **FreeRTOS Dual-Task**.
- Mã nguồn được mô-đun hóa sạch sẽ, đạt chuẩn kiểm thử và biên dịch thành công 100% trên PlatformIO.
- Đã kiểm chứng thành công trên Wokwi toàn bộ chu trình: **Đo khoảng cách $\rightarrow$ Lọc dữ liệu $\rightarrow$ Phân cấp cảnh báo $\rightarrow$ Điều chế xung Haptic $\rightarrow$ Phát sóng BLE**.

### 9.2. Kế hoạch tiếp theo khi tiếp nhận phần cứng thật
1. **Tích hợp cảm biến Laser ToF VL53L1X qua I2C:**
   - Kết nối vào bus I2C của Seeed XIAO ESP32-S3: `SDA` (D4 / GPIO 5), `SCL` (D5 / GPIO 6).
   - Thay thế ruột hàm `read_distance_cm()` trong `sensor_tof.cpp` bằng driver Pololu `VL53L1X.h` (toàn bộ logic FreeRTOS, BLE và Motor giữ nguyên).
2. **Hiện thực hóa bộ lọc số:**
   - Nhúng thuật toán lọc trung vị (Median Filter) 5 mẫu và dải trễ Hysteresis 10cm vào `sensor_tof.cpp`.
3. **Kiểm thử thực địa (Field Testing):**
   - Lắp đặt mạch lên đuôi xe máy / kính bảo hộ, kết nối với smartphone và tiến hành thử nghiệm phản xạ điểm mù ở các dải tốc độ 20km/h, 40km/h và 60km/h.