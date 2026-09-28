#include <Arduino.h>
#include "sensor_tof.h"
#include "config.h"

static float read_distance_cm() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  long duration = pulseIn(ECHO_PIN, HIGH, 30000);
  if (duration == 0) return 999.0f;
  return duration * 0.0343f / 2.0f;

  // TODO: khi có VL53L1X thật, thay toàn bộ hàm này bằng
  // sensor.startRanging()/getDistance() — không đổi gì bên ngoài file.
}

static uint8_t distance_to_urgency(float distance_cm) {
  // Fail-safe: nếu lỗi đo (<= 0cm) hoặc ngoài tầm (> 150cm) => Level 0 (An toàn tuyệt đối)
  if (distance_cm <= 0.0f || distance_cm > THRESHOLD_CHU_Y_CM) return 0;
  if (distance_cm > THRESHOLD_GAN_CM)                          return 1; // 100cm - 150cm
  if (distance_cm > THRESHOLD_KHAN_CAP_CM)                     return 2; // 50cm - 100cm
  return 3;                                                              // < 50cm (Khẩn cấp)
}

void sensor_init() {
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
}

ObstacleEvent_t sensor_read_event() {
  float distance_cm = read_distance_cm();
  ObstacleEvent_t e;
  e.distance_mm   = (uint16_t)(distance_cm * 10);
  e.timestamp_ms  = millis();
  e.urgency_level = distance_to_urgency(distance_cm);
  e.object_class  = 0;
  return e;
}