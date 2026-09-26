#include <Arduino.h>
#define LED_PIN D0
#define MOTOR_PIN D3
// put function declarations here:
int myFunction(int, int);

void setup()
{
  Serial.begin(115200);
  pinMode(LED_PIN, OUTPUT);
  pinMode(MOTOR_PIN, OUTPUT);
  // put your setup code here, to run once:
  int result = myFunction(2, 3);
}

void loop()
{
  digitalWrite(LED_PIN, HIGH);
  digitalWrite(MOTOR_PIN, HIGH);
  Serial.println("LED and MOTOR, ON!");
  delay(1000);

  digitalWrite(LED_PIN, LOW);
  digitalWrite(MOTOR_PIN, LOW);
  Serial.println("LED and MOTOR, OFF!");
  delay(1000);

  // put your main code here, to run repeatedly:
}

// put function definitions here:
int myFunction(int x, int y)
{
  return x + y;
}
