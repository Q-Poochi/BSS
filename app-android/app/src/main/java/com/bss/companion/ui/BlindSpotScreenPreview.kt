package com.bss.companion.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.bss.companion.ble.BleConnectionState
import com.bss.companion.ble.BleDevice
import com.bss.companion.ble.ObstacleEvent
import com.bss.companion.ble.UrgencyLevel
import com.bss.companion.data.EventStats
import com.bss.companion.ui.theme.BssTheme

/**
 * Preview trong Android Studio (không chạy trên thiết bị).
 * Nhờ [BlindSpotScreen] là composable thuần nên chỉ cần bơm dữ liệu giả là xem được UI.
 */
@Preview(name = "Đang cảnh báo mức 2", showBackground = true, backgroundColor = 0xFF0B1220)
@Composable
private fun BlindSpotScreenPreview() {
    val now = System.currentTimeMillis()
    BssTheme {
        BlindSpotScreen(
            state = BssUiState(
                connection = BleConnectionState.Connected(
                    BleDevice("SmartGlasses-OB", "3C:71:BF:12:34:56", -58),
                ),
                latest = ObstacleEvent(742, UrgencyLevel.WARNING, 0, now),
                history = listOf(
                    ObstacleEvent(742, UrgencyLevel.WARNING, 0, now),
                    ObstacleEvent(1180, UrgencyLevel.NOTICE, 0, now - 4_000),
                    ObstacleEvent(430, UrgencyLevel.CRITICAL, 0, now - 9_000),
                    ObstacleEvent(9990, UrgencyLevel.SAFE, 0, now - 15_000),
                ),
                stats = EventStats(totalEvents = 128, criticalEvents = 3, closestDistanceMm = 430),
                hasPermissions = true,
            ),
            onStartScan = {},
            onStopScan = {},
            onConnect = {},
            onDisconnect = {},
            onClearHistory = {},
            onRequestPermission = {},
        )
    }
}

/** Preview trạng thái chưa kết nối / chưa có dữ liệu. */
@Preview(name = "Chưa có dữ liệu", showBackground = true, backgroundColor = 0xFF0B1220)
@Composable
private fun BlindSpotScreenIdlePreview() {
    BssTheme {
        BlindSpotScreen(
            state = BssUiState(hasPermissions = true),
            onStartScan = {},
            onStopScan = {},
            onConnect = {},
            onDisconnect = {},
            onClearHistory = {},
            onRequestPermission = {},
        )
    }
}
