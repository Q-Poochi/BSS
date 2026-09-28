#pragma once
#include <cstdint>

typedef struct {
  uint16_t distance_mm;
  uint32_t timestamp_ms;
  uint8_t  urgency_level;  // 0=an toàn, 1=chú ý, 2=gần, 3=khẩn cấp
  uint8_t  object_class;   // 0=unknown, điền khi có CV
} ObstacleEvent_t;