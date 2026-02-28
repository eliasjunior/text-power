# Text Power - Agent Context

## What this app is
- `Text Power` is an Android OCR + reading app.
- It scans or picks document images, extracts text with ML Kit, and presents the text in a reader-style view.
- It supports multi-page scanning, text cleanup options, session history, and Text-to-Speech playback with pause/resume and spoken-text highlighting.

## Project structure
- Android app root: `android/`
- Main UI flow: `android/app/src/main/java/com/eliasjunior/textpower/ui/ocr/`
- OCR logic: `android/app/src/main/java/com/eliasjunior/textpower/ocr/`
- TTS logic: `android/app/src/main/java/com/eliasjunior/textpower/tts/`
- History/session storage: `android/app/src/main/java/com/eliasjunior/textpower/history/`

## Autonomous command policy (strict)
- The agent may run the following commands without asking for permission:
  - `./gradlew assembleDebug`
  - `./gradlew connectedDebugAndroidTest`

- Any other command must be confirmed with the user first.
