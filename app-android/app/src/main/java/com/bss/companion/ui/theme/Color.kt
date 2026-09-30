package com.bss.companion.ui.theme

import androidx.compose.ui.graphics.Color
import com.bss.companion.ble.UrgencyLevel

// ---- Nền tảng: bảng màu tối kiểu HUD, phù hợp hiển thị ngoài trời / trên kính ----
val BssBackground = Color(0xFF0B1220)
val BssSurface = Color(0xFF141E33)
val BssSurfaceVariant = Color(0xFF1E2B45)
val BssOutline = Color(0xFF2C3A56)
val BssAccent = Color(0xFF2E7DFF)
val BssOnSurface = Color(0xFFE6ECF7)
val BssMuted = Color(0xFF8FA3C4)

// ---- Màu theo mức cảnh báo (ánh xạ 1-1 với UrgencyLevel của firmware) ----
val UrgencySafeColor = Color(0xFF2ECC71)
val UrgencyNoticeColor = Color(0xFFFFC107)
val UrgencyWarningColor = Color(0xFFFF8A00)
val UrgencyCriticalColor = Color(0xFFFF3B30)

/** Trả về màu đại diện cho một mức cảnh báo do firmware gửi lên. */
fun UrgencyLevel.toColor(): Color = when (this) {
    UrgencyLevel.SAFE -> UrgencySafeColor
    UrgencyLevel.NOTICE -> UrgencyNoticeColor
    UrgencyLevel.WARNING -> UrgencyWarningColor
    UrgencyLevel.CRITICAL -> UrgencyCriticalColor
}
