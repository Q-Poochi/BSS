#pragma once
#include <Arduino.h>

// ---- Pin Mapping (Seeed Studio XIAO ESP32-S3) ----
#define TRIG_PIN D1  // Kích phát xung siêu âm (GPIO 2)
#define ECHO_PIN D2  // Thu sóng dải siêu âm (GPIO 3)
#define MOTOR_PIN D3 // Motor rung (GPIO 4)
#define LED_PIN D0   // LED cảnh báo (GPIO 1)

// ---- PWM (LEDC) ----
#define LEDC_CHANNEL_MOTOR 0
#define LEDC_CHANNEL_LED 1
#define LEDC_FREQ 100
#define LEDC_RES 8

// ---- Ngưỡng khoảng cách (cm) ----
#define THRESHOLD_CHU_Y_CM 150
#define THRESHOLD_GAN_CM 100
#define THRESHOLD_KHAN_CAP_CM 50

// ---- FreeRTOS Tasks ----
#define SENSOR_READ_INTERVAL_MS 100
#define SENSOR_TASK_PRIORITY 3
#define BLE_TASK_PRIORITY 1
#define SENSOR_TASK_STACK 4096
#define BLE_TASK_STACK 4096

// ---- Bluetooth Low Energy (BLE) ----
#define BLE_DEVICE_NAME "SmartGlasses-OB"
#define SERVICE_UUID "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"
