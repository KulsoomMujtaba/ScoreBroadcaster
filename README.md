# Scored

A mobile-first cricket scoring app for Android, built with Kotlin and Jetpack Compose.

---

## Product Vision

**Scored** is the fastest way to score a cricket match on your phone.

The core promise is simple: open the app, start a match, score every ball, and share it live. The primary flow is live ball-by-ball scoring. Facebook Live streaming is a secondary, opt-in feature for broadcasters.

### MVP Direction

| Priority | Feature |
|----------|---------|
| P0 | Ball-by-ball scoring with over summary |
| P0 | Match state: runs, wickets, overs, last-ball history |
| P1 | Camera preview with live scoreboard overlay |
| P1 | Facebook Live RTMP streaming with burned-in scoreboard |
| P2 | Create Match (teams, overs, toss) |
| P2 | My Matches (saved match history) |
| P3 | Backend sync (Supabase – future phase) |

---

## Current Implemented Features

| Feature | Status | Screen |
|---------|--------|--------|
| Ball-by-ball scoring engine | ✅ Done | `ScoringScreen` |
| Undo last ball | ✅ Done | `ScoringScreen` / `CameraPreviewScreen` |
| Live camera preview + scoreboard overlay | ✅ Done | `CameraPreviewScreen` |
| RTMPS streaming to Facebook Live | ✅ Done | `StreamPreviewScreen` |
| Scoreboard burned into live stream | ✅ Done | `ScoreboardOverlayRenderer` |
| Stream setup (URL, key, bitrate) | ✅ Done | `StreamSetupScreen` |
| Home screen with primary actions | ✅ Done | `HomeScreen` |
| Create Match (real form) | ✅ Done | `CreateMatchScreen` |
| Player setup | ✅ Done | `PlayerSetupScreen` |
| Pre-match summary + Start Match | ✅ Done | `MatchSummaryScreen` |
| My Matches (local in-memory list) | ✅ Done | `MyMatchesScreen` |
| Domain entities (Team, Player, Match, …) | ✅ Done | `data/entity/` |
| Local in-memory repository | ✅ Done | `repository/MatchRepository` |
| Match session management | ✅ Done | `MatchSessionViewModel` |

---

## Phase 1 Refactor Notes

Phase 1 converts the existing **ScoreBroadcaster** prototype into the **Scored** product foundation without removing or breaking any existing functionality.

### What changed

- App renamed from **ScoreBroadcaster** → **Scored** (app name in `strings.xml`, home screen title).
- `HomeScreen` redesigned around four product-oriented primary actions:
  - **Create Match** – navigates to `CreateMatchScreen` (placeholder)
  - **My Matches** – navigates to `MyMatchesScreen` (placeholder)
  - **Live Scoring** – navigates to `ScoringScreen` (manual ball-by-ball scoring — primary flow)
  - **Go Live** – navigates to `StreamSetupScreen` → `StreamPreviewScreen` (RTMP streaming)
- `CreateMatchScreen` and `MyMatchesScreen` added as clearly-labelled placeholder screens.
- All existing scoring, camera, and streaming screens remain fully intact.
- Navigation routes preserved: `live_preview`, `scoring_only`, `stream_setup`, `stream_preview`; new routes added: `create_match`, `my_matches`.

### What did NOT change

- Scoring engine (`ScoreReducer`, `MatchState`, `ScoreEvent`) — untouched.
- `CameraPreviewScreen`, `ScoringScreen`, `StreamSetupScreen`, `StreamPreviewScreen` — untouched.
- `RtmpLiveStreamer`, `ScoreboardOverlayRenderer` — untouched.
- `MatchViewModel`, `LiveStreamViewModel` — untouched.
- No Supabase integration in this phase.

### Files changed in Phase 1

