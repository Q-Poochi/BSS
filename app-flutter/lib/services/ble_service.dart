import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';

import '../models/obstacle_event.dart';

/// UUID PHẢI khớp tuyệt đối `include/config.h` bên firmware.
///
/// Lưu ý: lớp `Guid` chỉ có ở flutter_blue_plus **1.x** (2.x đổi sang String),
/// vì vậy `pubspec.yaml` cố ý ghim `flutter_blue_plus: ^1.32.12`.
final Guid bssServiceUuid = Guid('4fafc201-1fb5-459e-8fcc-c5c9c331914b');
final Guid bssCharacteristicUuid = Guid('beb5483e-36e1-4688-b7f5-ea07361b26a8');

/// `config.h` -> `BLE_DEVICE_NAME`, dùng cho thông báo "đang tìm thiết bị ...".
const String bssDeviceName = 'SmartGlasses-OB';

/// Trạng thái của tầng BLE. [BleConnState.disconnected] cũng là trạng thái nghỉ.
enum BleConnState { disconnected, scanning, connecting, connected }

/// Lỗi nghiệp vụ của tầng BLE - UI chỉ cần hiển thị `message` là đủ.
class BleException implements Exception {
  final String message;

  BleException(this.message);

  @override
  String toString() => message;
}

/// Toàn bộ luồng BLE: quét -> lọc theo Service UUID -> kết nối -> subscribe notify
/// -> giải mã 4 byte -> phát ra [events].
///
/// UI (widget) KHÔNG đụng tới flutter_blue_plus trực tiếp, chỉ nghe 2 stream
/// [events] và [connectionState] - nhờ vậy có thể thay tầng BLE mà không sửa UI.
class BleService {
  final _eventController = StreamController<ObstacleEvent>.broadcast();
  final _stateController = StreamController<BleConnState>.broadcast();

  /// Bản tin khoảng cách đã giải mã, phát cho UI.
  Stream<ObstacleEvent> get events => _eventController.stream;

  /// Trạng thái kết nối để UI hiển thị banner/vòng xoay.
  Stream<BleConnState> get connectionState => _stateController.stream;

  /// Kết quả quét (đã lọc theo Service UUID ở tầng OS).
  /// Đây là stream DUY NHẤT của plugin có thể phát lỗi -> UI phải xử lý `hasError`.
  Stream<List<ScanResult>> get scanResults => FlutterBluePlus.scanResults;

  /// Trạng thái hiện tại (để màn hình mới mở biết ngay, không phải chờ stream).
  BleConnState get state => _state;

  /// Số gói payload bị bỏ qua vì sai định dạng (chẩn đoán nhiễu/lỗi firmware).
  int get droppedPayloads => _droppedPayloads;

  BluetoothDevice? _device;
  BluetoothCharacteristic? _characteristic;
  StreamSubscription<List<int>>? _notifySub;
  StreamSubscription<BluetoothConnectionState>? _connSub;
  Timer? _scanTimeoutTimer;

  BleConnState _state = BleConnState.disconnected;
  int _droppedPayloads = 0;
  bool _disposed = false;

  void _setState(BleConnState next) {
    if (_disposed || _state == next) return;
    _state = next;
    _stateController.add(next);
  }

  /// Bắt đầu quét, tự dừng sau [timeout].
  ///
  /// Lọc bằng `withServices` để OS chỉ trả về đúng thiết bị của kính - không quét
  /// rác xung quanh, tiết kiệm pin và tránh danh sách dài vô nghĩa.
  Future<void> startScan({Duration timeout = const Duration(seconds: 8)}) async {
    if (_disposed) return;
    if (FlutterBluePlus.isScanningNow) {
      await FlutterBluePlus.stopScan();
    }

    _scanTimeoutTimer?.cancel();
    _setState(BleConnState.scanning);

    // Từ v1.15, startScan() trả về NGAY sau khi gửi lệnh quét, KHÔNG đợi hết
    // timeout -> phải tự hẹn giờ để đưa UI về trạng thái nghỉ.
    _scanTimeoutTimer = Timer(timeout, () {
      if (_state == BleConnState.scanning) _setState(BleConnState.disconnected);
    });

    await FlutterBluePlus.startScan(
      timeout: timeout,
      withServices: <Guid>[bssServiceUuid],
    );
  }

