package com.bss.companion.ble

import java.util.UUID

/**
 * "Hợp đồng" giao thức BLE giữa app Android (Central) và firmware ESP32-S3 (Peripheral).
 *
 * File này PHẢI luôn đồng bộ với phía firmware:
 *  - `include/config.h`     : BLE_DEVICE_NAME, SERVICE_UUID, CHARACTERISTIC_UUID,
 *                             THRESHOLD_*, SENSOR_READ_INTERVAL_MS, FILTER_WINDOW, HYSTERESIS_CM
 *  - `src/ble_service.cpp`  : thứ tự byte trong payload notify
 *
 * Đặc tả giao thức:
 *  - ESP32 chỉ đóng vai trò GATT Server, characteristic ở chế độ NOTIFY (chỉ đẩy, không nhận).
 *  - Payload cố định 4 byte, số nguyên little-endian.
 *  - Không có handshake: app subscribe CCCD là bắt đầu nhận dữ liệu.
 */
object BssBleContract {

    /** `#define BLE_DEVICE_NAME "SmartGlasses-OB"` */
    const val DEVICE_NAME = "SmartGlasses-OB"

    /** Tiền tố tên thiết bị, dùng để lọc nhanh trong kết quả quét. */
    const val DEVICE_NAME_PREFIX = "SmartGlasses"

    /** `#define SERVICE_UUID` */
    val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")

    /** `#define CHARACTERISTIC_UUID` */
    val CHARACTERISTIC_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

    /** CCCD (Client Characteristic Configuration Descriptor) - tương ứng BLE2902 trong firmware. */
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // ---------- Bố cục payload (4 byte) ----------
    const val PAYLOAD_SIZE = 4
    const val OFFSET_DISTANCE_LSB = 0
    const val OFFSET_DISTANCE_MSB = 1
    const val OFFSET_URGENCY = 2
    const val OFFSET_OBJECT_CLASS = 3

    // ---------- Ngưỡng khoảng cách (cm) - khớp config.h ----------
    const val THRESHOLD_CHU_Y_CM = 150
    const val THRESHOLD_GAN_CM = 100
    const val THRESHOLD_KHAN_CAP_CM = 50

    // ---------- Thông số xử lý tín hiệu phía firmware (chỉ để tra cứu/tài liệu) ----------
    const val FILTER_WINDOW = 5
    const val HYSTERESIS_CM = 10
    const val SENSOR_READ_INTERVAL_MS = 100
}
