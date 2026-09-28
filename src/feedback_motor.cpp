#include <Arduino.h>
#include "feedback_motor.h"
#include "config.h"

static uint8_t urgency_to_pwm(uint8_t level) {
  switch (level) {
    case 0: return 0;
    case 1: return 80;
    case 2: return 160;
    case 3: return 255;
    default: return 0;
  }
}

void motor_init() {
  ledcSetup(LEDC_CHANNEL_MOTOR, LEDC_FREQ, LEDC_RES);
  ledcAttachPin(MOTOR_PIN, LEDC_CHANNEL_MOTOR);
  ledcSetup(LEDC_CHANNEL_LED, LEDC_FREQ, LEDC_RES);
  ledcAttachPin(LED_PIN, LEDC_CHANNEL_LED);
  motor_set_level(0);
}

void motor_set_level(uint8_t urgency_level) {
  uint8_t pwm = urgency_to_pwm(urgency_level);
  ledcWrite(LEDC_CHANNEL_MOTOR, pwm);
  ledcWrite(LEDC_CHANNEL_LED, pwm); // mô phỏng, bỏ khi có motor thật

  // Ngắt triệt để điện áp về LOW khi ở Level 0 để tránh giữ xung trên Wokwi/ESP32
  if (pwm == 0) {
    digitalWrite(MOTOR_PIN, LOW);
    digitalWrite(LED_PIN, LOW);
  }
}