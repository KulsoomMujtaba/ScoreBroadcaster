# ScoreBroadcaster

A real-time cricket scoring application for Android, built with Kotlin and Jetpack Compose.

---

## Project Architecture

### Kotlin
The entire codebase is written in Kotlin. Kotlin's data classes, sealed classes, and extension functions are used throughout to keep the code concise and expressive.

### Jetpack Compose
The UI layer is built exclusively with Jetpack Compose. `ScoringScreen.kt` contains all composable functions and observes state changes via `StateFlow`, recomposing automatically when match state updates.

### MVVM
The project follows the Model-View-ViewModel pattern:
- **Model** – `MatchState` (immutable data class) and `ScoreEvent` (sealed class of possible scoring actions).
- **ViewModel** – `MatchViewModel` holds the list of events and exposes a derived `StateFlow<MatchState>` to the UI. It also exposes a single `dispatch(event: ScoreEvent)` entry point so the UI never mutates state directly.
- **View** – `ScoringScreen` (Compose) reads from the ViewModel's state flow and calls `dispatch` when the user taps a scoring button.

### Event-based Scoring Engine (ScoreEvent Reducer Pattern)
Scoring is modelled as an append-only event log:
1. Every user action (run, wicket, wide, no-ball, bye, leg-bye) is represented as a `ScoreEvent` subclass.
2. `ScoreReducer.kt` contains a pure `reduce(state, event)` function that returns a new `MatchState` without mutating anything.
3. `MatchViewModel` maintains the full event history and recomputes the current state by folding all events through the reducer. This makes **undo** trivial — simply drop the last event and re-reduce.
4. Because state is always derived from the event log, replaying, debugging, or persisting a match is straightforward.

---

## Development Log

### 2026-03-04

**Feature:** Initial project setup and core cricket scoring engine

**Files created/modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ScoreEvent.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/MatchState.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/ScoreReducer.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/MatchViewModel.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/ScoringScreen.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/MainActivity.kt` | Created |
| `README.md` | Created |

**Explanation:**
Set up the full application from scratch. `ScoreEvent` is a sealed class covering every legal cricket delivery outcome (Run, Wicket, Wide, NoBall, Bye, LegBye). `MatchState` is an immutable data class holding runs, wickets, overs, ball count, and the last six deliveries for the over summary. `ScoreReducer` is a stateless pure function that computes the next `MatchState` from the current state and a single event, including over progression and extras handling. `MatchViewModel` stores the append-only event list and exposes a `StateFlow<MatchState>` derived by folding all events through the reducer; it also provides `dispatch` and `undo` methods. `ScoringScreen` renders the scoreboard and scoring buttons using Jetpack Compose and collects state from the ViewModel. `MainActivity` bootstraps Compose and injects the ViewModel.

---

### 2026-03-04 (2)

**Feature:** `ScoreboardOverlay` – live-video broadcast bar composable

**Files created/modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoreboardOverlay.kt` | Created |
| `README.md` | Updated |

**Explanation:**
Added `ScoreboardOverlay`, a Jetpack Compose composable designed to sit on top of a live video stream. It renders a bottom broadcast bar with a semi-transparent black background (~80% opacity) for high contrast over any background. The main row shows the match title ("TeamA vs TeamB") on the left and the score ("123/4") plus overs ("14.2 ov") on the right. When `MatchState.lastBalls` is non-empty, an optional second row above the main bar lists the recent delivery outcomes, colour-coding wickets (red), boundaries (blue), and regular deliveries (light grey). Two `@Preview` composables are included so the layout can be inspected directly in Android Studio.

---

### 2026-03-04 (3)

**Feature:** `CameraPreviewScreen` – live camera preview with scoreboard overlay

**Files created/modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/CameraPreviewScreen.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/MainActivity.kt` | Updated |
| `app/src/main/AndroidManifest.xml` | Updated |
| `app/build.gradle.kts` | Updated |
| `gradle/libs.versions.toml` | Updated |
| `README.md` | Updated |

**Explanation:**
Added `CameraPreviewScreen`, a Jetpack Compose screen that shows a live camera feed using CameraX with `ScoreboardOverlay` composable layered above the preview. The camera preview uses `PreviewView` embedded via `AndroidView` inside the Compose hierarchy. Camera lifecycle is managed through a `DisposableEffect` keyed on the `LifecycleOwner`; the camera use-case is bound when the effect is started and unbound in the `onDispose` callback, so the camera automatically starts and stops with the screen lifecycle. The `ScoreboardOverlay` collects `MatchViewModel.state` as a Compose state and recomposes automatically on every `MatchState` change. Runtime CAMERA permission is requested via `rememberLauncherForActivityResult` if not yet granted, with a fallback message shown while permission is absent. CameraX dependencies (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view` 1.3.4) were added to the version catalogue and module build file. `MainActivity` was updated to instantiate a shared `MatchViewModel` and pass it to `CameraPreviewScreen`.