| File | Action |
|------|--------|
| `app/src/main/res/values/strings.xml` | Updated – `app_name` → `Scored` |
| `app/src/main/java/com/example/scorebroadcaster/ui/HomeScreen.kt` | Updated – renamed title, replaced 3 old buttons with 4 product-oriented buttons |
| `app/src/main/java/com/example/scorebroadcaster/ui/CreateMatchScreen.kt` | Created – placeholder |
| `app/src/main/java/com/example/scorebroadcaster/ui/MyMatchesScreen.kt` | Created – placeholder |
| `app/src/main/java/com/example/scorebroadcaster/MainActivity.kt` | Updated – wired `create_match` and `my_matches` routes |
| `README.md` | Updated – product vision, MVP direction, feature table, phase notes |

---

## Project Architecture

### Kotlin
The entire codebase is written in Kotlin. Kotlin's data classes, sealed classes, and extension functions are used throughout to keep the code concise and expressive.

### Jetpack Compose
The UI layer is built exclusively with Jetpack Compose. Screens observe state changes via `StateFlow`, recomposing automatically when match state updates.

### MVVM
The project follows the Model-View-ViewModel pattern:
- **Model** – `MatchState` (immutable data class) and `ScoreEvent` (sealed class of possible scoring actions).
- **ViewModel** – `MatchViewModel` holds the list of events and exposes a derived `StateFlow<MatchState>` to the UI. It also exposes a single `dispatch(event: ScoreEvent)` entry point so the UI never mutates state directly.
- **View** – Compose screens read from the ViewModel's state flow and call `dispatch` when the user taps a scoring button.

### Package Structure

```
com.example.scorebroadcaster/
├── data/
│   ├── MatchState.kt          # Scoring session state (runs, wickets, overs, …)
│   ├── ScoreEvent.kt          # Sealed class of deliveries (Run, Wicket, Wide, …)
│   ├── StreamConfig.kt
│   ├── StreamingStatus.kt
│   └── entity/                # ← Phase 2: domain entities
│       ├── Player.kt
│       ├── Team.kt
│       ├── Match.kt
│       ├── Innings.kt
│       ├── MatchFormat.kt     # T20, ODI, T10, Tape-ball, Custom
│       ├── MatchStatus.kt     # NOT_STARTED, IN_PROGRESS, INNINGS_BREAK, COMPLETED
│       ├── TossDecision.kt    # BAT / BOWL
│       ├── BattingEntry.kt
│       └── BowlingEntry.kt
├── domain/           # Pure business logic: ScoreReducer
├── repository/       # ← Phase 2: local in-memory repository
│   └── MatchRepository.kt
├── streaming/        # RTMP streaming: RtmpLiveStreamer, ScoreboardOverlayRenderer
├── ui/               # Compose screens and theme
│   ├── theme/        # Material3 theme (Color, Type, Theme)
│   ├── HomeScreen.kt              ← Phase 2: active-match banner
│   ├── CreateMatchScreen.kt       ← Phase 2: real form
│   ├── PlayerSetupScreen.kt       ← Phase 2: new
│   ├── MatchSummaryScreen.kt      ← Phase 2: new
│   ├── MyMatchesScreen.kt         ← Phase 2: real in-memory list
│   ├── CameraPreviewScreen.kt
│   ├── ScoringScreen.kt
│   ├── ScoreboardOverlay.kt
│   ├── StreamSetupScreen.kt
│   └── StreamPreviewScreen.kt
├── viewmodel/
│   ├── MatchViewModel.kt          ← Phase 2: initFromMatch() added
│   ├── MatchSessionViewModel.kt   ← Phase 2: new
│   └── LiveStreamViewModel.kt
└── MainActivity.kt               ← Phase 2: new routes wired
```

### Event-based Scoring Engine (ScoreEvent Reducer Pattern)
Scoring is modelled as an append-only event log:
1. Every user action (run, wicket, wide, no-ball, bye, leg-bye) is represented as a `ScoreEvent` subclass.
2. `ScoreReducer.kt` contains a pure `reduce(state, event)` function that returns a new `MatchState` without mutating anything.
3. `MatchViewModel` maintains the full event history and recomputes the current state by folding all events through the reducer. This makes **undo** trivial — simply drop the last event and re-reduce.
4. Because state is always derived from the event log, replaying, debugging, or persisting a match is straightforward.

