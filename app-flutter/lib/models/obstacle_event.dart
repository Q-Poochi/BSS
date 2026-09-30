/// HỢP ĐỒNG DỮ LIỆU giữa firmware ESP32-S3 và app điện thoại.
///
/// File này PHẢI luôn đồng bộ với phía firmware:
///  - `include/config.h`    : BLE_DEVICE_NAME, SERVICE_UUID, CHARACTERISTIC_UUID,
///                            THRESHOLD_*, SENSOR_READ_INTERVAL_MS, FILTER_WINDOW, HYSTERESIS_CM
///  - `src/ble_service.cpp` : thứ tự byte trong payload notify
///
/// Sửa bên nào thì PHẢI sửa cả hai bên (3 nơi: firmware, app Kotlin, app Flutter).
library;

/// Bố cục payload notify - ESP32 gửi cố định 4 byte, số nguyên little-endian:
/// ```
/// payload[0] =  distance_mm        & 0xFF   <- LSB
/// payload[1] = (distance_mm >> 8)  & 0xFF   <- MSB
/// payload[2] =  urgency_level               (0..3, đã qua lọc 5 mẫu + hysteresis 10 cm)
/// payload[3] =  object_class                (0 = unknown, dành sẵn cho tầng AI/CV)
/// ```
class ObstacleEvent {
  /// Kích thước payload hợp lệ. Khớp `PAYLOAD_SIZE` phía firmware.
  static const int payloadSize = 4;

  /// Khoảng cách (mm). Firmware đã lọc trung bình trượt 5 mẫu.
  /// Giá trị [sensorTimeoutDistanceMm] nghĩa là "ngoài tầm đo / pulseIn timeout".
  final int distanceMm;

  /// 0 = an toàn, 1 = chú ý, 2 = cảnh báo, 3 = khẩn cấp - do FIRMWARE quyết định.
  final int urgencyLevel;

  /// 0 = unknown, còn lại dành cho CV.
  final int objectClass;

  /// Mốc thời gian phía app (giờ máy điện thoại) - dùng để biết bản tin "cũ" bao lâu.
  final DateTime receivedAt;

  ObstacleEvent({
    required this.distanceMm,
    required this.urgencyLevel,
    required this.objectClass,
    DateTime? receivedAt,
  }) : receivedAt = receivedAt ?? DateTime.now();

  /// Giải mã payload notify của firmware.
  ///
  /// Ném [FormatException] nếu payload thiếu byte - [BleService] sẽ bắt lỗi này
  /// và bỏ qua gói đó thay vì làm chết luồng BLE.
  ///
  /// Payload dài hơn 4 byte vẫn được chấp nhận (chỉ đọc 4 byte đầu) để firmware
  /// có thể mở rộng thêm trường mới mà app cũ không vỡ.
  factory ObstacleEvent.fromBytes(List<int> bytes, {DateTime? receivedAt}) {
    if (bytes.length < payloadSize) {
      throw FormatException(
        'Payload phải đủ $payloadSize byte, nhận ${bytes.length}',
      );
    }
    return ObstacleEvent(
      distanceMm: bytes[0] | (bytes[1] << 8), // little-endian, khớp firmware
      urgencyLevel: bytes[2],
      objectClass: bytes[3],
      receivedAt: receivedAt,
    );
  }

  /// Giá trị bão hoà firmware trả về khi `pulseIn()` timeout.
  static const int sensorTimeoutDistanceMm = 9990;

  /// Khoảng cách quy đổi sang cm (hiển thị "128.4 cm").
  double get distanceCm => distanceMm / 10.0;

  /// True khi firmware không đo được vật thể (ngoài tầm cảm biến siêu âm).
  bool get isOutOfRange => distanceMm >= sensorTimeoutDistanceMm;

  /// Chuỗi khoảng cách sẵn sàng để hiển thị.
  String get distanceText =>
      isOutOfRange ? 'Ngoài tầm đo' : '${distanceCm.toStringAsFixed(1)} cm';

  /// Nhãn tiếng Việt của mức cảnh báo do firmware gửi lên.
  String get urgencyLabel => UrgencyLevel.label(urgencyLevel);

  /// Mức này có cần rung/đổi màu cảnh báo hay không (mức 0 = an toàn).
  bool get isAlert => UrgencyLevel.isAlert(urgencyLevel);

  /// Nhãn vật thể - hiện luôn là "Chưa xác định" vì firmware chưa có thị giác máy.
  String get objectLabel {
    switch (objectClass) {
      case 0:
        return 'Chưa xác định';
      case 1:
        return 'Người đi bộ';
      case 2:
        return 'Xe máy';
      case 3:
        return 'Ô tô';
      default:
        return 'Loại #$objectClass';
    }
  }

  @override
  String toString() =>
      'ObstacleEvent(distance: $distanceText, urgency: $urgencyLabel, '
      'class: $objectLabel)';
}

/// 4 mức cảnh báo do FIRMWARE quyết định - app chỉ ĐỌC và HIỂN THỊ.
///
/// Nguyên tắc thiết kế: app tuyệt đối không tự tính lại mức cảnh báo từ
/// [ObstacleEvent.distanceMm], tránh trường hợp app và motor rung trên kính báo
/// hai trạng thái khác nhau (firmware đã có lọc trung bình + hysteresis 10 cm).
class UrgencyLevel {
  UrgencyLevel._();

  /// d > 150 cm - PWM motor = 0.
  static const int safe = 0;

  /// 100 < d <= 150 cm - PWM motor = 80.
  static const int notice = 1;

  /// 50 < d <= 100 cm - PWM motor = 160.
  static const int warning = 2;

  /// 0 < d <= 50 cm - PWM motor = 255.
  static const int critical = 3;

  /// Mức cao nhất hợp lệ; byte lớn hơn bị coi là mức an toàn (fail-safe).
  static const int maxLevel = critical;

  /// Nhãn hiển thị. Giá trị lạ (firmware lỗi/phiên bản mới) -> "KHÔNG XÁC ĐỊNH".
  static String label(int code) {
    switch (code) {
      case safe:
        return 'AN TOÀN';
      case notice:
        return 'CHÚ Ý';
      case warning:
        return 'CẢNH BÁO';
      case critical:
        return 'KHẨN CẤP';
      default:
        return 'KHÔNG XÁC ĐỊNH';
    }
  }

  /// Mô tả dài, dùng cho tooltip/phụ đề.
  static String description(int code) {
    switch (code) {
      case safe:
        return 'Không có vật thể trong vùng điểm mù';
      case notice:
        return 'Có phương tiện đang tiến vào vùng điểm mù';
      case warning:
        return 'Vật thể đã ở rất gần, cần chuẩn bị xử lý';
      case critical:
        return 'Nguy cơ va chạm trực tiếp!';
      default:
        return 'Mức cảnh báo không hợp lệ';
    }
  }

  /// Mức này có phải trạng thái cảnh báo hay không.
  static bool isAlert(int code) => code >= notice && code <= maxLevel;
}
