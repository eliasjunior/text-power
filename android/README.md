# Text Power (Android)

Android app to:
- scan or pick document images,
- extract text with ML Kit OCR,
- read text aloud with Android Text-to-Speech.

## Current features
- ML Kit Document Scanner integration (single or multi-page).
- OCR preprocessing options (grayscale/threshold) and text cleaning levels.
- Reader view with larger typography and scroll.
- Spoken-text highlighting while TTS plays.
- Pause/resume playback from last position (including after app reopen, when text matches).
- Voice/rate/pitch controls.
- Session history (save/open/delete/clear).
- Copy/share extracted text.

## Requirements
- Android Studio (free) or command line + Android SDK.
- JDK 17.
- Android device or emulator.

## Build and install
From this folder:

```bash
cd ~/project-location/text-power/android
./gradlew assembleDebug
./gradlew installDebug
```

Install to a specific device:

```bash
./gradlew installDebug -Pandroid.injected.android.serial=<DEVICE_SERIAL>
```

## Run UI tests
Compile instrumentation tests:

```bash
./gradlew assembleDebugAndroidTest
```

Run connected tests:

```bash
./gradlew connectedDebugAndroidTest
```

## Connect phone (ADB)
1. Enable Developer options and USB debugging on phone.
2. Connect USB and accept RSA prompt.
3. Verify:

```bash
adb devices -l
```

## Troubleshooting
- `No connected devices!`
  - Check `adb devices -l`, cable, USB debugging, RSA confirmation.
- `gradlew: command not found`
  - Run from `android/` and use `./gradlew ...`.
- App installed but not visible
  - Search launcher for **Text Power**.
- OCR quality poor
  - Prefer scanner flow, good light, straight page, and preprocessing/cleaning options.
