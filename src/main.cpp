#include <Arduino.h>
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>
#include <freertos/queue.h>
#include "config.h"
#include "event_types.h"
#include "sensor_tof.h"
#include "feedback_motor.h"
#include "ble_service.h"

static QueueHandle_t ble_queue;

void sensor_task(void *pv)
{
  for (;;)
  {
    ObstacleEvent_t event = sensor_read_event();

    // Nhánh 1: phản hồi rung — gọi TRỰC TIẾP, không qua queue, độ trễ tối thiểu
    motor_set_level(event.urgency_level);

    // Dùng \r\n để dòng in trên Serial Monitor luôn thẳng hàng, không bị thụt bậc thang
    Serial.printf("Khoang cach: %.1f cm | Urgency: %d\r\n",
                  event.distance_mm / 10.0, event.urgency_level);

    // Nhánh 2: gửi cho ble_task — overwrite, chỉ giữ giá trị mới nhất
    xQueueOverwrite(ble_queue, &event);

    vTaskDelay(pdMS_TO_TICKS(SENSOR_READ_INTERVAL_MS));
  }
}

void ble_task(void *pv)
{
  ObstacleEvent_t event;
  uint8_t last_level = 255;
  uint16_t last_mm = 0;
  uint32_t last_sent = 0;

  for (;;)
  {
    if (xQueueReceive(ble_queue, &event, portMAX_DELAY) != pdTRUE)
      continue;

    bool level_changed = event.urgency_level != last_level;
    bool moved = abs((int)event.distance_mm - (int)last_mm) > 100; // >10cm
    bool heartbeat = event.timestamp_ms - last_sent >= 1000;

    if (level_changed || moved || heartbeat)
    {
      ble_notify(event);
      last_level = event.urgency_level;
      last_mm = event.distance_mm;
      last_sent = event.timestamp_ms;
    }
  }
}

void setup()
{
  Serial.begin(115200);
  sensor_init();
  motor_init();
  ble_init();

  ble_queue = xQueueCreate(1, sizeof(ObstacleEvent_t)); // length=1 => luôn là bản mới nhất

  xTaskCreate(sensor_task, "sensor_task", SENSOR_TASK_STACK, nullptr, SENSOR_TASK_PRIORITY, nullptr);
  xTaskCreate(ble_task, "ble_task", BLE_TASK_STACK, nullptr, BLE_TASK_PRIORITY, nullptr);
}

void loop()
{
  vTaskDelete(nullptr); // toàn bộ logic đã chuyển vào 2 task, không dùng loop() mặc định
}