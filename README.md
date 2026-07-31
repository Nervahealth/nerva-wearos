# HUGR Wear OS Companion App

**Tier 1 Sensor Fusion for the Samsung Galaxy Watch Ultra**

This is the Wear OS companion app for HUGR, enabling continuous EDA, HRV, and accelerometer monitoring on the Samsung Galaxy Watch Ultra (SM-L705). The app streams sensor data to the phone app via BLE, accelerating the **Symbiosis** phase and enabling pre-transition haptic interventions.

## Overview

The HUGR Wear OS app is a native Android application built in Kotlin that:

- **Reads continuous EDA** (electrodermal activity) at 1Hz via Samsung Health Sensor SDK
- **Captures IBI** (inter-beat intervals) for heart rate variability analysis
- **Monitors accelerometer** data at 25Hz for motion and state detection
- **Streams data via BLE** to the phone app for real-time fusion and prediction
- **Provides haptic feedback** as a "tap on the shoulder" before conscious awareness

## Architecture

### Core Components

| Component | Purpose | Status |
|-----------|---------|--------|
| **MainActivity.kt** | UI and service lifecycle management | ✅ Complete |
| **HealthSensorService.kt** | Samsung Health Sensor SDK integration | ✅ Complete |
| **BleGattService.kt** | BLE GATT server for data streaming | ✅ Complete |
| **AndroidManifest.xml** | Permissions and service declarations | ✅ Complete |

### Data Flow

```
Galaxy Watch Ultra
    ↓
Samsung Health Sensor SDK (EDA, IBI, Accel)
    ↓
HealthSensorService (reads sensors)
    ↓
BleGattService (BLE GATT server)
    ↓
Honor X5c Plus (phone app via BLE)
    ↓
Symbiosis Engine (fusion + prediction)
    ↓
Haptic Intervention
```

## Technical Specifications

### Target Device
- **Samsung Galaxy Watch Ultra** (SM-L705)
- **Wear OS 4.0+**
- **Minimum SDK:** API 30
- **Target SDK:** API 34

### Sensors
- **EDA_CONTINUOUS:** 1Hz electrodermal activity
- **IBI:** Beat-to-beat heart rate intervals
- **Accelerometer:** 25Hz motion data (X, Y, Z axes)

### Communication
- **BLE GATT Server** with custom UUIDs:
  - Service UUID: `12345678-1234-5678-1234-567812345678`
  - EDA Characteristic: `11111111-1111-1111-1111-111111111111`
  - IBI Characteristic: `22222222-2222-2222-2222-222222222222`
  - Accel Characteristic: `33333333-3333-3333-3333-333333333333`

### Permissions
- `BODY_SENSORS` - Read health sensor data
- `BODY_SENSORS_BACKGROUND` - Background sensor access
- `BLUETOOTH_*` - BLE communication
- `VIBRATE` - Haptic feedback
- `ACCESS_*_LOCATION` - BLE scanning

## Building the App

### Quick Start
```bash
cd /home/ubuntu/hugr-wearos-app
./gradlew assembleDebug
```

The APK will be generated at: `app/build/outputs/apk/debug/app-debug.apk`

### Detailed Instructions
See [BUILD_GUIDE.md](BUILD_GUIDE.md) for:
- Android Studio setup
- Command-line build instructions
- GitHub Actions automated builds
- Installation via wireless debugging

## Installation

### Prerequisites
- Samsung Galaxy Watch Ultra with developer mode enabled
- Honor X5c Plus with wireless debugging enabled
- ADB (Android Debug Bridge) installed

### Install Steps
```bash
# Connect to watch via wireless debugging
adb connect 192.168.4.52:43059

# Install the APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Launch the app
adb shell am start -n com.hugr.wearos/.MainActivity

# View logs
adb logcat -s HUGR-WearOS
```

## Usage

### On the Watch
1. Launch the HUGR app
2. You'll see the status: "HUGR WearOS App - Initializing sensors..."
3. Sensors will begin reading automatically
4. Log output shows EDA, IBI, and accelerometer data in real-time

### On the Phone
The phone app (via BLE) will receive:
- EDA values (in µS, microsiemens)
- IBI intervals (in milliseconds)
- Accelerometer vectors (X, Y, Z in m/s²)

### Haptic Feedback
When the Symbiosis engine predicts a state transition:
- Watch vibrates with a personalized haptic pattern
- Pattern intensity reflects confidence level
- Haptic serves as early warning before conscious awareness

## Development

### Project Structure
```
hugr-wearos-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/hugr/wearos/
│   │   │   ├── MainActivity.kt
│   │   │   ├── HealthSensorService.kt
│   │   │   └── BleGattService.kt
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml
│   │   │   └── values/strings.xml
│   │   └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── gradle/wrapper/
├── build.gradle
├── settings.gradle
└── README.md
```

### Adding Features

#### New Sensor
1. Add listener in `HealthSensorService.kt`
2. Define characteristic UUID in `BleGattService.kt`
3. Update `AndroidManifest.xml` permissions
4. Add UI logging in `MainActivity.kt`

#### New Haptic Pattern
1. Define pattern in `BleGattService.kt`
2. Trigger via `VibrationEffect` API
3. Test on watch with different amplitudes

## Troubleshooting

### App Crashes on Launch
```bash
adb logcat -s HUGR-WearOS
```
Check for missing permissions or Samsung Health SDK issues.

### Sensors Not Reading
- Verify Samsung Health app is installed on watch
- Check `BODY_SENSORS` permission is granted
- Confirm sensors are available: `adb shell dumpsys sensorservice`

### BLE Not Connecting
- Ensure Bluetooth is enabled on both devices
- Check BLE permissions are granted
- Verify phone app is scanning for HUGR service UUID

### Wireless Debugging Issues
- Confirm watch and phone are on same network
- Reset wireless debugging: Settings → Developer options → Wireless debugging
- Reconnect with: `adb connect <IP>:<PORT>`

## References

- [Samsung Health Sensor SDK Documentation](https://developer.samsung.com/health/android/health-data-service.html)
- [Android Wear OS Documentation](https://developer.android.com/training/wearables)
- [BLE GATT Server Guide](https://developer.android.com/guide/topics/connectivity/bluetooth/gatt-server)
- [Haptic Feedback API](https://developer.android.com/guide/topics/haptics)

## Next Steps

### Phase 2: Phone App Integration
- Implement BLE client in phone app
- Connect to watch GATT server
- Stream sensor data to Symbiosis engine

### Phase 3: Symbiosis Acceleration
- Multi-sensor fusion (EDA + HRV + Accel)
- Baseline calibration (2-4 weeks → days with Tier 1)
- State prediction model training

### Phase 4: Clinical Pilot
- Deploy to pilot sites
- Collect real-world data
- Validate haptic intervention efficacy

## License

Proprietary - Nerva/HUGR Project

## Contact

For questions or support:
- Nerva Team: [contact information]
- Clinical Integration: [contact information]
