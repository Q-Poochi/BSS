package com.bss.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bss.companion.ble.ObstacleEvent
import com.bss.companion.data.EventStats
import com.bss.companion.ui.theme.toColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Số dòng tối đa hiển thị trên màn hình (phần còn lại nằm trong bộ đệm của repository). */
private const val MAX_ROWS = 20

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)

/**
 * Khối thống kê phiên + danh sách sự kiện gần đây.
 * Danh sách đã được repository lọc (chỉ ghi khi đổi mức hoặc dịch chuyển > 10cm).
 */
@Composable
fun HistorySection(
    history: List<ObstacleEvent>,
    stats: EventStats,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatTile(
                label = "TỔNG SỰ KIỆN",
                value = stats.totalEvents.toString(),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "KHẨN CẤP",
                value = stats.criticalEvents.toString(),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "GẦN NHẤT",
                value = if (stats.closestDistanceMm == Int.MAX_VALUE) {
                    "--"
                } else {
                    "${stats.closestDistanceCm.toInt()}cm"
                },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
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
                        text = "LỊCH SỬ SỰ KIỆN (${history.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onClearHistory) { Text("Xoá") }
                }

                if (history.isEmpty()) {
                    Text(
                        text = "Chưa có sự kiện nào. Dữ liệu sẽ xuất hiện khi kính gửi notify.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    history.take(MAX_ROWS).forEach { event ->
                        HistoryRow(event)
                    }
                }
            }
        }
    }
}

/** Ô thống kê nhỏ. */
@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Một dòng sự kiện: giờ · khoảng cách · mức cảnh báo. */
@Composable
private fun HistoryRow(event: ObstacleEvent, modifier: Modifier = Modifier) {
    val color = event.urgency.toColor()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatTime(event.receivedAtMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${formatDistanceText(event)} · ${event.objectLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = event.urgency.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.14f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

private fun formatTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(TIME_FORMATTER)

private fun formatDistanceText(event: ObstacleEvent): String =
    if (event.isOutOfRange) "Ngoài tầm" else String.format(Locale.US, "%.1f cm", event.distanceCm)
