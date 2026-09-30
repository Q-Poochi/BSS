package com.bss.companion.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bss.companion.ble.ObstacleEvent
import com.bss.companion.ble.UrgencyLevel
import com.bss.companion.ui.theme.DistanceDigitsStyle
import com.bss.companion.ui.theme.toColor
import java.util.Locale

/** Khoảng cách tối đa dùng để vẽ thanh tiến trình (cm). */
private const val GAUGE_MAX_CM = 200f

/**
 * Khối hiển thị chính: số đo khoảng cách + thanh tiến trình + băng mức cảnh báo.
 * Toàn bộ số liệu đều đến từ firmware, app không tự suy diễn lại.
 */
@Composable
fun DistancePanel(
    latest: ObstacleEvent?,
    modifier: Modifier = Modifier,
) {
    val urgency = latest?.urgency ?: UrgencyLevel.SAFE
    val accent = urgency.toColor()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "KHOẢNG CÁCH VẬT THỂ GẦN NHẤT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatDistance(latest),
                    style = DistanceDigitsStyle,
                    color = accent,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "cm",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }

            Spacer(Modifier.height(4.dp))

            if (latest != null) {
                Text(
                    text = "${latest.distanceMm} mm · ${latest.objectLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            ProximityGauge(
                fraction = proximityFraction(latest),
                color = accent,
            )

            Spacer(Modifier.height(18.dp))

            UrgencyBanner(urgency)
        }
    }
}

/** Thanh tiến trình mô phỏng mức độ "gần": càng gần vật thể thanh càng đầy. */
@Composable
private fun ProximityGauge(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(color),
            )
        }
    }
}

/** Băng màu + mô tả mức cảnh báo hiện tại. */
@Composable
private fun UrgencyBanner(urgency: UrgencyLevel, modifier: Modifier = Modifier) {
    val color = urgency.toColor()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = urgency.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = urgency.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Ngoài tầm đo hoặc chưa có dữ liệu thì hiển thị gạch ngang thay vì số vô nghĩa. */
private fun formatDistance(latest: ObstacleEvent?): String = when {
    latest == null -> "--.-"
    latest.isOutOfRange -> "--.-"
    else -> String.format(Locale.US, "%.1f", latest.distanceCm)
}

/** Quy đổi khoảng cách thành tỉ lệ 0..1 cho thanh tiến trình. */
private fun proximityFraction(latest: ObstacleEvent?): Float {
    if (latest == null || latest.isOutOfRange) return 0f
    val cm = latest.distanceCm
    return (1f - (cm / GAUGE_MAX_CM)).coerceIn(0f, 1f)
}
