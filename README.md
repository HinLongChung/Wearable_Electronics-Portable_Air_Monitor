# Wearable_Electronics-Portable_Air_Monitor
## 2. Introduction
This repository contains the the software stack for a portable, wearable Air Quality Monitoring device. The software is divided into two primary components: the C++ firmware that runs on an Arduino UNO R4 WiFi, and a companion Android application written in Kotlin. The firmware is responsible for interfacing with multiple sensors to capture real-time localized air quality data of six pollutants, namely CO2, NO2, SO2, O3, PM2.5, and PM10, and the local Air Quality Index is calculated based on the UK Government's standard. This data is then transmitted via Bluetooth Low Energy (BLE) to the Android application, which provides personalized health recommendations based on user data, and historical trend charts.

## 3. Contextual Overview
The system architecture follows a distinct edge-to-mobile data flow:
1. **Data Acquisition:** The Arduino UNO R4 requests pollutant concentrations from all sensors via I2C and analog protocols.
2. **AQI Calculation:** The Arduino calculates the local AQI based on the data collected by the sensors.
3. **Local Display:** The microcontroller formats and display the data onto the onboard 16x2 LCD to the user.
4. **Wireless Transmission:** The Arduino acts as a BLE Peripheral, broadcasting the data to the Android Application.
5. **Mobile Processing:** The Android application acts as the BLE Central device, receiving the data stream, mapping the data to a visual user interface, provides personalized health recommendations based on user data which are collected on first startup, and historical trend charts.

<img width="469" height="340" alt="Screenshot 2026-05-01 064456" src="https://github.com/user-attachments/assets/111a2dcf-883e-4687-8a01-f6be9029eaa8" />


## 4. Installation Instructions

### Firmware Dependencies (Arduino IDE)
To compile the embedded code, the following libraries must be installed via the Arduino Library Manager on Arduino IDE:
* `SensirionI2CSen5x.h` (For Sensirion SEN55 Particulate Matter sensor)
* `SensirionI2cScd4x.h` (For Sensirion SCD41 Carbon Dioxide sensor)
* `SensirionCore.h` (Core for Sensirion sensors)
* `Wire.h` (For Analog inputs)
* `DFRobot_RGBLCD1602.h` (For DFRobot 16x2 LCD)
* `DFRobot_MICS.h` (For DFRobot SEN0441 Nitrogen Dioxide sensor)
* `DFRobot_OzoneSensor.h` (For DFRobot SEN0321 Ozone sensor)
* `ArduinoBLE.h` (For BLE communications)


## 5. How to Run the Software

### Flashing the Microcontroller
1. Navigate to the `/firmware` directory, download and open `Device_firmware.ino` in the Arduino IDE.
2. Connect the Arduino UNO R4 via USB-C.
3. Select the correct COM port and board (Arduino UNO R4 WiFi/Minima).
4. Click **Upload**..

### Installing the Android Application
1. Navigate the `/apk` directory
2. Download the `AQI_Monitor.apk` file and install it on your phone

## 6. Technical Details
* **BLE Architecture:** The system uses a custom BLE Service UUID. Each pollutant is assigned a specific Characteristic UUID to prevent packet collision during data transmission.
* **AQI Algorithm:** The software computes the Air Quality Index based on the official UK Daily Air Quality Index (DAQI) banding system, utilizing a if statements to map raw pollutant concentrations (µg/m³ and ppm) to a 1-10 index scale.

## 7. Known Issues and Future Improvements
* **Android Location Quirks:** On older Android versions (API < 31), BLE scanning requires global Location Services to be enabled on the phone. This is a OS-level restriction, not an app bug.
* **Sensor Baseline Calibration:** The device would require a 3 minutes startup time for calibrating the sensors, and the readings are accurate after 20 minutes of startup
