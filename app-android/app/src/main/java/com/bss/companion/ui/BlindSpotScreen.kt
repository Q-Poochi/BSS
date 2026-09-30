package com.bss.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bss.companion.ble.BleDevice
import com.bss.companion.ui.components.ConnectionPanel
import com.bss.companion.ui.components.DistancePanel
import com.bss.companion.ui.components.HistorySection

/**
 * Màn hình chính của app BSS.
 *
 * Là composable THUẦN (stateless): nhận [state] và các callback từ trên xuống,
 * không tự gọi ViewModel - nhờ vậy có thể preview và test UI độc lập.
 */
@Composable
fun BlindSpotScreen(
    state: BssUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (BleDevice) -> Unit,
    onDisconnect: () -> Unit,
    onClearHistory: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(isConnected = state.isConnected)

        DistancePanel(latest = state.latest)

        ConnectionPanel(
            state = state.connection,
            devices = state.devices,
            hasPermissions = state.hasPermissions,
            onStartScan = onStartScan,
            onStopScan = onStopScan,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            onRequestPermission = onRequestPermission,
        )

        HistorySection(
            history = state.history,
            stats = state.stats,
            onClearHistory = onClearHistory,
        )

        Text(
            text = "BSS Companion v1.0.0 · giao thức BLE 4 byte · firmware BSS v1.1",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Tiêu đề màn hình + chấm trạng thái tổng thể. */
@Composable
private fun ScreenHeader(isConnected: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = "BSS Companion",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = if (isConnected) {
                "Đang nhận dữ liệu điểm mù theo thời gian thực"
            } else {
                "Hệ thống giám sát điểm mù cho người khiếm thị"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
