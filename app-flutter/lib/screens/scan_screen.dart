import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';

import '../services/ble_service.dart';
import 'monitor_screen.dart';

/// Màn hình 1: quét thiết bị và kết nối tới kính.
///
/// Không quan tâm tới chi tiết BLE - chỉ gọi [BleService] và vẽ lại theo
/// `scanResults` + `connectionState`.
class ScanScreen extends StatefulWidget {
  final BleService bleService;

  const ScanScreen({super.key, required this.bleService});

  @override
  State<ScanScreen> createState() => _ScanScreenState();
}

class _ScanScreenState extends State<ScanScreen> {
  StreamSubscription<BleConnState>? _stateSub;
  BleConnState _state = BleConnState.disconnected;

  @override
  void initState() {
    super.initState();
    _state = widget.bleService.state;
    _stateSub = widget.bleService.connectionState.listen((state) {
      if (!mounted) return;
      setState(() => _state = state);
    });
    unawaited(_startScan());
  }

  @override
  void dispose() {
    unawaited(_stateSub?.cancel());
    // Rời màn hình quét thì dừng quét ngay để không hao pin.
    unawaited(widget.bleService.stopScan());
    super.dispose();
  }

  Future<void> _startScan() async {
    try {
      await widget.bleService.startScan();
    } catch (error) {
      if (!mounted) return;
      _showMessage('Không bắt đầu được quét: $error');
    }
  }

  Future<void> _connect(BluetoothDevice device) async {
    try {
      await widget.bleService.connect(device);
    } catch (error) {
      if (!mounted) return;
      _showMessage('$error');
      return;
    }

    if (!mounted) return;
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => MonitorScreen(bleService: widget.bleService),
      ),
    );

    // Quay lại màn hình này -> quét tiếp để có thể đổi sang thiết bị khác.
    if (!mounted) return;
    await widget.bleService.stopScan();
    await _startScan();
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Quét thiết bị'),
        actions: <Widget>[
          IconButton(
            tooltip: 'Quét lại',
            onPressed: _state == BleConnState.scanning ? null : _startScan,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: Column(
        children: <Widget>[
          if (_state == BleConnState.scanning) const LinearProgressIndicator(),
          Expanded(
            child: StreamBuilder<List<ScanResult>>(
              stream: widget.bleService.scanResults,
              initialData: const <ScanResult>[],
              builder: (context, snapshot) {
                // scanResults là stream duy nhất của plugin có thể phát lỗi
                // (ví dụ Bluetooth đang tắt).
                if (snapshot.hasError) {
                  return _hint('Lỗi khi quét: ${snapshot.error}');
                }

                final results = snapshot.data ?? const <ScanResult>[];
                if (results.isEmpty) return _emptyView();

                return ListView.separated(
                  itemCount: results.length,
                  separatorBuilder: _separator,
                  itemBuilder: (context, index) => _tile(results[index]),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _separator(BuildContext context, int index) => const Divider(height: 1);

  Widget _hint(String text) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Text(text, textAlign: TextAlign.center),
      ),
    );
  }

  Widget _emptyView() {
    return _hint(
      'Đang tìm thiết bị "$bssDeviceName"...\n\n'
      'Nếu danh sách vẫn trống, kiểm tra:\n'
      '• Kính (ESP32-S3) đã được cấp nguồn và chưa kết nối thiết bị khác\n'
      '• Bluetooth của điện thoại đã bật\n'
      '• Đã cho phép quyền Bluetooth khi app hỏi lần đầu\n'
      '• Khoảng cách điện thoại - kính trong vài mét',
    );
  }

  Widget _tile(ScanResult result) {
    final name = result.device.platformName.isEmpty
        ? '(không tên)'
        : result.device.platformName;
    final busy = _state == BleConnState.connecting;
    final isTarget = result.device.platformName == bssDeviceName;

    return ListTile(
      leading: Icon(
        isTarget ? Icons.sports_motorsports : Icons.bluetooth,
        color: isTarget ? Theme.of(context).colorScheme.primary : null,
      ),
      title: Text(name),
      subtitle: Text('${result.device.remoteId} • RSSI ${result.rssi} dBm'),
      trailing: busy
          ? const SizedBox(
              width: 20,
              height: 20,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : const Icon(Icons.chevron_right),
      enabled: !busy,
      onTap: () => _connect(result.device),
    );
  }
}