---

### 2026-03-04 (4)

**Feature:** Live score overlay updates and scoring controls panel in `CameraPreviewScreen`

**Files created/modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/CameraPreviewScreen.kt` | Updated |
| `gradle/libs.versions.toml` | Updated |
| `app/build.gradle.kts` | Updated |
| `README.md` | Updated |

**Explanation:**
`CameraPreviewScreen` now collects `MatchViewModel.state` using `collectAsStateWithLifecycle()` (from `androidx.lifecycle:lifecycle-runtime-compose`) instead of `collectAsState()`, ensuring the overlay pauses collection when the screen is not in the foreground and resumes automatically on return. A new `ScoringControlsPanel` composable is overlaid at the top of the camera preview, providing compact buttons for all common delivery outcomes: **0, 1, 2, 3, 4, 6** (runs), **W** (wicket), **Wd+1** (wide, +1 extra), **NB+1** (no-ball, +1 extra), and **Undo**. Each button is wired directly to `MatchViewModel.addEvent()` or `MatchViewModel.undo()` so the `ScoreboardOverlay` at the bottom reacts instantly without leaving the camera view. The `MatchViewModel` instance continues to be created once in `MainActivity` and passed into `CameraPreviewScreen` as a parameter, ensuring a single shared VM across all screens. `lifecycle-runtime-compose` was added to `libs.versions.toml` and `app/build.gradle.kts`.

---

### 2026-03-04 (5)

**Feature:** `HomeScreen` – app entry point with Jetpack Compose Navigation

**Files created/modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/HomeScreen.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchViewModel.kt` | Updated |
| `app/src/main/java/com/example/scorebroadcaster/MainActivity.kt` | Updated |
| `gradle/libs.versions.toml` | Updated |
| `app/build.gradle.kts` | Updated |
| `README.md` | Updated |

**Explanation:**
Added a `HomeScreen` composable as the new app entry point, presenting two large primary action buttons: **"Live Scoring Preview"** (navigates to `CameraPreviewScreen` with camera + scoreboard overlay + in-camera scoring controls) and **"Scoring Only"** (navigates to `ScoringScreen` without the camera). A smaller **"Reset Match"** `TextButton` at the bottom clears the event log and resets `MatchState` by calling the new `MatchViewModel.resetMatch()` method. Jetpack Compose Navigation (`navigation-compose 2.7.7`) was added and `MainActivity` was updated to host a `NavHost` with three routes: `home`, `live_preview`, and `scoring_only`. A single `MatchViewModel` instance is created in `MainActivity` (scoped to the Activity) and passed to each destination, ensuring score state is preserved when navigating between screens. Back navigation returns to `HomeScreen` without losing match state.

---

### 2026-03-04 (6)

**Feature:** `StreamSetupScreen` – RTMP stream configuration UI and `LiveStreamViewModel`

**Files created/modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/data/StreamConfig.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/data/StreamingStatus.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/LiveStreamViewModel.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/ui/StreamSetupScreen.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/ui/HomeScreen.kt` | Updated |
| `app/src/main/java/com/example/scorebroadcaster/MainActivity.kt` | Updated |
| `gradle/libs.versions.toml` | Updated |
| `app/build.gradle.kts` | Updated |
| `README.md` | Updated |

**Explanation:**
Added `StreamSetupScreen`, a new Jetpack Compose screen reachable from `HomeScreen` via the `stream_setup` navigation route. The screen provides text fields for **Server URL** and **Stream Key** (the stream key is masked using `PasswordVisualTransformation`), a read-only **Resolution** field fixed to `720p`, and a dropdown **Bitrate** selector with options 2500 / 3500 / 4500 kbps. Two buttons—**Start Streaming** and **Stop Streaming**—trigger `LiveStreamViewModel.startStreaming(config)` and `LiveStreamViewModel.stopStreaming()` respectively. A colour-coded status area at the bottom of the screen reflects the current `StreamingStatus`: Idle (surface), Connecting (tertiary), Streaming (primary), Reconnecting (secondary), or Error (error). `LiveStreamViewModel` is an `AndroidViewModel` that exposes a `StateFlow<StreamingStatus>` and persists the last-used server URL and stream key in `EncryptedSharedPreferences` (AES256-GCM / AES256-SIV via `androidx.security:security-crypto 1.0.0`). Actual RTMP transmission is intentionally not implemented; the ViewModel contract is in place as a stub. `HomeScreen` gained a new **"Stream Setup"** button and `MainActivity` wires the `stream_setup` composable destination using a single `LiveStreamViewModel` scoped to the Activity.
