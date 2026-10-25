# Implementation Plan: Background Voice Assistant & Protected Key Configuration

## Objective
Configure secure API key management via git-ignored local environment configuration, and add system-wide background voice assistant capabilities so MYRA can be triggered without opening the main app UI, using Android's native Voice Interaction Service and Accessibility Service.

---

## Proposed Changes

### 1. API Key Security & Secrets Gradle Configuration
- **File**: `/.gitignore`, `/.env`, `/.env.example`, `app/build.gradle.kts`
- Ensure `.env` is strictly tracked in `.gitignore` so sensitive keys are never pushed to GitHub or public repositories.
- Configure the Secrets Gradle plugin properties in `app/build.gradle.kts` to load `GEMINI_API_KEY` from the git-ignored local `.env` and map it into `BuildConfig.GEMINI_API_KEY`.
- Maintain environment compatibility without breaking cloud builder requirements.

---

### 2. Settings Dialog UI: Enable Assistant & Accessibility
- **File**: `app/src/main/java/com/example/ui/components/SettingsDialog.kt`
- Add an **"Enable Background Assistant"** action card:
  - Button to open Android System Assistant Settings (`android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS`) to set MYRA as the default device digital assistant.
- Add an **"Enable Accessibility Service"** toggle card:
  - Button to open Android Accessibility Settings (`android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS`) to enable MYRA's background accessibility engine.
- Display clear status indicators showing whether permissions are currently granted.

---

### 3. Background Voice Interaction Service (System Assistant)
- **Files**:
  - `app/src/main/java/com/example/service/MyraVoiceInteractionService.kt`
  - `app/src/main/java/com/example/service/MyraVoiceSessionService.kt`
  - `app/src/main/java/com/example/service/MyraVoiceSession.kt`
  - `app/src/main/res/xml/voice_interaction_service.xml`
- Implement Android's official `VoiceInteractionService` and `VoiceInteractionSessionService`:
  - When triggered (via power button hold, gesture, or system assistant trigger), `MyraVoiceSession` launches silently in the background without opening `MainActivity`.
  - The Android system automatically displays the native green microphone privacy dot in the status bar while recording audio.
  - User speaks their command (e.g., *"WhatsApp kholo"*, *"Call Mom"*, *"What is the weather?"*).
  - The session processes speech using `SpeechRecognizer` and Gemini AI (`gemini-3.8-flash`), executes the device action through `DeviceActionManager`, and speaks back the answer with audible audio (`TtsFallback`).

---

### 4. Background Accessibility Service
- **Files**:
  - `app/src/main/java/com/example/service/MyraAccessibilityService.kt`
  - `app/src/main/res/xml/accessibility_service_config.xml`
- Implements `AccessibilityService` allowing MYRA to execute actions across the device even when the main app is closed.

---

### 5. Manifest & Permissions
- **File**: `app/src/main/AndroidManifest.xml`
- Declare `<service>` for `MyraVoiceInteractionService` with `android.permission.BIND_VOICE_INTERACTION`.
- Declare `<service>` for `MyraAccessibilityService` with `android.permission.BIND_ACCESSIBILITY_SERVICE`.
- Ensure foreground service type permissions and audio focus management are properly specified.

---

## Verification Plan
1. **Compilation Check**: Run `compile_applet` to verify zero build errors.
2. **Unit Tests**: Run `gradle :app:testDebugUnitTest` to ensure existing Robolectric tests pass.
3. **Service Validation**: Verify Manifest declarations and XML configurations for Voice Interaction and Accessibility services.
