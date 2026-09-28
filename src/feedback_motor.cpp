#include <Arduino.h>
#include "feedback_motor.h"
#include "config.h"

static bool is_attached = false;

static uint8_t urgency_to_pwm(uint8_t level)
{
  switch (level)
  {
  case 0:
    return 0;
  case 1:
    return 80;
  case 2:
    return 160;
  case 3:
    return 255;
  default:
    return 0;
  }
}

void motor_init()
{
  ledcSetup(LEDC_CHANNEL_MOTOR, LEDC_FREQ, LEDC_RES);
  ledcSetup(LEDC_CHANNEL_LED, LEDC_FREQ, LEDC_RES);

  pinMode(MOTOR_PIN, OUTPUT);
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(MOTOR_PIN, LOW);
  digitalWrite(LED_PIN, LOW);
  is_attached = false;
}

void motor_set_level(uint8_t urgency_level)
{
  uint8_t pwm = urgency_to_pwm(urgency_level);

  if (pwm == 0)
  {
    // Nếu chân đang gán cho LEDC, gỡ bỏ để ép về GPIO LOW tuyệt đối
    if (is_attached)
    {
      ledcWrite(LEDC_CHANNEL_MOTOR, 0);
      ledcWrite(LEDC_CHANNEL_LED, 0);

      ledcDetachPin(MOTOR_PIN);
      ledcDetachPin(LED_PIN);
      is_attached = false;
    }
    pinMode(MOTOR_PIN, OUTPUT);
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(MOTOR_PIN, LOW);
    digitalWrite(LED_PIN, LOW);
  }
  else
  {
    // Khi có cảnh báo (Level 1, 2, 3), gắn lại chân vào bộ phát xung LEDC
    if (!is_attached)
    {
      ledcAttachPin(MOTOR_PIN, LEDC_CHANNEL_MOTOR);
      ledcAttachPin(LED_PIN, LEDC_CHANNEL_LED);
      is_attached = true;
    }
    ledcWrite(LEDC_CHANNEL_MOTOR, pwm);
    ledcWrite(LEDC_CHANNEL_LED, pwm);
  }
}