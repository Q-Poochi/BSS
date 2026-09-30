package com.bss.companion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bss.companion.ui.theme.BssTheme

/**
 * Điểm vào của toàn bộ UI: bọc theme, thu thập [BssViewModel.uiState]
 * (chỉ khi app đang hiển thị nhờ collectAsStateWithLifecycle) rồi đẩy xuống màn hình.
 */
@Composable
fun BssApp(
    viewModel: BssViewModel,
    onRequestPermission: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BssTheme {
        BlindSpotScreen(
            state = state,
            onStartScan = viewModel::startScan,
            onStopScan = viewModel::stopScan,
            onConnect = viewModel::connect,
            onDisconnect = viewModel::disconnect,
            onClearHistory = viewModel::clearHistory,
            onRequestPermission = onRequestPermission,
        )
    }
}
