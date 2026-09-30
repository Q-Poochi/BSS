package com.bss.companion.ble

/**
 * Một bản tin sự kiện va chạm, giải mã từ payload 4 byte do ESP32-S3 notify.
 *
 * @param distanceMm   khoảng cách (mm) - đã được firmware lọc trung bình trượt 5 mẫu,
 *                     giá trị 9990 mm (999.0 cm) nghĩa là "ngoài tầm đo / timeout".
 * @param urgency      mức cảnh báo do firmware quyết định (đã có hysteresis).
 * @param objectClass  0 = unknown, dành sẵn cho tầng AI/CV phân loại vật thể.
 * @param receivedAtMs mốc thời gian phía app (epoch ms) để tính "tuổi" bản tin.
 */
data class ObstacleEvent(
    val distanceMm: Int,
    val urgency: UrgencyLevel,
    val objectClass: Int,
    val receivedAtMs: Long,
) {
    /** Khoảng cách quy đổi sang cm, dùng để hiển thị ("128.4 cm"). */
    val distanceCm: Float get() = distanceMm / 10f

    /** True khi firmware trả về giá trị bão hoà 9990 mm (pulseIn timeout). */
    val isOutOfRange: Boolean get() = distanceMm >= 9990

    /** Nhãn vật thể - hiện luôn là "Chưa xác định" vì firmware chưa có thị giác máy. */
    val objectLabel: String
        get() = when (objectClass) {
            0 -> "Chưa xác định"
            1 -> "Người đi bộ"
            2 -> "Xe máy"
            3 -> "Ô tô"
            else -> "Loại #$objectClass"
        }

    companion object {
        /** Kích thước payload hợp lệ (byte). */
        const val SIZE = BssBleContract.PAYLOAD_SIZE

        /**
         * Giải mã payload notify của firmware:
         * ```
         * payload[0] =  distance_mm        & 0xFF   <- LSB (little-endian)
         * payload[1] = (distance_mm >> 8)  & 0xFF   <- MSB
         * payload[2] =  urgency_level               (0..3)
         * payload[3] =  object_class                (0 = unknown)
         * ```
         *
         * @return null nếu payload sai kích thước -> app bỏ qua bản tin thay vì crash.
         */
        fun fromPayload(
            payload: ByteArray,
            now: Long = System.currentTimeMillis(),
        ): ObstacleEvent? {
            if (payload.size < SIZE) return null

            val distanceMm =
                (payload[BssBleContract.OFFSET_DISTANCE_LSB].toInt() and 0xFF) or
                    ((payload[BssBleContract.OFFSET_DISTANCE_MSB].toInt() and 0xFF) shl 8)

            return ObstacleEvent(
                distanceMm = distanceMm,
                urgency = UrgencyLevel.fromCode(payload[BssBleContract.OFFSET_URGENCY].toInt() and 0xFF),
                objectClass = payload[BssBleContract.OFFSET_OBJECT_CLASS].toInt() and 0xFF,
                receivedAtMs = now,
            )
        }
    }
}