---

## Development Log

### 2026-03-06 – Flow Correction: Manual Scoring as Primary Match Entry Point

**Feature:** Correct the post-match-creation navigation so the default flow lands in `ScoringScreen` (manual scoring), not `CameraPreviewScreen`. Camera preview is now a separate, secondary mode.

**Files modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/MainActivity.kt` | `match_summary` and `my_matches` routes navigate to `scoring_only`; `onLiveScoringClick` navigates to `scoring_only` (or `create_match` if no active match); new `onCameraPreviewClick` added navigating to `live_preview` |
| `app/src/main/java/com/example/scorebroadcaster/ui/HomeScreen.kt` | Added `onCameraPreviewClick` parameter and a **"Camera Preview"** `OutlinedButton` as a secondary action |
| `README.md` | Corrected Phase 2 descriptions; added this log entry |

**What changed:**
- "Start Match" on `MatchSummaryScreen` now navigates to `ScoringScreen` (`scoring_only` route), not `CameraPreviewScreen`.
- Selecting a match from "My Matches" now opens `ScoringScreen`, not `CameraPreviewScreen`.
- `HomeScreen` **"Live Scoring"** / **"Resume Scoring"** button opens `ScoringScreen`. If no active match exists, it redirects to "Create Match" so the user can start one.
- A new **"Camera Preview"** button is added to `HomeScreen` as a secondary `OutlinedButton`. It opens `CameraPreviewScreen` when a match is active, or redirects to "Create Match" if none exists.
- The `CameraPreviewScreen` itself is unchanged; it remains accessible and fully functional.
- The Facebook Live / stream flow (`stream_setup` → `stream_preview`) is unchanged.
- The scoring engine (`ScoreReducer`, `MatchState`, `ScoreEvent`, `MatchViewModel`) is unchanged.

**Product rationale:** The primary scorer persona needs a fast, reliable manual scoring UI. The camera preview is an optional broadcast feature that should not be forced on every scorer. Defaulting to `ScoringScreen` keeps the app product-oriented: scoring first, camera second.

---

### 2026-03-06 – Phase 2: Entity Layer and Local-First Match Flow

**Feature:** Domain entities, local repository, match creation flow, player setup, pre-match summary, and My Matches list

**Files created:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/data/entity/Player.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/data/entity/Team.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/data/entity/Match.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/data/entity/Innings.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/data/entity/MatchFormat.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/data/entity/MatchStatus.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/data/entity/TossDecision.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/data/entity/BattingEntry.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/data/entity/BowlingEntry.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/repository/MatchRepository.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchSessionViewModel.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/ui/PlayerSetupScreen.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/ui/MatchSummaryScreen.kt` | Created |

**Files modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/CreateMatchScreen.kt` | Replaced placeholder with real form |
| `app/src/main/java/com/example/scorebroadcaster/ui/MyMatchesScreen.kt` | Replaced placeholder with in-memory match list |
| `app/src/main/java/com/example/scorebroadcaster/ui/HomeScreen.kt` | Added active-match banner and context-aware button labels |
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchViewModel.kt` | Added `initFromMatch()` |
| `app/src/main/java/com/example/scorebroadcaster/MainActivity.kt` | Added `MatchSessionViewModel` + new routes |
| `gradle/libs.versions.toml` | Added `material-icons-core` |
| `app/build.gradle.kts` | Added `material-icons-core` dependency |
| `README.md` | Updated |

**Explanation:**

Phase 2 turns Scored into a usable local-first MVP for real cricket match scoring.

**Entity layer** (`data/entity/`): Nine domain entities were introduced — `Player`, `Team`, `Match`, `Innings`, `MatchFormat`, `MatchStatus`, `TossDecision`, `BattingEntry`, and `BowlingEntry`. These model a real cricket match without coupling to any backend.

