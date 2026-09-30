package com.bss.companion.ble

/**
 * 4 mức cảnh báo do FIRMWARE quyết định (đã đi qua bộ lọc trung bình trượt 5 mẫu
 * và cơ chế trễ Hysteresis 10cm trên ESP32-S3).
 *
 * Nguyên tắc thiết kế: app Android chỉ HIỂN THỊ, tuyệt đối không tự tính lại mức
 * cảnh báo - tránh trường hợp app và thiết bị rung báo hai trạng thái khác nhau.
 */
enum class UrgencyLevel(
    val code: Int,
    val label: String,
    val description: String,
) {
    /** d > 150 cm - PWM motor = 0 */
    SAFE(0, "AN TOÀN", "Không có vật thể trong vùng điểm mù"),

    /** 100 < d <= 150 cm - PWM motor = 80 */
    NOTICE(1, "CHÚ Ý", "Có phương tiện đang tiến vào vùng điểm mù"),

    /** 50 < d <= 100 cm - PWM motor = 160 */
    WARNING(2, "CẢNH BÁO", "Vật thể đã ở rất gần, cần chuẩn bị xử lý"),

    /** 0 < d <= 50 cm - PWM motor = 255 */
    CRITICAL(3, "KHẨN CẤP", "Nguy cơ va chạm trực tiếp!");

    /** Mức này có cần rung/đổi màu cảnh báo hay không. */
    val isAlert: Boolean get() = this != SAFE

    companion object {
        /** Ánh xạ byte `urgency_level` -> enum. Giá trị lạ được coi là SAFE (fail-safe). */
        fun fromCode(code: Int): UrgencyLevel = entries.firstOrNull { it.code == code } ?: SAFE
    }
}
