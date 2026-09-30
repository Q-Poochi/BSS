package com.bss.companion.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Khoá hành vi của bộ giải mã payload 4 byte - điểm giao tiếp dễ vỡ nhất
 * giữa firmware ESP32-S3 (`src/ble_service.cpp`) và app Android.
 *
 * Chạy bằng: ./gradlew :app:testDebugUnitTest
 */
class ObstacleEventParserTest {

    /** Đóng gói payload y hệt cách firmware làm trong `ble_notify()`. */
    private fun payload(distanceMm: Int, urgency: Int, objectClass: Int) = byteArrayOf(
        (distanceMm and 0xFF).toByte(),        // LSB
        ((distanceMm shr 8) and 0xFF).toByte(), // MSB
        urgency.toByte(),
        objectClass.toByte(),
    )

    @Test
    fun `ghep dung khoang cach little endian`() {
        val event = ObstacleEvent.fromPayload(payload(distanceMm = 742, urgency = 2, objectClass = 0))
        requireNotNull(event)
        assertEquals(742, event.distanceMm)
        assertEquals(74.2f, event.distanceCm, 0.001f)
        assertEquals(UrgencyLevel.WARNING, event.urgency)
    }

    @Test
    fun `byte cao duoc ghep dung voi byte thap`() {
        val bytes = payload(distanceMm = 9990, urgency = 0, objectClass = 0)
        assertEquals(0x06.toByte(), bytes[0])
        assertEquals(0x27.toByte(), bytes[1])

        val event = requireNotNull(ObstacleEvent.fromPayload(bytes))
        assertEquals(9990, event.distanceMm)
        assertTrue(event.isOutOfRange)
    }

    @Test
    fun `payload sai kich thuoc bi bo qua chu khong crash`() {
        assertNull(ObstacleEvent.fromPayload(ByteArray(0)))
        assertNull(ObstacleEvent.fromPayload(byteArrayOf(1, 2, 3)))
        assertNull(ObstacleEvent.fromPayload(ByteArray(BssBleContract.PAYLOAD_SIZE - 1)))
    }

    @Test
    fun `payload thua byte van doc duoc 4 byte dau`() {
        val bytes = payload(distanceMm = 500, urgency = 3, objectClass = 0) + byteArrayOf(9, 9)
        val event = requireNotNull(ObstacleEvent.fromPayload(bytes))
        assertEquals(500, event.distanceMm)
        assertEquals(UrgencyLevel.CRITICAL, event.urgency)
    }

    @Test
    fun `ma muc canh bao la thi roi ve SAFE`() {
        assertEquals(UrgencyLevel.SAFE, ObstacleEvent.fromPayload(payload(100, 7, 0))!!.urgency)
        assertEquals(UrgencyLevel.SAFE, ObstacleEvent.fromPayload(payload(100, 255, 0))!!.urgency)
    }

    @Test
    fun `byte urgency 255 khong bi hieu thanh -1`() {
        // Lỗi kinh điển: byte 0xFF bị mở rộng dấu thành -1 rồi không khớp enum nào.
        val raw = payload(distanceMm = 1000, urgency = 3, objectClass = 255)
        val event = requireNotNull(ObstacleEvent.fromPayload(raw))
        assertEquals(3, event.urgency.code)
        assertEquals(255, event.objectClass)
    }

    @Test
    fun `phan loai vat the hien thi dung nhan`() {
        assertEquals("Chưa xác định", ObstacleEvent.fromPayload(payload(100, 0, 0))!!.objectLabel)
        assertEquals("Người đi bộ", ObstacleEvent.fromPayload(payload(100, 0, 1))!!.objectLabel)
    }

    @Test
    fun `moc thoi gian nhan duoc duoc gan tu tham so`() {
        val event = requireNotNull(ObstacleEvent.fromPayload(payload(300, 1, 0), now = 1234L))
        assertEquals(1234L, event.receivedAtMs)
    }
}