**Local repository** (`repository/MatchRepository`): A singleton object that manages the in-memory list of created matches and the currently active match. It is intentionally thin and will be replaced by a backend-backed repository in Phase 3.

**MatchSessionViewModel**: A new ViewModel that sits above `MatchViewModel` and manages the higher-level match lifecycle — creating matches, assembling the player-setup draft (stored as `pendingMatch`), confirming a match (which persists it to the repository and marks it active), and switching between matches in My Matches. It co-exists with the existing `MatchViewModel` which continues to manage ball-by-ball scoring.

**Create Match flow** (three screens):
1. `CreateMatchScreen` — a scrollable form collecting match title (optional), Team A/B names, match format (T20/T10/ODI/Tape-ball/Custom), custom overs, toss winner, and toss decision. Toss-winner chips update reactively as team names are typed. The "Next" button is disabled until both team names are filled and overs are valid.
2. `PlayerSetupScreen` — shows a resizable list of player-name text fields for each team (1–11 players). Players can be added or removed; blank rows are ignored on save. Tapping "Continue" updates the pending match in `MatchSessionViewModel` and navigates to the summary.
3. `MatchSummaryScreen` — a read-only confirmation screen listing format, toss result, batting/bowling order, and player rosters. Tapping "Start Match" calls `MatchSessionViewModel.confirmMatch()`, calls `MatchViewModel.initFromMatch()` (seeds the scoring session with the correct team names), and navigates to `ScoringScreen`, clearing the creation back-stack.

**My Matches screen**: Now shows the real in-memory match list from `MatchSessionViewModel.matches`. Displays each match's title, format, overs, and a status chip (Live / Not Started / Completed). A "● Live" indicator highlights the active match. Tapping an item switches the active match and opens it in `ScoringScreen`. An empty-state with a "Create Match" shortcut is shown when no matches exist.

**HomeScreen**: Shows a compact active-match banner (match title + format + live indicator) when a session is in progress. The "Live Scoring" button label changes to "Resume Scoring" when there is an active match.

**Scoring engine unchanged**: `ScoreReducer`, `MatchState`, `ScoreEvent`, and `MatchViewModel`'s event-log approach are all preserved. The only addition to `MatchViewModel` is `initFromMatch(match)`, which resets the event log and seeds the initial `MatchState` with the batting/bowling team names from the `Match` entity.

**Architecture direction**: The app is now local-first. All state lives in memory for this phase. `MatchRepository` is the single source of truth for created matches; `MatchSessionViewModel` is the UI-facing interface to that repository. The repository interface is intentionally narrow so it can be replaced by a Supabase-backed implementation in Phase 3 without touching the ViewModels.

---

### 2026-03-06 – Phase 1: Scored Foundation

**Feature:** Rename app to Scored, redesign HomeScreen, add placeholder screens

**Files changed:**
| File | Action |
|------|--------|
| `app/src/main/res/values/strings.xml` | Updated |
| `app/src/main/java/com/example/scorebroadcaster/ui/HomeScreen.kt` | Updated |
| `app/src/main/java/com/example/scorebroadcaster/ui/CreateMatchScreen.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/ui/MyMatchesScreen.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/MainActivity.kt` | Updated |
| `README.md` | Updated |

**Explanation:**
Converted the ScoreBroadcaster prototype into the Scored product foundation. The app name was updated in `strings.xml`. `HomeScreen` was redesigned with four product-oriented primary actions that replace the previous three developer-labelled buttons: **Create Match** (placeholder), **My Matches** (placeholder), **Live Scoring** (CameraPreviewScreen), and **Go Live** (RTMP stream setup + preview). Two placeholder screens — `CreateMatchScreen` and `MyMatchesScreen` — were added with "coming soon" copy so the navigation is complete without dead routes. `MainActivity` was updated to register the new `create_match` and `my_matches` routes. All existing scoring, camera, and streaming screens are unchanged. No Supabase integration in this phase.

