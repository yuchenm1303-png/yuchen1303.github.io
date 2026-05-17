# AI Assistant Native Compose Migration

This document tracks the native Android direction for the AI assistant. The old
Capacitor/WebView build remains intact while the Compose app grows to feature
parity.

## Goal

Move the app shell, liquid glass surface, animation, navigation, and high-touch
interactions from WebView/CSS into native Kotlin + Jetpack Compose.

Keep the useful service layer ideas from the web app:

- Cloudflare Worker AI parsing endpoint.
- Ledger/chat data model.
- Sync/account flow.
- Native actions such as opening apps, alarms, and navigation.

## Current Native Preview

The top-level `app` module is now a buildable Compose preview app.

- Entry: `app/src/main/java/com/yuchen/ailedger/MainActivity.kt`
- View state: `app/src/main/java/com/yuchen/ailedger/AssistantViewModel.kt`
- Models: `app/src/main/java/com/yuchen/ailedger/model`
- Preview data: `app/src/main/java/com/yuchen/ailedger/data`
- Service placeholders: `app/src/main/java/com/yuchen/ailedger/service`
- Compose UI: `app/src/main/java/com/yuchen/ailedger/ui`
- Build command: `npm.cmd run android:build:compose`
- Direct Gradle command: `android/gradlew.bat -p . :app:assembleDebug`
- CI workflow: `.github/workflows/build-compose-android-apk.yml`

The existing Capacitor app still builds from `ai-ledger-android/android`.

## Migration Phases

1. Native shell
   - Compose activity.
   - Assistant / Tools / Settings tabs.
   - Starry weather background.
   - Liquid glass surface primitives.
   - Render quality settings.

2. State and models
   - `LedgerRecord`
   - `ChatMessage`
   - `AssistantCommand`
   - `MobileAction`
   - `UserPrefs`

3. Persistence
   - DataStore for settings.
   - Room or JSON store for ledger/chat history.
   - Optional one-time migration from Web localStorage export.

4. AI service
   - Kotlin HTTP client for the existing Cloudflare Worker.
   - Native command parser result types.
   - Tool/action capability descriptions passed to the worker.

5. Native actions
   - Android intents for opening apps.
   - Alarm intents or AlarmManager flow.
   - Navigation intents.
   - Permission prompts only where required.

6. Feature parity
   - Chat and action cards.
   - Ledger center.
   - Statistics.
   - Reminders.
   - Sync/login.

7. Replacement
   - Keep both APK workflows until the Compose app is stable.
   - Switch release distribution only after parity and real-device performance checks.

## Rendering Principles

- Prefer one shared starry background instead of many expensive per-card effects.
- Use thin translucent glass skins with low alpha and crisp text.
- Use small press-scale feedback instead of large layout-changing animations.
- Keep high quality mode as an experimental profile.
- Default to balanced mode for everyday use.
