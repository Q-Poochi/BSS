import 'package:flutter_test/flutter_test.dart';
import 'package:smart_glasses_app/models/obstacle_event.dart';

/// Unit test cho "hợp đồng dữ liệu" 4 byte giữa firmware ESP32-S3 và app.
/// Mọi thay đổi ở `lib/models/obstacle_event.dart` phải làm bộ test này chạy lại.
///
/// Chạy: flutter test
void main() {
  group('ObstacleEvent.fromBytes - giải mã payload notify', () {
    test('đọc distance_mm theo little-endian (LSB trước)', () {
      // 1284 mm = 0x0504 -> firmware gửi [0x04, 0x05]
      final event = ObstacleEvent.fromBytes(<int>[0x04, 0x05, 2, 0]);

      expect(event.distanceMm, 1284);
      expect(event.distanceCm, closeTo(128.4, 0.0001));
      expect(event.distanceText, '128.4 cm');
    });

    test('byte 2 là urgency_level, byte 3 là object_class', () {
      final event = ObstacleEvent.fromBytes(<int>[0x00, 0x00, 3, 2]);

      expect(event.urgencyLevel, 3);
      expect(event.objectClass, 2);
      expect(event.urgencyLabel, 'KHẨN CẤP');
      expect(event.objectLabel, 'Xe máy');
      expect(event.isAlert, isTrue);
    });

    test('payload thiếu byte -> FormatException để tầng BLE bỏ qua gói', () {
      expect(
        () => ObstacleEvent.fromBytes(<int>[0x04, 0x05, 2]),
        throwsFormatException,
      );
      expect(
        () => ObstacleEvent.fromBytes(const <int>[]),
        throwsFormatException,
      );
    });

    test('payload dài hơn 4 byte vẫn đọc được 4 byte đầu', () {
      // Firmware thêm trường mới thì app cũ không vỡ.
      final event = ObstacleEvent.fromBytes(<int>[0x00, 0x01, 1, 0, 0xAB]);

      expect(event.distanceMm, 256);
      expect(event.urgencyLevel, 1);
      expect(event.objectClass, 0);
    });

    test('9990 mm là giá trị bão hoà "ngoài tầm đo" của cảm biến', () {
      final event = ObstacleEvent.fromBytes(<int>[0x06, 0x27, 0, 0]);

      expect(event.distanceMm, 9990);
      expect(event.isOutOfRange, isTrue);
      expect(event.distanceText, 'Ngoài tầm đo');
    });

    test('0 mm (vật cản sát) là dữ liệu hợp lệ, không phải lỗi', () {
      final event = ObstacleEvent.fromBytes(<int>[0x00, 0x00, 3, 0]);

      expect(event.distanceMm, 0);
      expect(event.distanceCm, 0);
      expect(event.isOutOfRange, isFalse);
    });

    test('receivedAt dùng mốc thời gian truyền vào nếu có', () {
      final fixed = DateTime(2026, 9, 29, 8, 30, 15);
      final event = ObstacleEvent.fromBytes(<int>[0, 0, 0, 0], receivedAt: fixed);

      expect(event.receivedAt, fixed);
    });

    test('toString() đủ thông tin để đọc log', () {
      final event = ObstacleEvent.fromBytes(<int>[0x64, 0x00, 1, 1]);

      expect(event.toString(), contains('10.0 cm'));
      expect(event.toString(), contains('CHÚ Ý'));
      expect(event.toString(), contains('Người đi bộ'));
    });
  });

  group('UrgencyLevel', () {
    test('nhãn 4 mức khớp firmware và motor rung trên kính', () {
      expect(UrgencyLevel.label(UrgencyLevel.safe), 'AN TOÀN');
      expect(UrgencyLevel.label(UrgencyLevel.notice), 'CHÚ Ý');
      expect(UrgencyLevel.label(UrgencyLevel.warning), 'CẢNH BÁO');
      expect(UrgencyLevel.label(UrgencyLevel.critical), 'KHẨN CẤP');
      expect(UrgencyLevel.maxLevel, 3);
    });

    test('mức 0 không phải cảnh báo, 1..3 là cảnh báo', () {
      expect(UrgencyLevel.isAlert(0), isFalse);
      expect(UrgencyLevel.isAlert(1), isTrue);
      expect(UrgencyLevel.isAlert(2), isTrue);
      expect(UrgencyLevel.isAlert(3), isTrue);
    });

    test('byte lạ (do lỗi truyền/nhiễu) bị coi là an toàn - fail-safe', () {
      expect(UrgencyLevel.isAlert(200), isFalse);
      expect(UrgencyLevel.label(200), 'KHÔNG XÁC ĐỊNH');
      expect(UrgencyLevel.description(200), 'Mức cảnh báo không hợp lệ');
    });
  });
}
