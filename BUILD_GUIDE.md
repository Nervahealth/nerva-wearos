# HUGR Wear OS App - Build Guide

This guide provides instructions for building the HUGR Wear OS companion app for the Samsung Galaxy Watch Ultra.

## Prerequisites

- **Java Development Kit (JDK) 17** or later
- **Android SDK** (API level 34, minimum SDK 30 for Wear OS)
- **Gradle 8.1** or later (included via wrapper)
- **Samsung Galaxy Watch Ultra** (SM-L705) or compatible Wear OS device

## Option 1: Build with Android Studio (Recommended for Development)

### Step 1: Install Android Studio
1. Download from https://developer.android.com/studio
2. Install and launch Android Studio
3. Complete the initial setup wizard

### Step 2: Install Required SDKs
1. Open **Settings** → **Appearance & Behavior** → **System Settings** → **Android SDK**
2. Install:
   - Android SDK Platform 34
   - Android SDK Build-Tools 34.0.0
   - Wear OS System Image (optional, for emulation)

### Step 3: Open the Project
1. File → Open
2. Navigate to `/home/ubuntu/hugr-wearos-app`
3. Click **Open**
4. Wait for Gradle sync to complete

### Step 4: Build the APK
1. Build → Build Bundle(s)/APK(s) → Build APK(s)
2. Wait for the build to complete
3. Find the APK at: `app/build/outputs/apk/debug/app-debug.apk`

## Option 2: Build from Command Line

### Step 1: Install Android SDK
```bash
# On macOS with Homebrew
brew install android-sdk

# On Linux (Ubuntu/Debian)
sudo apt-get install android-sdk

# Or download from https://developer.android.com/studio/releases/platforms
```

### Step 2: Set Environment Variables
```bash
export ANDROID_SDK_ROOT=/path/to/android-sdk
export ANDROID_HOME=$ANDROID_SDK_ROOT
export PATH=$PATH:$ANDROID_SDK_ROOT/tools:$ANDROID_SDK_ROOT/platform-tools
```

### Step 3: Build the APK
```bash
cd /home/ubuntu/hugr-wearos-app
./gradlew assembleDebug
```

The APK will be generated at: `app/build/outputs/apk/debug/app-debug.apk`

## Option 3: Build with GitHub Actions (Automated)

### Step 1: Push to GitHub
```bash
cd /home/ubuntu/hugr-wearos-app
git init
git add .
git commit -m "Initial Wear OS app"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/hugr-wearos-app.git
git push -u origin main
```

### Step 2: Download APK
1. Go to your GitHub repository
2. Click **Actions**
3. Click the latest workflow run
4. Under **Artifacts**, download `hugr-wearos-debug`
5. Extract the APK

## Installing on Galaxy Watch Ultra

### Prerequisites
- Honor X5c Plus with wireless debugging enabled
- IP address and port from wireless debugging (e.g., 192.168.4.52:43059)
- APK file ready

### Step 1: Enable Wireless Debugging on Watch
1. On the watch, go to **Settings** → **About**
2. Tap **Build number** 7 times to enable Developer Mode
3. Go to **Settings** → **Developer options**
4. Enable **Wireless debugging**
5. Note the IP address and port

### Step 2: Install APK via ADB
```bash
# Connect to the watch
adb connect 192.168.4.52:43059

# Install the APK
adb install app/build/outputs/apk/debug/app-debug.apk

# Verify installation
adb shell pm list packages | grep hugr
```

### Step 3: Launch the App
```bash
# Start the app
adb shell am start -n com.hugr.wearos/.MainActivity

# View logs
adb logcat -s HUGR-WearOS
```

## Troubleshooting

### Build Fails with "SDK not found"
- Ensure `ANDROID_SDK_ROOT` environment variable is set
- Run `sdkmanager --list` to verify SDK installation
- Install missing platforms: `sdkmanager "platforms;android-34"`

### APK Installation Fails
- Verify device is connected: `adb devices`
- Check device has sufficient storage: `adb shell df /data`
- Uninstall previous version: `adb uninstall com.hugr.wearos`

### App Crashes on Launch
- Check logs: `adb logcat -s HUGR-WearOS`
- Verify permissions are granted on the watch
- Ensure Samsung Health Sensor SDK is available on the device

## Project Structure

```
hugr-wearos-app/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/hugr/wearos/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── HealthSensorService.kt
│   │   │   │   └── BleGattService.kt
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   └── activity_main.xml
│   │   │   │   └── values/
│   │   │   │       └── strings.xml
│   │   │   └── AndroidManifest.xml
│   │   └── test/
│   ├── build.gradle
│   └── proguard-rules.pro
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── build.gradle
├── settings.gradle
└── gradlew
```

## Key Features

- **EDA Continuous Monitoring** (1Hz) via Samsung Health Sensor SDK
- **Heart Rate Variability (IBI)** beat-to-beat intervals
- **Accelerometer Data** (25Hz) for motion detection
- **BLE GATT Server** for streaming data to phone app
- **Haptic Feedback** integration for wrist notifications
- **Real-time Logging** on watch display

## Next Steps

1. Build the APK using one of the methods above
2. Install on your Galaxy Watch Ultra
3. Connect to the Honor X5c Plus phone app via BLE
4. Monitor sensor data in real-time
5. Test haptic feedback patterns

## Support

For issues or questions:
- Check the logs: `adb logcat -s HUGR-WearOS`
- Review the AndroidManifest.xml for permission issues
- Verify Samsung Health Sensor SDK is installed on the device
- Contact the Nerva team for clinical integration support
