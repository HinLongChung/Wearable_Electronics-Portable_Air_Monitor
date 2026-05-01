#include <SensirionI2CSen5x.h>
#include <SensirionCore.h>
#include <SensirionI2cScd4x.h>
#include <Wire.h>
#include "DFRobot_RGBLCD1602.h"
#include "DFRobot_MICS.h"
#undef NO
#include "DFRobot_OzoneSensor.h"
#include <ArduinoBLE.h>
BLEService aqiService("19B10000-E8F2-537E-4F6C-D104768A1214");
BLEIntCharacteristic statusChar("19B10001-E8F2-537E-4F6C-D104768A1214", BLERead | BLENotify);
BLEIntCharacteristic aqiChar("19B10002-E8F2-537E-4F6C-D104768A1214", BLERead | BLENotify);
BLEFloatCharacteristic pm25Char("19B10003-E8F2-537E-4F6C-D104768A1214", BLERead | BLENotify);
BLEFloatCharacteristic pm10Char("19B10004-E8F2-537E-4F6C-D104768A1214", BLERead | BLENotify);
BLEIntCharacteristic co2Char("19B10005-E8F2-537E-4F6C-D104768A1214", BLERead | BLENotify);
BLEFloatCharacteristic so2Char("19B10006-E8F2-537E-4F6C-D104768A1214", BLERead | BLENotify);
BLEFloatCharacteristic no2Char("19B10007-E8F2-537E-4F6C-D104768A1214", BLERead | BLENotify);
BLEFloatCharacteristic o3Char("19B10008-E8F2-537E-4F6C-D104768A1214", BLERead | BLENotify);
DFRobot_RGBLCD1602 lcd(/*RGBAddr*/0x6B ,/*lcdCols*/16,/*lcdRows*/2);
SensirionI2CSen5x sen5x;
SensirionI2cScd4x CO2sensor;
#define ADC_PIN   A2
#define POWER_PIN 8
#define COLLECT_NUMBER   50             
#define Ozone_IICAddress OZONE_ADDRESS_3
DFRobot_OzoneSensor Ozone;
DFRobot_MICS_ADC mics(/*adcPin*/ADC_PIN, /*powerPin*/POWER_PIN);
static uint16_t reading;
static int16_t error;
const int buttonPin = 2;
static int count=0;
int sensorStatus = 0;
static unsigned long lastBLEUpdate = 0;
// --- Configuration Constants ---
const int analogInPin = A1;    // Analog input pin 
const float resValue = 100000.0; // 100 kOhm feedback resistor on the Op-Amp
const float Vref = 1.5;        // Voltage of the AR_INTERNAL reference for UNO R4
const float Sf = 25.0;        // Sensitivity in nA/ppm for the 3SP_SO2_20 sensor [cite: 28]
const int extraBit = 256;      // Number of samples averaged (oversampling)
int state=0;
long int cOff = 111000;         
long int sensorValue = 0;    // Raw value read from the sensor
float SO2conc = 0.0;
const float so2SmoothingFactor = 0.1; // 0.1 = Very smooth/slow, 0.5 = Faster/twitchier
bool firstSO2Reading = true;          // Helps the math snap to the right number on startup

void setup() {
  Wire.begin();
  Serial.begin(115200);
  lcd.init();
  lcd.setRGB(255, 255, 255);
  lcd.clear(); 
  lcd.setCursor(0,0);
  lcd.print("Calibrating");
  lcd.setCursor(0,1);
  lcd.print("Please wait...");
  delay(500);
  sen5x.begin(Wire);
  pinMode(buttonPin, INPUT_PULLUP);
  randomSeed(analogRead(A0));
  delay(500);
  CO2sensor.begin(Wire, SCD41_I2C_ADDR_62);
  delay(500);
  reading=sen5x.startMeasurement();
  error=CO2sensor.wakeUp();
  error=CO2sensor.startPeriodicMeasurement();
  analogReference(AR_INTERNAL);
  analogReadResolution(10);
  delay(500);
  Ozone.begin(Ozone_IICAddress);
  Ozone.setModes(MEASURE_MODE_PASSIVE);
  delay(500);
  mics.begin();
  mics.wakeUpMode();
  long int so2_calibration_total = 0;
  int so2_calibration_reads = 0;
  while(!mics.warmUpTime(3)){
    long int temp_sensorValue = 0;
      analogReadResolution(14);
      // Take rapid samples from the SO2 Op-Amp
      for (int i = 0; i < extraBit; i++) {
        temp_sensorValue = analogRead(analogInPin);
        so2_calibration_total += temp_sensorValue;
        so2_calibration_reads++;
        delay(3); 
      }
      analogReadResolution(10); 
  }
  if (so2_calibration_reads > 0) {
      cOff = so2_calibration_total / so2_calibration_reads;
  }
  BLE.begin();
  BLE.setLocalName("SpeedoData AQI"); 
  BLE.setAdvertisedService(aqiService);
  aqiService.addCharacteristic(statusChar);
  aqiService.addCharacteristic(aqiChar);
  aqiService.addCharacteristic(pm25Char);
  aqiService.addCharacteristic(pm10Char);
  aqiService.addCharacteristic(co2Char);
  aqiService.addCharacteristic(so2Char);
  aqiService.addCharacteristic(no2Char);
  aqiService.addCharacteristic(o3Char);
  BLE.addService(aqiService);
  BLE.advertise();
  lcd.setCursor(0,0);
  lcd.print("Accurate values");
  lcd.setCursor(0,1);
  lcd.print("after 20 mins");
  delay(5000);
  lcd.clear();
}

