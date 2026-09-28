#include <Arduino.h>
#include "sensor_tof.h"
#include "config.h"

static float read_distance_cm()
{
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  long duration = pulseIn(ECHO_PIN, HIGH, 30000);
  if (duration == 0)
    return 999.0f;
  return duration * 0.0343f / 2.0f;

  // TODO: khi có VL53L1X thật, thay toàn bộ hàm này bằng
  // sensor.startRanging()/getDistance() — không đổi gì bên ngoài file.
}

static float window[FILTER_WINDOW];
static uint8_t win_count = 0, win_idx = 0;
static float filter_push(float v)
{
  window[win_idx] = v;
  win_idx = (win_idx + 1) % FILTER_WINDOW;
  if (win_count < FILTER_WINDOW)
    win_count++;
  float sum = 0;
  for (uint8_t i = 0; i < win_count; i++)
    sum += window[i];
  return sum / win_count;
}

static uint8_t distance_to_urgency(float d)
{
  // Mốc để RỜI khỏi từng mức khi khoảng cách tăng lại
  static const float LEAVE_CM[4] = {0, THRESHOLD_CHU_Y_CM, THRESHOLD_GAN_CM, THRESHOLD_KHAN_CAP_CM};
  static uint8_t level = 0;

  // Fail-safe: <= 0cm hoặc > 150cm đều coi là vùng an toàn (Level 0)
  uint8_t raw;
  if (d <= 0.0f || d > THRESHOLD_CHU_Y_CM)
    raw = 0;
  else if (d > THRESHOLD_GAN_CM)
    raw = 1;
  else if (d > THRESHOLD_KHAN_CAP_CM)
    raw = 2;
  else
    raw = 3;

  if (raw > level)
  {
    level = raw; // nguy hiểm hơn: báo ngay lập tức
  }
  else if (raw < level && d > LEAVE_CM[level] + HYSTERESIS_CM)
  {
    level = raw; // an toàn hơn: phải vượt mốc + 10cm mới hạ cấp
  }
  return level;
}

void sensor_init()
{
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);
}

ObstacleEvent_t sensor_read_event()
{
  float distance_cm = filter_push(read_distance_cm());
  ObstacleEvent_t e;
  e.distance_mm = (uint16_t)(distance_cm * 10);
  e.timestamp_ms = millis();
  e.urgency_level = distance_to_urgency(distance_cm);
  e.object_class = 0;
  return e;
}