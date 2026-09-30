package com.bss.companion.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tầng BLE client (GATT Central) nói chuyện với firmware BSS trên Seeed XIAO ESP32-S3.
 *
 * Trách nhiệm:
 *  1. Quét và lọc thiết bị quảng bá tên `SmartGlasses-OB`.
 *  2. Kết nối GATT, khám phá service, subscribe CCCD để nhận notify.
 *  3. Giải mã payload 4 byte -> [ObstacleEvent] và phát ra [events] dưới dạng Flow.
 *  4. Poll RSSI định kỳ để UI hiển thị cường độ tín hiệu.
 *
 * Tầng này KHÔNG giữ reference tới Activity/View -> không gây memory leak.
 * Mọi thứ UI cần đều đi qua [connectionState], [devices] và [events].
 */
class BssBleManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Idle)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    /** Buffer 64 phần tử, phần tử cũ nhất bị loại khi tắc nghẽn (không chặn luồng GATT). */
    private val _events = MutableSharedFlow<ObstacleEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ObstacleEvent> = _events.asSharedFlow()

    private var gatt: BluetoothGatt? = null
    private var activeDevice: BleDevice? = null
    private var rssiJob: Job? = null

    private val scanner get() = adapter?.bluetoothLeScanner

    // ======================================================================
    //  1. QUÉT THIẾT BỊ (SCAN)
    // ======================================================================

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = runCatching { device.name }.getOrNull().orEmpty()

            // Lọc theo tên firmware. Vẫn nhận thêm thiết bị khớp Service UUID
            // vì một số điện thoại cache tên cũ và có thể trả về tên rỗng.
            val nameMatched = name.contains(BssBleContract.DEVICE_NAME_PREFIX, ignoreCase = true)
            val serviceMatched = result.scanRecord?.serviceUuids
                ?.any { it.uuid == BssBleContract.SERVICE_UUID } == true
            if (!nameMatched && !serviceMatched) return

            val found = BleDevice(
                name = name.ifBlank { "(không tên)" },
                address = device.address,
                rssi = result.rssi,
            )
            _devices.value = _devices.value
                .filterNot { it.address == found.address }
                .plus(found)
                .sortedByDescending { it.rssi }

            Log.d(TAG, "Tìm thấy ${found.name} [${found.address}] RSSI=${found.rssi}")
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Quét thất bại, mã lỗi = $errorCode")
            _connectionState.value = BleConnectionState.Failed("Quét thất bại (mã $errorCode)")
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val bleAdapter = adapter
        if (bleAdapter == null) {
            _connectionState.value = BleConnectionState.Unsupported
            return
        }
        if (!bleAdapter.isEnabled) {
            _connectionState.value = BleConnectionState.BluetoothOff
            return
        }
        if (!hasPermissions()) {
            _connectionState.value = BleConnectionState.Failed("Chưa được cấp quyền Bluetooth")
            return
        }
        val bleScanner = scanner
        if (bleScanner == null) {
            _connectionState.value = BleConnectionState.Failed("Không lấy được BLE scanner")
            return
        }

        _devices.value = emptyList()
        _connectionState.value = BleConnectionState.Scanning

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        runCatching { bleScanner.startScan(null, settings, scanCallback) }
            .onFailure {
                Log.e(TAG, "startScan lỗi", it)
                _connectionState.value = BleConnectionState.Failed("Không thể bắt đầu quét")
            }

        // Tự dừng sau 15s tránh đốt pin vô ích khi không tìm thấy thiết bị.
        scope.launch {
            delay(SCAN_TIMEOUT_MS)
            if (_connectionState.value is BleConnectionState.Scanning) stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (hasPermissions()) {
            runCatching { scanner?.stopScan(scanCallback) }
        }
        if (_connectionState.value is BleConnectionState.Scanning) {
            _connectionState.value = BleConnectionState.Idle
        }
    }

    // ======================================================================
    //  2. KẾT NỐI GATT + SUBSCRIBE NOTIFY
    // ======================================================================

    @SuppressLint("MissingPermission")
    fun connect(device: BleDevice) {
        if (!hasPermissions()) {
            _connectionState.value = BleConnectionState.Failed("Chưa được cấp quyền Bluetooth")
            return
        }
        stopScan()
        closeGatt()

        val bleAdapter = adapter
        if (bleAdapter == null) {
            _connectionState.value = BleConnectionState.Unsupported
            return
        }
        if (!bleAdapter.isEnabled) {
            _connectionState.value = BleConnectionState.BluetoothOff
            return
        }

        val remote = runCatching { bleAdapter.getRemoteDevice(device.address) }.getOrNull()
        if (remote == null) {
            _connectionState.value = BleConnectionState.Failed("Địa chỉ MAC không hợp lệ")
            return
        }

        activeDevice = device
        _connectionState.value = BleConnectionState.Connecting(device)

        // autoConnect = false: kết nối trực tiếp, nhanh và có timeout rõ ràng.
        // TRANSPORT_LE: bắt buộc BLE, không rơi xuống Bluetooth Classic.
        val newGatt = remote.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        if (newGatt == null) {
            _connectionState.value = BleConnectionState.Failed("Không tạo được kết nối GATT")
        }
        gatt = newGatt
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        rssiJob?.cancel()
        gatt?.let { g ->
            if (hasPermissions()) runCatching { g.disconnect() }
        }
        activeDevice?.let { _connectionState.value = BleConnectionState.Disconnected(it, "Người dùng ngắt kết nối") }
            ?: run { _connectionState.value = BleConnectionState.Idle }
    }

    /** Đọc RSSI hiện tại từ thiết bị (bất đồng bộ, kết quả về ở [BluetoothGattCallback.onReadRemoteRssi]). */
    @SuppressLint("MissingPermission")
    private fun requestRssi() {
        if (!hasPermissions()) return
        runCatching { gatt?.readRemoteRssi() }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = activeDevice
                        ?.let { BleConnectionState.Connecting(it) }
                        ?: BleConnectionState.Idle
                    // Bắt buộc phải discoverServices() thì mới thấy được characteristic.
                    if (!g.discoverServices()) {
                        _connectionState.value = BleConnectionState.Failed("Không khám phá được service")
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    rssiJob?.cancel()
                    val reason = "Mã trạng thái GATT = $status"
                    _connectionState.value = BleConnectionState.Disconnected(activeDevice, reason)
                    runCatching { g.close() }
                    if (gatt === g) gatt = null
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = BleConnectionState.Failed("Khám phá service thất bại ($status)")
                return
            }

            val characteristic = g
                .getService(BssBleContract.SERVICE_UUID)
                ?.getCharacteristic(BssBleContract.CHARACTERISTIC_UUID)

            if (characteristic == null) {
                _connectionState.value = BleConnectionState.Failed(
                    "Không tìm thấy characteristic trên firmware này",
                )
                return
            }

            val enabled = enableNotifications(g, characteristic)
            if (!enabled) {
                _connectionState.value = BleConnectionState.Failed("Không bật được notify")
                return
            }

            _connectionState.value = activeDevice
                ?.let { BleConnectionState.Connected(it) }
                ?: BleConnectionState.Idle

            // Poll RSSI mỗi 2s để UI hiển thị cường độ tín hiệu.
            rssiJob?.cancel()
            rssiJob = scope.launch {
                while (true) {
                    requestRssi()
                    delay(RSSI_POLL_INTERVAL_MS)
                }
            }
        }

        // ---- Nhận dữ liệu notify ----
        // API 33+ gọi overload 3 tham số; API < 33 gọi overload 2 tham số.
        // Override cả hai để chạy đúng trên mọi phiên bản Android.

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleNotification(value)
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            handleNotification(characteristic.value ?: return)
        }

        override fun onReadRemoteRssi(g: BluetoothGatt, rssi: Int, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val current = activeDevice ?: return
            val updated = current.copy(rssi = rssi)
            activeDevice = updated
            // Chỉ cập nhật RSSI, không đổi state kết nối.
            when (val state = _connectionState.value) {
                is BleConnectionState.Connected -> _connectionState.value = state.copy(device = updated)
                is BleConnectionState.Connecting -> _connectionState.value = state.copy(device = updated)
                else -> Unit
            }
        }
    }

    /** Giải mã payload và phát sự kiện cho tầng trên. */
    private fun handleNotification(payload: ByteArray) {
        val event = ObstacleEvent.fromPayload(payload) ?: run {
            Log.w(TAG, "Payload sai kích thước: ${payload.size} byte")
            return
        }
        Log.d(TAG, "Notify: ${event.distanceCm} cm | urgency=${event.urgency.code}")
        _events.tryEmit(event)
    }

    /**
     * Bật notify: ghi 0x0001 vào CCCD (BLE2902 phía firmware).
     * @return true nếu lệnh ghi được gửi thành công.
     */
    @SuppressLint("MissingPermission")
    private fun enableNotifications(
        g: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ): Boolean {
        if (!g.setCharacteristicNotification(characteristic, true)) return false

        val cccd = characteristic.getDescriptor(BssBleContract.CCCD_UUID) ?: return false
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            cccd.value = value
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        }
    }

    /** Đóng GATT để giải phóng tài nguyên hệ thống (bắt buộc sau mỗi phiên kết nối). */
    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        rssiJob?.cancel()
        gatt?.let { g ->
            if (hasPermissions()) runCatching { g.disconnect() }
            runCatching { g.close() }
        }
        gatt = null
    }

    /** Giải phóng toàn bộ tài nguyên khi ViewModel bị huỷ (gọi trong onCleared). */
    fun release() {
        closeGatt()
        activeDevice = null
        scope.cancel() // dừng vòng poll RSSI và timer hết hạn quét
    }

    /** Kiểm tra quyền BLE theo phiên bản Android đang chạy. */
    private fun hasPermissions(): Boolean = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "BssBleManager"
        private const val SCAN_TIMEOUT_MS = 15_000L
        private const val RSSI_POLL_INTERVAL_MS = 2_000L

        /**
         * Android 12 (API 31) trở lên dùng bộ quyền BLE mới.
         * Android 11 trở xuống bắt buộc phải xin quyền vị trí mới quét được BLE.
         */
        val REQUIRED_PERMISSIONS: Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                )
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
    }
}
