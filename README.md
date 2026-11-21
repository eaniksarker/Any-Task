# Any Task

**Any Task** is a modern Android productivity application built with **Kotlin**, **Jetpack Compose**, Material Design 3, and a **Liquid Glass** design system. It replicates the clean workflow of Google Tasks while adding priority color-coding, translucent home-screen widgets, exact deadline push notifications with tray actions, and Google account cloud sync.

---

## Key Features

- **Core Task Workflow**: Create, edit, complete/uncomplete, delete, search, and reorder tasks via drag-and-drop.
- **Nested Subtasks & Lists**: Add inline subtasks per task, manage multiple task lists (create, rename, delete, switch), and expand/collapse the **Completed** section.
- **Liquid Glass UI Theme**: Frosted translucent cards, directional specular rim borders, softly drifting gradient mesh backdrop, spring physics, and both **Light Glass** & **Dark Glass** modes.
- **Priority Color System**:
  - **High Priority**: `#EF4444` (Red)
  - **Medium Priority**: `#EAB308` (Amber/Yellow)
  - **Low Priority**: `#22C55E` (Green)
- **Home-Screen Widgets**: Resizable Liquid Glass `AppWidgetProvider` + `RemoteViewsService` displaying priority indicators, due times, direct checkbox completion, and quick-add `+` action, plus per-widget list and Light/Dark customization.
- **Deadline Push Notifications**: Exact `AlarmManager` scheduling with early reminder offsets (At deadline, 15m, 1h, 1d before), `POST_NOTIFICATIONS` & `SCHEDULE_EXACT_ALARM` permission flows, boot rescheduling, and **Mark Complete**, **Snooze 15m**, & **Snooze 1h** tray actions.
- **Offline-First & Google Cloud Sync**:
  - Local persistence via **Room** (`AnyTaskDatabase`) and **DataStore** (`PreferencesManager`).
  - Bidirectional cloud sync with **Last-Write-Wins** (`updatedAtMillis`) conflict resolution.
  - **Google**: Firebase Auth (`CredentialManager` + `GetSignInWithGoogleOption`) + Cloud Firestore real-time sync.

---

## Project Architecture

```text
app/src/main/java/com/example/
├── AnyTaskApplication.kt             # Singleton graph & notification channel init
├── MainActivity.kt                   # Edge-to-edge root, Navigation Compose, deep-link routing
├── domain/model/Models.kt            # Pure domain models (TaskItem, TaskList, Subtask, Priority)
├── data/
│   ├── AnyTaskRepository.kt          # Offline-first Room + Firestore + Alarm + Widget coordinator
│   ├── local/                        # Room Entities, DAOs, AnyTaskDatabase, DataStore Preferences
│   └── remote/                       # FirestoreTaskRepository, SyncEngine
├── notifications/                    # DeadlineAlarmScheduler, Notification & Boot BroadcastReceivers
├── widget/                           # AnyTaskWidgetProvider, RemoteViewsService, ConfigureActivity
└── ui/
    ├── auth/GoogleAuthHelper.kt      # Jetpack Credential Manager & NonCancellable sign-out
    ├── components/                   # LiquidGlassBackground, LiquidGlassSurface, GoogleSyncLogo, PriorityBadge
    ├── screens/                      # AuthScreen, TaskBoardScreen, TaskEditorSheet, AccountSyncScreen
    ├── theme/                        # Color, Typography (Plus Jakarta Sans, Inter, JetBrains Mono), Theme
    └── viewmodel/                    # TaskBoardViewModel, AuthSyncViewModel
```

---

## Google Sign-In & Cloud Firestore Setup

Google Sign-In and Cloud Firestore are provisioned automatically via `app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml`.
To deploy Firestore security rules manually via Firebase CLI:
```bash
firebase deploy --only firestore:rules
```

---

## Building the APK & Running Tests

### Local Gradle CLI
```bash
# Run unit & Robolectric tests
gradle :app:testDebugUnitTest

# Build Debug APK (output: app/build/outputs/apk/debug/app-debug.apk)
gradle :app:assembleDebug

# Build Release APK (requires KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD env vars)
gradle :app:assembleRelease
```

### Containerized Build with Docker
```bash
# Build the reproducible Android SDK 36 container
docker build -t anytask-android-builder .

# Run unit tests inside the container
docker run --rm -v "$(pwd)":/workspace anytask-android-builder gradle :app:testDebugUnitTest

# Build the Debug APK inside the container
docker run --rm -v "$(pwd)":/workspace anytask-android-builder gradle :app:assembleDebug
```

### Firestore Security Rules Tests
```bash
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```

---

## CI/CD Pipeline

Automated testing, linting, and APK packaging are configured in `.github/workflows/android-ci.yml`:
- Runs `gradle :app:testDebugUnitTest` and `gradle :app:lintDebug` on every push and pull request.
- Builds `app-debug.apk` (and signed release APK/AAB when signing secrets are present) and uploads build artifacts.
