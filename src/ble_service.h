#pragma once
#include "event_types.h"

void ble_init();
void ble_notify(const ObstacleEvent_t &event);