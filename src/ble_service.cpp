#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include "ble_service.h"
#include "config.h"

static BLECharacteristic *pCharacteristic = nullptr;
static bool deviceConnected = false;

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* server) override { deviceConnected = true; }
  void onDisconnect(BLEServer* server) override {
    deviceConnected = false;
    server->getAdvertising()->start(); // advertise lại khi mất kết nối
  }
};

void ble_init() {
  BLEDevice::init(BLE_DEVICE_NAME);
  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);
  pCharacteristic = pService->createCharacteristic(
      CHARACTERISTIC_UUID,
      BLECharacteristic::PROPERTY_NOTIFY
  );
  pCharacteristic->addDescriptor(new BLE2902());
  pService->start();
  pServer->getAdvertising()->start();
}

void ble_notify(const ObstacleEvent_t &event) {
  if (!deviceConnected) return; // không tốn công serialize nếu chưa có ai nghe

  uint8_t payload[4];
  payload[0] = event.distance_mm & 0xFF;
  payload[1] = (event.distance_mm >> 8) & 0xFF;
  payload[2] = event.urgency_level;
  payload[3] = event.object_class;

  pCharacteristic->setValue(payload, sizeof(payload));
  pCharacteristic->notify();
}