---

### 2026-03-04 (8)

**Files modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/streaming/ScoreboardOverlayRenderer.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/streaming/RtmpLiveStreamer.kt` | Updated |
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/LiveStreamViewModel.kt` | Updated |
| `app/src/main/java/com/example/scorebroadcaster/ui/StreamPreviewScreen.kt` | Updated |
| `README.md` | Updated |

**Explanation:**
Burned scoreboard overlay into RTMP stream using RootEncoder image overlay; live updates from MatchState.

`ScoreboardOverlayRenderer` renders a `MatchState` to a reused ARGB_8888 `Bitmap` (1280 × 140 px) using Android Canvas/Paint — matching the styling of the existing Compose `ScoreboardOverlay`. A semi-transparent (~80 % opaque) dark background is drawn first, followed by an optional last-ball delivery row (wickets red, boundaries blue) and a main bar with team title on the left and runs/wickets + overs on the right. A `Mutex` guards the single bitmap buffer so `render()` is safe to call from a background coroutine.

`RtmpLiveStreamer` now holds an `ImageObjectFilterRender` (from `com.pedro.encoder.input.gl.render.filters.object`) which is registered with `rtmpCamera.getGlInterface().addFilter(overlayFilter)` after `startStream()`. This composites the bitmap as an OpenGL texture on every encoded frame. `updateOverlayBitmap(bitmap)` calls `overlayFilter.setImage(bitmap)`, `setDefaultScale(VIDEO_WIDTH, VIDEO_HEIGHT)`, and `setPosition(TranslateTo.BOTTOM)` so the overlay fills the full width at the bottom of the frame. `release()` calls `clearFilters()` to remove the overlay cleanly.

`LiveStreamViewModel.startStreaming()` now accepts a `StateFlow<MatchState>` parameter and launches a coroutine (via `viewModelScope`) that collects the flow with a 100 ms `debounce` (≤ 10 updates/second), renders each state on `Dispatchers.Default`, and calls `updateOverlayBitmap` with the result. The overlay job is cancelled in `stopStreaming()` and `onCleared()`. `StreamPreviewScreen` was updated to inject `MatchViewModel` and pass `matchViewModel.state` to `startStreaming`.

---

### 2026-03-04 (fix)

**Fix:** Facebook Live streaming — four bugs resolved

**Files modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/streaming/RtmpLiveStreamer.kt` | Updated |
| `app/src/main/java/com/example/scorebroadcaster/ui/StreamSetupScreen.kt` | Updated |
| `README.md` | Updated |

**Explanation:**
Fixed four bugs that prevented Facebook Live streaming from connecting:

1. **Wrong protocol (Bug 1):** Replaced `RtmpCamera2` (plain RTMP) with `RtmpsCamera2` (RTMPS/TLS). Facebook's ingest endpoint `rtmps://live-api-s.facebook.com:443/rtmp/` requires TLS; connections over plain RTMP are rejected. Import changed from `com.pedro.library.rtmp.RtmpCamera2` to `com.pedro.library.rtmps.RtmpsCamera2`. KDoc and log messages updated accordingly.

2. **Infinite retry loop (Bug 2):** In `handleConnectionFailed`, the `else` branch (all retries exhausted) now calls `rtmpCamera.getStreamClient().setReTries(0)` before `stopStream()`. This makes RootEncoder's internal `shouldRetry()` return `false`, stopping the "Reconnecting" loop before `callback.onError(...)` is fired.

3. **`iFrameInterval = 0` rejects keyframes (Bug 3):** Changed `iFrameInterval` from `0` to `2` in `rtmpCamera.prepareVideo(...)`. Facebook's ingest server requires periodic keyframes; `0` disables them on many Android encoders.

4. **URL construction (Bug 4):** `buildRtmpUrl` now handles a blank stream key — if the user pasted the full stream URL into the Server URL field, the stream key field is left empty and no trailing `/` is appended.