  /// Dừng quét sớm (rời màn hình quét, hoặc trước khi kết nối).
  Future<void> stopScan() async {
    _scanTimeoutTimer?.cancel();
    if (FlutterBluePlus.isScanningNow) {
      await FlutterBluePlus.stopScan();
    }
    if (_state == BleConnState.scanning && !_disposed) {
      _setState(BleConnState.disconnected);
    }
  }

  /// Kết nối tới thiết bị, discover service/characteristic và bật notify.
  ///
  /// Ném [BleException] nếu thất bại; mọi subscription dở dang đã được dọn sạch
  /// trước khi ném ra ngoài.
  Future<void> connect(BluetoothDevice device) async {
    if (_disposed) return;
    if (_state == BleConnState.connected &&
        _device?.remoteId == device.remoteId) {
      return; // đã kết nối đúng thiết bị này
    }

    _setState(BleConnState.connecting);
    await stopScan();
    _device = device;

    await _connSub?.cancel();
    _connSub = device.connectionState.listen((s) {
      if (s == BluetoothConnectionState.disconnected) {
        _setState(BleConnState.disconnected);
      }
    });

    try {
      await device.connect(timeout: const Duration(seconds: 10));

      final services = await device.discoverServices();
      final service = services.firstWhere(
        (s) => s.uuid == bssServiceUuid,
        orElse: () => throw BleException(
          'Thiết bị không có service $bssServiceUuid - có thể không phải kính BSS',
        ),
      );
      final characteristic = service.characteristics.firstWhere(
        (c) => c.uuid == bssCharacteristicUuid,
        orElse: () => throw BleException(
          'Không tìm thấy characteristic notify $bssCharacteristicUuid',
        ),
      );

      _characteristic = characteristic;
      await characteristic.setNotifyValue(true);

      _notifySub = characteristic.onValueReceived.listen(_onPayload);
      // Plugin khuyến nghị: tự huỷ subscription khi thiết bị ngắt kết nối, tránh
      // listen trùng (dữ liệu bị phát 2 lần) ở lần kết nối sau.
      device.cancelWhenDisconnected(_notifySub!);

      _setState(BleConnState.connected);
      if (kDebugMode) debugPrint('[BSS] Đã subscribe notify: $bssDeviceName');
    } catch (error) {
      await disconnect();
      if (error is BleException) rethrow;
      throw BleException('Kết nối thất bại: $error');
    }
  }

  /// Characteristic đang subscribe - dùng khi cần đọc lại giá trị / debug.
  BluetoothCharacteristic? get characteristic => _characteristic;

  /// Giải mã payload và phát cho UI. Gói sai định dạng bị bỏ qua, không crash luồng.
  void _onPayload(List<int> bytes) {
    try {
      _eventController.add(ObstacleEvent.fromBytes(bytes));
    } catch (error) {
      _droppedPayloads++;
      if (kDebugMode) debugPrint('[BSS] Bỏ qua payload không hợp lệ ($error)');
    }
  }

  /// Ngắt kết nối và dọn mọi subscription.
  Future<void> disconnect() async {
    await _notifySub?.cancel();
    _notifySub = null;
    await _connSub?.cancel();
    _connSub = null;
    _characteristic = null;
    try {
      await _device?.disconnect();
    } catch (error) {
      if (kDebugMode) debugPrint('[BSS] disconnect() lỗi: $error');
    }
    _device = null;
    _setState(BleConnState.disconnected);
  }

  /// Gọi trong `dispose()` của widget gốc: ngắt kết nối, đóng stream, huỷ timer.
  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    _scanTimeoutTimer?.cancel();
    await disconnect();
    await _notifySub?.cancel();
    await _connSub?.cancel();
    await _eventController.close();
    await _stateController.close();
  }
}
