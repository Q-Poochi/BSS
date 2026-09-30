package com.bss.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bss.companion.ble.BleConnectionState
import com.bss.companion.ble.BleDevice
import com.bss.companion.ui.theme.UrgencyCriticalColor
import com.bss.companion.ui.theme.UrgencyNoticeColor
import com.bss.companion.ui.theme.UrgencySafeColor

/**
 * Khối quản lý kết nối: trạng thái, nút quét/kết nối và danh sách thiết bị tìm thấy.
 * Không giữ state nội bộ - mọi thứ do ViewModel đẩy xuống (mô hình stateless composable).
 */
@Composable
fun ConnectionPanel(
    state: BleConnectionState,
    devices: List<BleDevice>,
    hasPermissions: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (BleDevice) -> Unit,
    onDisconnect: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "KẾT NỐI THIẾT BỊ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StateChip(state)
            }

            Spacer(Modifier.height(14.dp))

            if (!hasPermissions) {
                Text(
                    text = "App cần quyền Bluetooth để quét và kết nối với kính BSS.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                    Text("Cấp quyền Bluetooth")
                }
                return@Column
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state is BleConnectionState.Scanning) {
                    OutlinedButton(onClick = onStopScan) { Text("Dừng quét") }
                } else {
                    Button(onClick = onStartScan, enabled = !state.isBusy) { Text("Quét thiết bị") }
                }
                if (state is BleConnectionState.Connected || state is BleConnectionState.Connecting) {
                    OutlinedButton(onClick = onDisconnect) { Text("Ngắt kết nối") }
                }
            }

            val activeDevice = when (state) {
                is BleConnectionState.Connected -> state.device
                is BleConnectionState.Connecting -> state.device
                is BleConnectionState.Disconnected -> state.device
                else -> null
            }

            if (activeDevice != null) {
                Spacer(Modifier.height(16.dp))
                DeviceInfoCard(activeDevice)
            }

            if (devices.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "THIẾT BỊ TÌM THẤY (${devices.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                devices.take(MAX_DEVICE_ROWS).forEach { device ->
                    DeviceRow(device = device, onConnect = { onConnect(device) })
                }
            } else if (state is BleConnectionState.Scanning) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Đang tìm SmartGlasses-OB…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val MAX_DEVICE_ROWS = 8

/** Chip nhỏ hiển thị trạng thái kết nối với màu tương ứng. */
@Composable
private fun StateChip(state: BleConnectionState, modifier: Modifier = Modifier) {
    val (label, color) = stateChipAppearance(state)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Thẻ thông tin thiết bị đang kết nối (tên, MAC, RSSI). */
@Composable
private fun DeviceInfoCard(device: BleDevice, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "MAC ${device.address}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "RSSI ${device.rssi} dBm",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Một dòng trong danh sách kết quả quét; bấm vào để kết nối. */
@Composable
private fun DeviceRow(
    device: BleDevice,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onConnect)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${device.address} · ${device.rssi} dBm",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onConnect) { Text("Kết nối") }
    }
}

/** Nhãn + màu cho từng trạng thái kết nối (giữ UI và logic tách rời nhau). */
private fun stateChipAppearance(state: BleConnectionState): Pair<String, Color> = when (state) {
    is BleConnectionState.Idle -> "CHƯA KẾT NỐI" to UrgencyNoticeColor
    is BleConnectionState.Scanning -> "ĐANG QUÉT" to UrgencyNoticeColor
    is BleConnectionState.Connecting -> "ĐANG KẾT NỐI" to UrgencyNoticeColor
    is BleConnectionState.Connected -> "ĐÃ KẾT NỐI" to UrgencySafeColor
    is BleConnectionState.Disconnected -> "MẤT KẾT NỐI" to UrgencyCriticalColor
    is BleConnectionState.Failed -> "LỖI" to UrgencyCriticalColor
    is BleConnectionState.BluetoothOff -> "BLUETOOTH TẮT" to UrgencyCriticalColor
    is BleConnectionState.Unsupported -> "KHÔNG HỖ TRỢ BLE" to UrgencyCriticalColor
}
