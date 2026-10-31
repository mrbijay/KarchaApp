# KarchaManager - Android Native Application (APK) Build Guide

## 1. Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer / Ladybug
- JDK 17 or higher
- Android SDK 35

## 2. Opening the Project
1. Extract this .ZIP file into your workspace folder.
2. Open Android Studio -> Select **Open an existing Android Studio Project**.
3. Select the extracted folder containing `settings.gradle.kts`.
4. Allow Gradle to sync dependencies automatically.

## 3. Building Debug APK
Run in terminal inside project directory:
```bash
./gradlew assembleDebug
```
The APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

## 4. Building Signed Release APK
In Android Studio:
1. Go to menu: **Build** > **Generate Signed Bundle / APK**.
2. Select **APK** and click Next.
3. Choose your Keystore path (or click "Create New Keystore").
4. Select build variant **release**.
5. Check **V1 (Jar Signature)** and **V2 (Full APK Signature)**.
6. Click Finish.

The Signed APK will be saved at:
`app/release/app-release.apk`

## 5. Installing on Mobile Device
Connect phone via USB with USB Debugging enabled:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Or simply transfer the .apk file via WhatsApp, Google Drive, or Telegram to your Android phone and tap to Install!
