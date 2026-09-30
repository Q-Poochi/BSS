package com.bss.companion.ble

/** Thông tin rút gọn của một thiết bị BLE tìm thấy khi quét (không giữ BluetoothDevice trong UI). */
data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
)

/** Máy trạng thái kết nối BLE - UI chỉ render theo state này. */
sealed interface BleConnectionState {
    /** Chưa làm gì / đã dọn dẹp tài nguyên. */
    data object Idle : BleConnectionState

    /** Bluetooth đang tắt trên điện thoại. */
    data object BluetoothOff : BleConnectionState

    /** Máy không có phần cứng BLE. */
    data object Unsupported : BleConnectionState

    /** Đang quét quảng bá (advertising). */
    data object Scanning : BleConnectionState

    /** Đang thiết lập kết nối GATT tới thiết bị. */
    data class Connecting(val device: BleDevice) : BleConnectionState

    /** Đã kết nối và đã bật notify thành công. */
    data class Connected(val device: BleDevice) : BleConnectionState

    /** Mất kết nối (chủ động hoặc do thiết bị reset / ra khỏi tầm). */
    data class Disconnected(val device: BleDevice?, val reason: String) : BleConnectionState

    /** Lỗi không thể phục hồi trong phiên kết nối (ví dụ sai UUID). */
    data class Failed(val reason: String) : BleConnectionState

    val isBusy: Boolean get() = this is Scanning || this is Connecting
}
