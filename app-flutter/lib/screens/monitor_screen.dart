import 'dart:async';

import 'package:flutter/material.dart';

import '../models/obstacle_event.dart';
import '../services/ble_service.dart';

/// Màn hình 2: theo dõi trực tiếp - nền đổi màu + số cm theo từng bản tin notify.
///
/// App CHỈ HIỂN THỊ mức cảnh báo do firmware gửi lên, tuyệt đối không tự tính lại
/// từ khoảng cách - để app và motor rung trên kính luôn cùng một trạng thái.
class MonitorScreen extends StatefulWidget {
  final BleService bleService;

  const MonitorScreen({super.key, required this.bleService});

  @override
  State<MonitorScreen> createState() => _MonitorScreenState();
}

class _MonitorScreenState extends State<MonitorScreen> {
  StreamSubscription<ObstacleEvent>? _eventSub;
  StreamSubscription<BleConnState>? _stateSub;

  /// Bản tin mới nhất được giữ lại: stream là broadcast (không replay), nếu không
  /// cache thì màn hình sẽ nhấp nháy về "Đang chờ dữ liệu..." mỗi lần rebuild.
  ObstacleEvent? _lastEvent;
  BleConnState _state = BleConnState.disconnected;

  @override
  void initState() {
    super.initState();
    _state = widget.bleService.state;
    _eventSub = widget.bleService.events.listen((event) {
      if (!mounted) return;
      setState(() => _lastEvent = event);
    });
    _stateSub = widget.bleService.connectionState.listen((state) {
      if (!mounted) return;
      setState(() => _state = state);
    });
  }

  @override
  void dispose() {
    unawaited(_eventSub?.cancel());
    unawaited(_stateSub?.cancel());
    super.dispose();
  }

  Future<void> _disconnect() async {
    await widget.bleService.disconnect();
    if (!mounted) return;
    Navigator.of(context).pop();
  }

  /// Màu nền theo mức cảnh báo: 0 xanh, 1 vàng, 2 cam, 3 đỏ.
  Color _backgroundColor(int level) {
    switch (level) {
      case UrgencyLevel.safe:
        return const Color(0xFF2E7D32);
      case UrgencyLevel.notice:
        return const Color(0xFFF9A825);
      case UrgencyLevel.warning:
        return const Color(0xFFEF6C00);
      case UrgencyLevel.critical:
        return const Color(0xFFC62828);
      default:
        return const Color(0xFF616161);
    }
  }

  @override
  Widget build(BuildContext context) {
    final connected = _state == BleConnState.connected;
    final event = _lastEvent;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Trạng thái thiết bị'),
        actions: <Widget>[
          IconButton(
            tooltip: 'Ngắt kết nối',
            onPressed: _disconnect,
            icon: const Icon(Icons.bluetooth_disabled),
          ),
        ],
      ),
      body: Column(
        children: <Widget>[
          if (!connected)
            Container(
              width: double.infinity,
              color: const Color(0xFF37474F),
              padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 16),
              child: Row(
                children: <Widget>[
                  const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'Chưa có kết nối tới kính (${_state.name})',
                      style: const TextStyle(color: Colors.white),
                    ),
                  ),
                ],
              ),
            ),
          Expanded(
            child: Container(
              width: double.infinity,
              color: _backgroundColor(event?.urgencyLevel ?? UrgencyLevel.safe),
              alignment: Alignment.center,
              padding: const EdgeInsets.all(24),
              child: (event == null || !connected)
                  ? _waitingView()
                  : _eventView(event),
            ),
          ),
        ],
      ),
    );
  }

  Widget _waitingView() {
    return const Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: <Widget>[
        CircularProgressIndicator(color: Colors.white),
        SizedBox(height: 16),
        Text(
          'Đang chờ dữ liệu...',
          style: TextStyle(fontSize: 20, color: Colors.white),
        ),
      ],
    );
  }

  Widget _eventView(ObstacleEvent event) {
    final rangeNote = event.isOutOfRange ? '  •  ngoài tầm đo' : '';

    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: <Widget>[
        Text(
          event.distanceText,
          textAlign: TextAlign.center,
          style: const TextStyle(
            fontSize: 56,
            fontWeight: FontWeight.bold,
            color: Colors.white,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          event.urgencyLabel,
          style: const TextStyle(fontSize: 28, color: Colors.white),
        ),
        const SizedBox(height: 24),
        Text(
          UrgencyLevel.description(event.urgencyLevel),
          textAlign: TextAlign.center,
          style: const TextStyle(fontSize: 14, color: Colors.white70),
        ),
        const SizedBox(height: 16),
        Text(
          'Vật thể: ${event.objectLabel}\n'
          'Cập nhật: ${_timeText(event.receivedAt)}$rangeNote',
          textAlign: TextAlign.center,
          style: const TextStyle(fontSize: 14, color: Colors.white70),
        ),
      ],
    );
  }

  /// Định dạng giờ:phút:giây không cần thêm package `intl`.
  String _timeText(DateTime time) {
    String two(int value) => value.toString().padLeft(2, '0');
    return '${two(time.hour)}:${two(time.minute)}:${two(time.second)}';
  }
}