**UI:** `StreamSetupScreen` Server URL placeholder updated to `rtmps://live-api-s.facebook.com:443/rtmp` and the Stream Key field gained a supporting hint: *"Leave blank if the stream key is already in the Server URL"*.

---

### 2026-03-04 (7)

**Feature:** Added RTMP streaming via pedroSG94/RootEncoder (`RtmpCamera2`, H.264 + AAC)

**Files created/modified:**
| File | Action |
|------|--------|
| `settings.gradle.kts` | Updated – added JitPack repository |
| `gradle/libs.versions.toml` | Updated – added `rootEncoder = "2.4.7"` version + `root-encoder` library alias |
| `app/build.gradle.kts` | Updated – added `root-encoder` dependency |
| `app/src/main/AndroidManifest.xml` | Updated – added `RECORD_AUDIO`, `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE` permissions |
| `app/src/main/java/com/example/scorebroadcaster/streaming/RtmpLiveStreamer.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/LiveStreamViewModel.kt` | Updated |
| `app/src/main/java/com/example/scorebroadcaster/ui/StreamSetupScreen.kt` | Updated |
| `app/src/main/java/com/example/scorebroadcaster/ui/StreamPreviewScreen.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/MainActivity.kt` | Updated |
| `README.md` | Updated |

**Explanation:**
Integrated [pedroSG94/RootEncoder](https://github.com/pedroSG94/RootEncoder) (`com.github.pedroSG94.RootEncoder:library:2.4.7`) via JitPack for hardware-accelerated H.264 + AAC RTMP streaming. `RtmpLiveStreamer` wraps `RtmpCamera2` and exposes `startPreview()`, `start(config)`, and `release()`. It implements `ConnectCheckerRtmp` and forwards lifecycle events (`onConnecting`, `onConnected`, `onDisconnected`, `onReconnecting`, `onError`) via a `StreamStatusCallback` interface with up to three automatic reconnect attempts. `LiveStreamViewModel` now holds an `RtmpLiveStreamer` instance: `prepareStreaming(config)` persists credentials and stages the config; `startStreaming(surfaceView)` creates the streamer, opens the camera preview, and starts the RTMP session; `stopStreaming()` / `onCleared()` cleanly release the streamer. `StreamSetupScreen` was updated to call `prepareStreaming(config)` and navigate to the new `stream_preview` route instead of starting streaming directly. `StreamPreviewScreen` is a new full-screen composable that embeds `RtmpCamera2`'s `SurfaceView` via `AndroidView`, requests CAMERA and RECORD_AUDIO permissions at runtime, shows a red **"● LIVE"** badge while streaming, and provides a **Stop Streaming** button. Streaming starts in a `DisposableEffect` when permissions are granted and stops automatically when the screen is popped from the back stack. The scoreboard overlay is intentionally excluded from the stream at this stage.

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

### 2026-03-04

**Feature:** Initial project setup and core cricket scoring engine

**Files created/modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/data/ScoreEvent.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/data/MatchState.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/domain/ScoreReducer.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchViewModel.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/MainActivity.kt` | Created |
| `README.md` | Created |

**Explanation:**
Set up the full application from scratch. `ScoreEvent` is a sealed class covering every legal cricket delivery outcome (Run, Wicket, Wide, NoBall, Bye, LegBye). `MatchState` is an immutable data class holding runs, wickets, overs, ball count, and the last six deliveries for the over summary. `ScoreReducer` is a stateless pure function that computes the next `MatchState` from the current state and a single event, including over progression and extras handling. `MatchViewModel` stores the append-only event list and exposes a `StateFlow<MatchState>` derived by folding all events through the reducer; it also provides `dispatch` and `undo` methods. `ScoringScreen` renders the scoreboard and scoring buttons using Jetpack Compose and collects state from the ViewModel. `MainActivity` bootstraps Compose and injects the ViewModel.

