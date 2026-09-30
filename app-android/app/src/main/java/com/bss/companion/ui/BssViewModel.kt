package com.bss.companion.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bss.companion.ble.BleConnectionState
import com.bss.companion.ble.BleDevice
import com.bss.companion.ble.BssBleManager
import com.bss.companion.ble.ObstacleEvent
import com.bss.companion.data.EventStats
import com.bss.companion.data.ObstacleEventRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Toàn bộ trạng thái mà màn hình cần vẽ. Bất biến (immutable) -> Compose recompose an toàn. */
data class BssUiState(
    val connection: BleConnectionState = BleConnectionState.Idle,
    val devices: List<BleDevice> = emptyList(),
    val latest: ObstacleEvent? = null,
    val history: List<ObstacleEvent> = emptyList(),
    val stats: EventStats = EventStats(),
    val hasPermissions: Boolean = false,
) {
    val isConnected: Boolean get() = connection is BleConnectionState.Connected
    val isScanning: Boolean get() = connection is BleConnectionState.Scanning
}

/**
 * ViewModel duy nhất của app (mô hình MVVM).
 *
 * Nhiệm vụ: giữ [BssBleManager] và [ObstacleEventRepository] sống sót qua các lần
 * xoay màn hình, đồng thời hợp nhất nhiều luồng dữ liệu thành một [BssUiState] duy nhất.
 * UI (Compose) chỉ việc đọc `uiState` và gọi các hàm hành động bên dưới.
 */
class BssViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = BssBleManager(application.applicationContext)
    private val repository = ObstacleEventRepository(bleManager)

    private val permissionGranted = MutableStateFlow(false)

    val uiState: StateFlow<BssUiState> = combine(
        bleManager.connectionState,
        bleManager.devices,
        repository.latest,
        repository.history,
        repository.stats,
    ) { connection, devices, latest, history, stats ->
        BssUiState(
            connection = connection,
            devices = devices,
            latest = latest,
            history = history,
            stats = stats,
        )
    }.combine(permissionGranted) { state, granted ->
        state.copy(hasPermissions = granted)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = BssUiState(),
    )

    init {
        repository.start(viewModelScope)
    }

    // ---------------- Hành động từ UI ----------------

    fun startScan() = bleManager.startScan()

    fun stopScan() = bleManager.stopScan()

    fun connect(device: BleDevice) = bleManager.connect(device)

    fun disconnect() {
        bleManager.disconnect()
        repository.resetLive()
    }

    fun clearHistory() = repository.clearHistory()

    /** Gọi sau khi hộp thoại xin quyền kết thúc, hoặc trong onResume. */
    fun updatePermissionState(granted: Boolean) {
        permissionGranted.value = granted
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.release()
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