void loop() {
  BLE.poll();
  Serial.println(cOff);
  sensorValue = 0;
  uint16_t co2Concentration;
  float temperature = 0.0;
  float relativeHumidity = 0.0;
  int AQI25, AQI10, AQISO2, AQINO2, AQIO3;
  float massConcentrationPm1p0;
  float massConcentrationPm2p5;
  float massConcentrationPm4p0;
  float massConcentrationPm10p0;
  float ambientHumidity;
  float ambientTemperature;
  float vocIndex;
  float noxIndex;
  int buttonState = digitalRead(buttonPin);
  analogReadResolution(14);
  sensorValue = analogRead(analogInPin);
  long int adjustedValue = sensorValue - cOff;
  Serial.println(sensorValue);
  float current_nA = ((float)adjustedValue / extraBit / 16384.0 * Vref / resValue * 1000000000.0);
  float SO2_ppm = current_nA / Sf;
  float current_ugm3 = SO2_ppm * 2620.0;
  if (firstSO2Reading) {
      SO2conc = current_ugm3;
      firstSO2Reading = false;
  } else {
      // Smooth out the noise 
      SO2conc = (so2SmoothingFactor * current_ugm3) + ((1.0 - so2SmoothingFactor) * SO2conc);
  }
  if (SO2conc < 0.0) {
      SO2conc = 0.0;
  }
  reading = sen5x.readMeasuredValues(
    massConcentrationPm1p0, massConcentrationPm2p5, massConcentrationPm4p0,
    massConcentrationPm10p0, ambientHumidity, ambientTemperature, vocIndex,
    noxIndex);
  error = CO2sensor.readMeasurement(co2Concentration, temperature, relativeHumidity);
  analogReadResolution(10);
  float NO2conc = mics.getGasData(NO2);
  NO2conc=(NO2conc*1912.5);
  int16_t O3conc = Ozone.readOzoneData(COLLECT_NUMBER);
  O3conc=O3conc*1.9957;//take readings
  if(buttonState==LOW){
    //simple state machine
    if(count==10){
      NVIC_SystemReset();//if button was pressed for 10 seconds the system will restart
    }
    else if(state<2){
      state++;
      lcd.clear();
      count++;
    }
    else{
      state=0;
      lcd.clear();
      count++;
    }
  }else{
    count=0;
  }
  //calculate AQI
  if(massConcentrationPm2p5<12){
    AQI25=1;
  }else if(massConcentrationPm2p5<24){
    AQI25=2;
  }else if(massConcentrationPm2p5<36){
    AQI25=3;
  }else if(massConcentrationPm2p5<42){
    AQI25=4;
  }else if(massConcentrationPm2p5<48){
    AQI25=5;
  }else if(massConcentrationPm2p5<54){
    AQI25=6;
  }else if(massConcentrationPm2p5<59){
    AQI25=7;
  }else if(massConcentrationPm2p5<65){
    AQI25=8;
  }else if(massConcentrationPm2p5<71){
    AQI25=9;
  }else{
    AQI25=10;
  }
  if(massConcentrationPm10p0<17){
    AQI10=1;
  }else if(massConcentrationPm10p0<34){
    AQI10=2;
  }else if(massConcentrationPm10p0<51){
    AQI10=3;
  }else if(massConcentrationPm10p0<59){
    AQI10=4;
  }else if(massConcentrationPm10p0<67){
    AQI10=5;
  }else if(massConcentrationPm10p0<76){
    AQI10=6;
  }else if(massConcentrationPm10p0<84){
    AQI10=7;
  }else if(massConcentrationPm10p0<92){
    AQI10=8;
  }else if(massConcentrationPm10p0<101){
    AQI10=9;
  }else{
    AQI10=10;
  }
  if(O3conc<34){
    AQIO3=1;
  }else if(O3conc<67){
    AQIO3=2;
  }else if(O3conc<101){
    AQIO3=3;
  }else if(O3conc<121){
    AQIO3=4;
  }else if(O3conc<141){
    AQIO3=5;
  }else if(O3conc<161){
    AQIO3=6;
  }else if(O3conc<188){
    AQIO3=7;
  }else if(O3conc<214){
    AQIO3=8;
  }else if(O3conc<241){
    AQIO3=9;
  }else{
    AQIO3=10;
  }
  if(NO2conc<68){
    AQINO2=1;
  }else if(NO2conc<135){
    AQINO2=2;
  }else if(NO2conc<201){
    AQINO2=3;
  }else if(NO2conc<268){
    AQINO2=4;
  }else if(NO2conc<335){
    AQINO2=5;
  }else if(NO2conc<401){
    AQINO2=6;
  }else if(NO2conc<468){
    AQINO2=7;
  }else if(NO2conc<535){
    AQINO2=8;
  }else if(NO2conc<601){
    AQINO2=9;
  }else{
    AQINO2=10;
  }
  if(SO2conc<89){
    AQISO2=1;
  }else if(SO2conc<178){
    AQISO2=2;
  }else if(SO2conc<267){
    AQISO2=3;
  }else if(SO2conc<355){
    AQISO2=4;
  }else if(SO2conc<444){
    AQISO2=5;
  }else if(SO2conc<533){
    AQISO2=6;
  }else if(SO2conc<711){
    AQISO2=7;
  }else if(SO2conc<888){
    AQISO2=8;
  }else if(SO2conc<1065){
    AQISO2=9;
  }else{
    AQISO2=10;
  }
  if(state==1){
    //printing to LCD
    lcd.setCursor(0, 0);
    lcd.print("2.5:");
    lcd.setCursor(4, 0);
    lcddisplay(massConcentrationPm2p5);
    lcd.setCursor(8, 0);
    lcd.print(" 10:");
    lcd.setCursor(12, 0);
    lcddisplay(massConcentrationPm10p0);
  }else if(state==0){
    lcd.setCursor(0, 0);
    lcd.print("CO2:");
    lcd.setCursor(4, 0);
    lcd.print(co2Concentration);
    lcd.print(" ");
    lcd.setCursor(8, 0);
    lcd.print("SO2:");
    lcd.setCursor(12,0);
    lcddisplay(SO2conc);
  }else if(state==2){
    lcd.setCursor(0, 0);
    lcd.print("NO2:");
    lcd.setCursor(4, 0);
    lcd.print(NO2conc);
    lcd.setCursor(8, 0);
    lcd.print(" O3:");
    lcd.setCursor(12, 0);
    lcd.print(O3conc);
    lcd.print(" ");
  }else{
    lcd.setCursor(0, 0);
    lcd.print("error");
  }
  int overallAQI;
  overallAQI = AQI25;
  if (AQI10 > overallAQI)  overallAQI = AQI10;  
  if (AQISO2 > overallAQI) overallAQI = AQISO2; 
  if (AQINO2 > overallAQI) overallAQI = AQINO2; 
  if (AQIO3 > overallAQI)  overallAQI = AQIO3; //determine the AQI based on the highest number
  lcd.setCursor(0, 1);
  lcd.print(overallAQI);
  lcd.print(" ");
  lcd.setCursor(3,1);
  lcd.print(ambientHumidity);
  lcd.setCursor(7,1);
  lcd.print("%");
  lcd.setCursor(10,1);
  lcd.print(ambientTemperature);
  lcd.setCursor(14,1);
  lcd.print("C");
  if (millis() < 1200000) {
      sensorStatus = 0; 
  } else {
      sensorStatus = 1; 
  }
  if (millis() - lastBLEUpdate >= 60000) {
      //update via BLE every minute
      lastBLEUpdate = millis(); 
      int sensorStatus = (millis() < 1200000) ? 0 : 1;
      statusChar.writeValue(sensorStatus);
      aqiChar.writeValue(overallAQI);
      pm25Char.writeValue(massConcentrationPm2p5);
      pm10Char.writeValue(massConcentrationPm10p0);
      co2Char.writeValue(co2Concentration);
      so2Char.writeValue(SO2conc);
      no2Char.writeValue(NO2conc);
      o3Char.writeValue((float)O3conc);
  }
  delay(1000);
}

void lcddisplay(float input){ //printing function that converts the number to 3s.f automatically
  if(input<100){
    lcd.print(input);
  }else{
    lcd.print(int(input));
    lcd.print(" ");
  }
  return;
}