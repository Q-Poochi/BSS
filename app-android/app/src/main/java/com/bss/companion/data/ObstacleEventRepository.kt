package com.bss.companion.data

import com.bss.companion.ble.BssBleManager
import com.bss.companion.ble.ObstacleEvent
import com.bss.companion.ble.UrgencyLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Thống kê nhanh của một phiên theo dõi. */
data class EventStats(
    val totalEvents: Int = 0,
    val criticalEvents: Int = 0,
    val closestDistanceMm: Int = Int.MAX_VALUE,
) {
    val closestDistanceCm: Float get() = if (closestDistanceMm == Int.MAX_VALUE) 0f else closestDistanceMm / 10f
}

/**
 * Bộ đệm sự kiện phía app - tầng trung gian giữa BLE và UI.
 *
 * Vai trò:
 *  - Gom sự kiện notify thành: bản mới nhất (cho đồng hồ khoảng cách) + lịch sử có lọc
 *    (cho danh sách sự kiện) + thống kê phiên.
 *  - Lịch sử chỉ ghi khi mức cảnh báo đổi hoặc khoảng cách lệch > 10 cm, tránh việc
 *    tạo 10 bản ghi/giây làm tràn danh sách trên màn hình.
 *  - Tách hẳn khỏi Android framework nên có thể unit test trên JVM.
 */
class ObstacleEventRepository(private val bleManager: BssBleManager) {

    private val _latest = MutableStateFlow<ObstacleEvent?>(null)
    val latest: StateFlow<ObstacleEvent?> = _latest.asStateFlow()

    private val _history = MutableStateFlow<List<ObstacleEvent>>(emptyList())
    val history: StateFlow<List<ObstacleEvent>> = _history.asStateFlow()

    private val _stats = MutableStateFlow(EventStats())
    val stats: StateFlow<EventStats> = _stats.asStateFlow()

    /** Bắt đầu lắng nghe luồng notify của firmware. Gọi một lần trong ViewModel. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            bleManager.events.collect { onEvent(it) }
        }
    }

    /**
     * Xử lý một bản tin. Hàm thuần logic, không phụ thuộc Android -> test được bằng JUnit.
     */
    fun onEvent(event: ObstacleEvent) {
        val previous = _latest.value
        _latest.value = event
        _stats.value = _stats.value.let {
            EventStats(
                totalEvents = it.totalEvents + 1,
                criticalEvents = it.criticalEvents + if (event.urgency == UrgencyLevel.CRITICAL) 1 else 0,
                closestDistanceMm = minOf(it.closestDistanceMm, event.distanceMm),
            )
        }

        if (shouldRecord(previous, event)) {
            _history.value = (listOf(event) + _history.value).take(MAX_HISTORY)
        }
    }

    /** Chỉ ghi lịch sử khi có thay đổi đáng kể (đổi mức hoặc dịch chuyển > 10 cm). */
    private fun shouldRecord(previous: ObstacleEvent?, current: ObstacleEvent): Boolean {
        if (previous == null) return true
        if (previous.urgency != current.urgency) return true
        return abs(previous.distanceMm - current.distanceMm) > HISTORY_DELTA_MM
    }

    fun clearHistory() {
        _history.value = emptyList()
        _stats.value = EventStats()
    }

    /** Xoá trạng thái khi ngắt kết nối để UI không hiển thị dữ liệu cũ. */
    fun resetLive() {
        _latest.value = null
    }

    companion object {
        private const val MAX_HISTORY = 200
        private const val HISTORY_DELTA_MM = 100
    }
}
