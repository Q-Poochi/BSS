import 'dart:async';

import 'package:flutter/material.dart';

import 'screens/scan_screen.dart';
import 'services/ble_service.dart';

void main() => runApp(const SmartGlassesApp());

/// App điện thoại đi kèm hệ thống BSS (Blind Spot System).
class SmartGlassesApp extends StatefulWidget {
  const SmartGlassesApp({super.key});

  @override
  State<SmartGlassesApp> createState() => _SmartGlassesAppState();
}

class _SmartGlassesAppState extends State<SmartGlassesApp> {
  /// BleService được tạo MỘT LẦN và sống theo vòng đời app.
  /// (Nếu tạo trong `build()` thì mỗi lần rebuild sẽ sinh một service mới,
  /// làm rò rỉ kết nối BLE và mất stream đang nghe.)
  final BleService _bleService = BleService();

  @override
  void dispose() {
    unawaited(_bleService.dispose());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Smart Glasses - Cảnh báo điểm mù',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorSchemeSeed: const Color(0xFF1565C0),
        brightness: Brightness.light,
      ),
      home: ScanScreen(bleService: _bleService),
    );
  }
}
