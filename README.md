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
| Match-context header (title, format, innings, teams) | ✅ Done | `ScoringScreen` |
| Batter / bowler tracking with live stats | ✅ Done | `ScoringScreen` |
| Opening batters & bowler setup dialog | ✅ Done | `ScoringScreen` |
| Wicket → select next batter dialog (+ add new player inline) | ✅ Done | `ScoringScreen` |
| Over-end → select new bowler dialog (+ add new player inline) | ✅ Done | `ScoringScreen` |
| Innings management (end 1st innings, start 2nd) | ✅ Done | `ScoringScreen` / `MatchViewModel` |
| Target / chase info panel (2nd innings) | ✅ Done | `ScoringScreen` |
| Match-complete result banner | ✅ Done | `ScoringScreen` |
| Extras entry dialog (variable runs, wicket/run-out on extras) | ✅ Done | `ScoringScreen` |
| Live camera preview + scoreboard overlay | ✅ Done | `CameraPreviewScreen` |
| RTMPS streaming to Facebook Live | ✅ Done | `StreamPreviewScreen` |
| Scoreboard burned into live stream | ✅ Done | `ScoreboardOverlayRenderer` |
| Stream setup (URL, key, bitrate) | ✅ Done | `StreamSetupScreen` |
| Home screen with primary actions | ✅ Done | `HomeScreen` |
| Create Match (real form) | ✅ Done | `CreateMatchScreen` |
| Player setup | ✅ Done | `PlayerSetupScreen` |
| Pre-match summary + Start Match | ✅ Done | `MatchSummaryScreen` |
| My Matches (local in-memory list) | ✅ Done | `MyMatchesScreen` |
| Add player after match start | ✅ Done | `ScoringScreen`, `MatchDetailsScreen` |
| Saved teams (create, view, reuse) | ✅ Done | `SavedTeamsScreen`, `CreateMatchScreen` |
| Scorecard view (batting + bowling summary, both innings) | ✅ Done | `ScorecardScreen` |
| Ball timeline / over history (per-ball, grouped by over) | ✅ Done | `BallTimelineScreen` |
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
│       ├── BowlingEntry.kt
│       ├── ExtrasBreakdown.kt     # ← Phase 5: extras breakdown per delivery
│       └── SavedTeam.kt           # ← Phase 4: reusable team template
├── domain/           # Pure business logic: BallEvent, ScoreReducer
├── repository/       # ← Phase 2: local in-memory repository
│   ├── MatchRepository.kt
│   └── SavedTeamRepository.kt # ← Phase 4
├── streaming/        # RTMP streaming: RtmpLiveStreamer, ScoreboardOverlayRenderer
├── ui/               # Compose screens and theme
│   ├── theme/        # Material3 theme (Color, Type, Theme)
│   ├── HomeScreen.kt              ← Phase 2: active-match banner
│   ├── CreateMatchScreen.kt       ← Phase 4: saved-team picker added
│   ├── PlayerSetupScreen.kt       ← Phase 2: new
│   ├── MatchSummaryScreen.kt      ← Phase 2: new
│   ├── MyMatchesScreen.kt         ← Phase 2: real in-memory list
│   ├── MatchDetailsScreen.kt      ← Phase 4: add-player button
│   ├── SavedTeamsScreen.kt        ← Phase 4: new
│   ├── CameraPreviewScreen.kt
│   ├── ScoringScreen.kt           ← Phase 4: wicket/bowler add-new-player + add-player button
│   ├── ScoreboardOverlay.kt
│   ├── StreamSetupScreen.kt
│   └── StreamPreviewScreen.kt
├── viewmodel/
│   ├── MatchViewModel.kt          ← Phase 4: addPlayerToTeam() added
│   ├── MatchSessionViewModel.kt   ← Phase 4: savedTeams CRUD added
│   └── LiveStreamViewModel.kt
└── MainActivity.kt               ← Phase 4: saved_teams route added
```

### Event-based Scoring Engine (BallEvent Reducer Pattern)
Scoring is modelled as an append-only event log:
1. Every user action (run, wicket, wide, no-ball, bye, leg-bye) is represented as a `ScoreEvent` subclass at the UI layer. Each `ScoreEvent` is converted to a `BallEvent` before being appended to the internal log.
2. `BallEvent` (`domain/BallEvent.kt`) is the canonical delivery model. It carries `runsOffBat`, an `ExtrasBreakdown` (wides, noBalls, byes, legByes), a `wicket` flag, optional `DismissalDetail`, and a `countsAsBall` flag.
3. `ScoreReducer.kt` contains a pure `reduce(events: List<BallEvent>)` function that returns a new `MatchState` without mutating anything.
4. `MatchViewModel` maintains the full event history as `List<BallEvent>` and recomputes the current state by folding all events through the reducer. This makes **undo** trivial — simply drop the last event and re-reduce.
4. Because state is always derived from the event log, replaying, debugging, or persisting a match is straightforward.

---

## Development Log

### 2026-03-07 – Reusable Player Profiles

**What changed:**
Introduced a reusable player-profile model alongside a dedicated Saved Players screen.  Players can now be saved privately and reused when building teams or setting up a match, while every match-level player entry remains an independent snapshot so historical scorecards are never affected by later profile edits.

**Files created/modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/data/entity/PlayerProfile.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/data/entity/Player.kt` | Updated |
| `app/src/main/java/com/example/scorebroadcaster/repository/SavedPlayerRepository.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchSessionViewModel.kt` | Updated |
| `app/src/main/java/com/example/scorebroadcaster/ui/SavedPlayersScreen.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/ui/PlayerSetupScreen.kt` | Updated |
| `app/src/main/java/com/example/scorebroadcaster/ui/SavedTeamsScreen.kt` | Updated |
| `app/src/main/java/com/example/scorebroadcaster/ui/AppShell.kt` | Updated |
| `app/src/main/java/com/example/scorebroadcaster/MainActivity.kt` | Updated |
| `README.md` | Updated |

**Architecture:**

*PlayerProfile* (`data/entity/PlayerProfile.kt`) is the reusable template.  It carries:
- `id`, `displayName`
- `playerSourceType: PlayerSourceType` — enum with `PRIVATE` (today) and `APP_USER` (future).  Adding a new source type later requires no changes to repositories or view-models.
- `linkedUserId: String?` — nullable; will hold the account id when `APP_USER` profiles are introduced without a model change.
- Nullable metadata stubs: `avatarUrl`, `role`, `battingStyle`, `bowlingStyle` — present in the schema for future extension.

A `PlayerProfile.toMatchPlayer()` extension function snapshots the profile into a `Player` at selection time.

*Player* (`data/entity/Player.kt`) — match-level snapshot — gains one new nullable field `sourceProfileId: String? = null`.  This records which profile the player came from while keeping the data fully independent.  All existing code continues to work unchanged because the field has a default value.

*SavedPlayerRepository* (`repository/SavedPlayerRepository.kt`) — in-memory singleton with `addPlayer`, `removePlayer`, `updatePlayer`, and `findById`.  Follows the same pattern as `SavedTeamRepository`.

*MatchSessionViewModel* — `savedPlayers: StateFlow<List<PlayerProfile>>` exposed alongside the existing `savedTeams`.  `addSavedPlayer` / `removeSavedPlayer` methods added; `refresh()` also refreshes the player list.

**Team vs match player handling:**
- `SavedTeam.players: List<Player>` stores snapshots created at team-save time.  If the originating `PlayerProfile` is later renamed, the team template is unaffected (by design — the team can be updated manually).
- When a match is created from a team, the team's `Player` list is copied into `Match.teamA/B.players`.  This is a second-level snapshot.
- `BattingEntry.player` and `BowlingEntry.player` hold yet another copy captured at the moment the batter/bowler is selected.  Three independent copy points ensure maximum scorecard stability.

**UX additions:**
- *Saved Players screen* (`saved_players` route in `MainActivity`, drawer item in `AppShell`) — list, create, and delete private player profiles.
- *PlayerSetupScreen* — each player slot gains a person-icon button that opens `SavedPlayerPickerDialog` to fill the slot from a saved profile.  Manual typing is still fully supported.
- *SavedTeamsScreen* `CreateSavedTeamDialog` — same person-icon button added to each player row so teams can be assembled from existing saved profiles.

**Future readiness:**
The `PlayerSourceType` enum and `linkedUserId` field mean that when app-user player search is added, a new `APP_USER` profile can be created and stored without touching the existing `PRIVATE` flow or any scoring logic.

---

### 2026-03-07 – Phase 8: Ball Editing / Correction

**What changed:**
Added the ability to tap any ball in the timeline and edit or delete it. Correcting a delivery updates the event log and replays all remaining events through the existing pure reducer to rebuild the innings aggregate state — no aggregate score is mutated directly. The edit flow supports all delivery outcomes: normal runs (dot / 1 / 2 / 3 / 4 / 6 / custom), extras (Wide, No Ball, Bye, Leg Bye) with variable runs, wickets with full dismissal details (type, batter out, fielder, bowler), and extras with run-outs. Deleting a ball requires a confirmation step showing the over/ball number and current label.

**Files created/modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchViewModel.kt` | Updated |
| `app/src/main/java/com/example/scorebroadcaster/ui/EditBallDialog.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/ui/BallTimelineScreen.kt` | Updated |
| `README.md` | Updated |

**Architecture — replay-based correction:**
1. `MatchViewModel.replaceBallEvent(globalIndex, updatedEvent, inFirstInnings)` — replaces the event at `globalIndex` in the target event log (current innings or first-innings archive) and re-reduces all events to produce the new `MatchState`.
2. `MatchViewModel.deleteBallEvent(globalIndex, inFirstInnings)` — removes the event at `globalIndex` and re-reduces. Requires a confirmation step in the UI.
3. `rebuildFirstInningsSnapshot(events)` (private) — after any first-innings edit, re-reduces the first-innings events and updates the aggregate snapshot fields in `ScoringConsoleState` (runs, wickets, extras breakdown, overs, target). Per-player batting/bowling entries are not rebuilt on edit (same simplification as `undo()`).
4. The `IndexedBall.globalIndex` field (already present from Phase 7) is used as the stable identifier to locate the event.

**UX:**
- Each `BallChip` in the timeline is now tappable (accessible with `Role.Button`).
- Tapping opens `EditBallDialog` pre-populated with the existing delivery's values.
- The dialog title shows the over number, ball-in-over position, and the original label ("was: …") for context.
- The dialog has three sections: delivery type/runs, wicket details, and a "Delete this ball" danger button.
- Deleting shows a nested `AlertDialog` with the ball coordinates, requiring explicit confirmation before removal.

---

### 2026-03-07 – Phase 7: Ball Timeline / Over History

**What changed:**
Added a full ball-by-ball timeline and over history screen (`BallTimelineScreen`) for the active innings. All deliveries are displayed in compact cricket notation, grouped into over cards and rendered in a scrollable `LazyColumn`. Multi-innings matches show a tab switcher so the scorer can toggle between 1st and 2nd innings histories.

**Grouping and formatting approach:**

- **`BallTimelineFormatter`** (`domain/BallTimelineFormatter.kt`) is a pure Kotlin object (no Android or Compose dependencies) responsible for all non-UI logic:
  - `formatBall(event: BallEvent): String` — converts a `BallEvent` to compact cricket notation (`.`, `1`, `4`, `6`, `W`, `W (run out)`, `wd`, `wd+2`, `nb`, `nb+4`, `nb+W`, `b2`, `lb3`).
  - `groupByOver(events: List<BallEvent>): List<OverSummary>` — folds the event log into `OverSummary` objects, respecting the `countsAsBall` flag so wides and no-balls are correctly placed in the over without advancing the ball counter.
  - `OverSummary` — data class holding the 1-based `overNumber` and a list of `IndexedBall` objects.
  - `IndexedBall` — data class carrying `globalIndex` (position in the event log), `overNumber`, `ballInOver`, `display` string, and the original `BallEvent`. The stable `globalIndex` keeps each ball identifiable so future edit-ball support can be wired without structural changes.

- **`MatchViewModel`** now exposes two public `StateFlow<List<BallEvent>>`:
  - `events` — the current-innings event log (always the live innings).
  - `firstInningsEvents` — snapshot of the first-innings log, populated when `endFirstInnings()` is called and reset when `initFromMatch()` is called.

- **`BallTimelineScreen`** (`ui/BallTimelineScreen.kt`) reads from both flows, calls `BallTimelineFormatter.groupByOver()`, and renders:
  - An innings tab row (shown only when a second innings is available) using `FilterChip`s.
  - A `LazyColumn` of `OverCard` composables — each card shows the over label and a `FlowRow` of colour-coded `BallChip` composables.
  - Ball chips use distinct background colours: error container for wickets, primary container for boundaries, tertiary container for extras, and surface variant for normal deliveries.
  - An empty-state message when no deliveries have been recorded.

**Navigation:**
- Accessible from: **ScoringScreen** quick-nav bar → "Timeline", **MatchDetailsScreen** → "Ball Timeline" button, **navigation drawer** → "Ball Timeline" item (new).
- Route `ball_timeline` registered in `MainActivity`.
- `TopAppBar` title for `ball_timeline` is "Over History".
- Bottom-nav tab highlight maps `ball_timeline` to the **Score** tab.

**Files created/modified:**

| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/domain/BallTimelineFormatter.kt` | Created – `IndexedBall`, `OverSummary`, `BallTimelineFormatter` |
| `app/src/main/java/com/example/scorebroadcaster/ui/BallTimelineScreen.kt` | Created – `BallTimelineScreen` and helpers |
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchViewModel.kt` | Updated – exposed `events` and `firstInningsEvents` StateFlows; snapshots first-innings events in `endFirstInnings()` |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Updated – added `onViewTimeline` param and "Timeline" button to `QuickNavBar` |
| `app/src/main/java/com/example/scorebroadcaster/ui/MatchDetailsScreen.kt` | Updated – added `onViewTimeline` param and "Ball Timeline" button in `MatchActionButtons` |
| `app/src/main/java/com/example/scorebroadcaster/ui/AppShell.kt` | Updated – added "Ball Timeline" drawer item, title mapping, and `selectedTab` mapping |
| `app/src/main/java/com/example/scorebroadcaster/MainActivity.kt` | Updated – wired `ball_timeline` route; passed `onViewTimeline` to `ScoringScreen` and `MatchDetailsScreen` |
| `README.md` | Updated |

**What did NOT change:**
- Scoring engine (`ScoreReducer`, `BallEvent`, `MatchState`) — untouched.
- Existing screens (`ScorecardScreen`, `CameraPreviewScreen`, `StreamPreviewScreen`) — untouched.
- Scoring flow, undo, extras dialog, wicket dialog — untouched.
- This phase is **display-only**: no ball editing is implemented.

---

### 2026-03-07 – Phase 6: Extras Entry Dialog

**What changed:**
Replaced the four fixed extra buttons (`Wd+1`, `NB+1`, `Bye`, `LB`) with a proper extras-entry workflow. Tapping any extras button now opens an `ExtrasEntryDialog` that lets the scorer specify variable runs, choose the correct extra type, and optionally record a run-out wicket on the same delivery.

**Extras entry dialog:**
- Scorer taps **Wide**, **No Ball**, **Bye**, or **Leg Bye** in the scoring panel.
- `ExtrasEntryDialog` opens with the tapped type pre-selected.
- Scorer can change the type, select runs (1 / 2 / 3 / 4 / 5+), and optionally tick "Wicket on this ball (Run Out only)".
- If a wicket is selected, the scorer chooses which batter was run out (striker or non-striker) and optionally which fielder was involved.
- Confirmation builds the correct `BallEvent` directly and dispatches it via `MatchViewModel.addBallEvent()`.

**Variable extras support:**
- Runs from 1 up to 4 are selectable as chips; a "5+" option reveals a free-text numeric input.
- Wide: all runs go to `ExtrasBreakdown.wides` (no ball face counted).
- No Ball: 1-run penalty in `noBalls`; remaining runs go to `runsOffBat`.
- Bye / Leg Bye: all runs go to `byes` / `legByes` respectively and count as a legal ball.

**Wicket on extras:**
- Only Run Out is allowed on extras deliveries, matching real-world cricket rules.
- The `DismissalDetail` is created with `bowler = null` so the bowler is not credited.
- The existing `MatchViewModel.updateConsoleAfterEvent` correctly handles the wicket flag on a `BallEvent`, so next-batter selection, strike rotation, and all-out detection all work without modification.

**New `addBallEvent` method:**
`MatchViewModel` now exposes `addBallEvent(BallEvent)` as a public entry point for cases where the full delivery cannot be expressed as a single `ScoreEvent`. The existing `addEvent(ScoreEvent)` delegates to this method, keeping behaviour identical.

**Files changed:**

| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Updated – `ExtraType` enum added; `ExtrasEntryDialog` and `buildExtrasEvent` added; `ScoringButtonsSection` extras buttons replaced with dialog-opening buttons; extras dialog state wired in `ScoringScreen` |
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchViewModel.kt` | Updated – `addBallEvent(BallEvent)` method added; `addEvent` delegates to it |
| `README.md` | Updated |

**What did NOT change:**
- `0`, `1`, `2`, `3`, `4`, `6`, `W`, and `Undo` scoring buttons — untouched.
- Normal wicket dialog (`WicketDetailsDialog`) — untouched.
- Innings / match control flow — untouched.
- Camera preview and Facebook Live streaming — untouched.
- Scorecard screen — untouched.
- `BallEvent`, `ScoreEvent`, `ScoreReducer`, `ExtrasBreakdown` — untouched.

---

### 2026-03-07 – Phase 5: Flexible Ball Event Model

**Why BallEvent was introduced:**
The original `ScoreEvent` sealed class modelled each delivery as a single, flat type (Run, Wide, NoBall, Bye, LegBye, Wicket). This worked for simple outcomes but could not represent combined real-world deliveries such as *Wide + 4 runs*, *NoBall + run out*, *Bye + 3*, or *LegBye + 2*. A richer domain model was needed so that one delivery object can capture every possible outcome without ambiguity.

**How extras and wickets are now modelled:**
- **`ExtrasBreakdown`** (`data/entity/ExtrasBreakdown.kt`) — a data class with four fields (`wides`, `noBalls`, `byes`, `legByes`) that records precisely which extras were conceded and how many. A `total` computed property sums them. A `NONE` companion constant is provided for the common case of no extras.
- **`BallEvent`** (`domain/BallEvent.kt`) — the new canonical delivery model. Fields:
  - `runsOffBat` — runs credited to the batter.
  - `extras` — an `ExtrasBreakdown` for any extras on the delivery.
  - `wicket` — whether a dismissal occurred.
  - `dismissalDetail` — full dismissal information (null when no wicket).
  - `countsAsBall` — `true` for legal deliveries; `false` for wides and no-balls, which do not increment the over counter.
- **`ScoreReducer`** now accepts `List<BallEvent>` and applies a single unified `applyEvent` function. Ball-count logic, extras breakdown, and run totals are all derived from `BallEvent` fields rather than from the type of the event.

**Backward compatibility:**
`ScoreEvent` (the original sealed class) is retained unchanged. A `ScoreEvent.toBallEvent()` extension function converts each legacy variant to the equivalent `BallEvent`. `MatchViewModel.addEvent(ScoreEvent)` converts at the boundary so the UI buttons (`0`, `1`, `2`, `3`, `4`, `6`, `W`, `Wd+1`, `NB+1`, `Bye`, `LB`) continue to work without modification.

**Files modified:**

| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/data/entity/ExtrasBreakdown.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/domain/BallEvent.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/data/ScoreEvent.kt` | Updated – added `toBallEvent()` extension |
| `app/src/main/java/com/example/scorebroadcaster/domain/ScoreReducer.kt` | Updated – now reduces `List<BallEvent>` |
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchViewModel.kt` | Updated – internal event log changed to `List<BallEvent>`; `updateConsoleAfterEvent` rewritten for `BallEvent` |
| `README.md` | Updated |

---

### 2026-03-07 – UI Cleanup: Remove Duplicate Screen Titles

**Change:** Removed duplicate page-title headings from three screens that already display the same title in the `TopAppBar` provided by `AppShell`.

**Screens modified:**
| Screen | Duplicate removed |
|--------|-------------------|
| `MyMatchesScreen` | `Text("My Matches", headlineMedium)` — title already shown in TopAppBar |
| `SavedTeamsScreen` | `Text("Saved Teams", headlineSmall)` — title already shown in TopAppBar; "New Team" button retained |
| `MatchDetailsScreen` | Custom in-content top-bar `Row` containing back icon + `Text("Match Details")` — both already provided by `AppShell`'s `TopAppBar` |

**What changed:**
- `MyMatchesScreen`: Removed the `Text("My Matches")` heading and the `Spacer(height(16.dp))` that followed it. Adjusted top padding to `vertical = 16.dp` so content remains well-spaced below the `TopAppBar`.
- `SavedTeamsScreen`: Removed the `Text("Saved Teams")` from the header `Row`. Changed the row's `horizontalArrangement` from `SpaceBetween` to `End` so the "New Team" button stays right-aligned without a blank label on the left.
- `MatchDetailsScreen`: Removed the entire custom top-bar `Row` (back `IconButton` + `Text("Match Details")`). Added `vertical = 12.dp` padding to the content `Column` to preserve breathing room below the `TopAppBar`. Removed the now-unused `Icons`, `Icon`, `IconButton`, and `ArrowBack` imports.

**What did NOT change:**
- Navigation routes, screen composable signatures, and `onBack` / navigation callbacks are untouched.
- Section headers inside screen content (`"Batting"`, `"Bowling"`, `"1st Innings — …"`, etc.) are preserved.
- View models and scoring logic are unaffected.
- `ScorecardScreen` was audited and found to be clean: its `ScorecardMatchHeader` displays the actual match title (e.g. "Team A vs Team B"), which is distinct from the TopAppBar label "Scorecard".

### 2026-03-07 – Innings Setup Popup Flow Fix

**Fix:** Innings setup dialog (opening batters + bowler) is now safe, dismissible, and handles missing players gracefully.

**Files modified:**
| File | Change |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | See details below |
| `README.md` | Added this log entry |

**What was corrected:**

1. **Dismissible dialog** — `SetupOpenersDialog` previously had `onDismissRequest = { /* must complete setup */ }` (a no-op), trapping the user. It now accepts an `onDismiss` callback and exposes a **"Later"** dismiss button, so the user can close it and return at any time.

2. **Setup required banner** — When the innings-setup dialog is dismissed without completing setup, a red "Innings setup required before scoring can begin" banner appears on the scoring screen with a **"Setup"** button to re-open the dialog. Scoring controls remain disabled until setup is complete (unchanged behaviour, now clearly communicated).

3. **Missing-player warnings** — If the batting team has fewer than 2 players, the dialog shows: *"You need at least 2 batters to start the innings."* If the bowling team has no players, it shows: *"You need at least 1 bowler to start the innings."* These messages appear inline, above the relevant dropdowns.

4. **Inline add-player** — The dialog now contains an "Add batter" row (name field + Add button) below the batting dropdowns, and an "Add bowler" row below the bowling dropdown. Adding a player calls `MatchViewModel.addPlayerToTeam()`, which immediately updates `_activeMatch` and the repository. Because `activeMatch` is a `StateFlow`, the composable recomposes and the new player appears in the dropdowns without closing the dialog.

**Architecture notes:**
- All new player-management logic stays in `MatchViewModel.addPlayerToTeam()` (unchanged).
- No new ViewModel methods were needed.
- The `InningsPhase.SETUP` phase continues to disable scoring controls (no change to reducer or phase logic).
- Wicket flow, bowler-change flow, camera preview, and Facebook Live flow are unaffected.

### 2026-03-06 – Wicket Dismissal Detail Support

**Feature:** Proper wicket detail capture for realistic MVP scoring — dismissal type, fielder, and bowler credit.

**Files created:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/data/entity/DismissalType.kt` | Created — enum with Bowled, Caught, LBW, Run Out, Stumped, Other |
| `app/src/main/java/com/example/scorebroadcaster/data/entity/DismissalDetail.kt` | Created — data class capturing batter, type, optional fielder, bowler, and scorecard string helper |

**Files modified:**
| File | Change |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/data/ScoreEvent.kt` | `Wicket` changed from `data object` to `data class Wicket(val dismissal: DismissalDetail)` |
| `app/src/main/java/com/example/scorebroadcaster/data/entity/BattingEntry.kt` | Replaced `dismissalInfo: String` with `dismissal: DismissalDetail?` |
| `app/src/main/java/com/example/scorebroadcaster/data/ScoringConsoleState.kt` | `SelectNextBatter` gains `replacingStriker: Boolean` to handle non-striker run outs |
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchViewModel.kt` | Wicket handling now marks the correct player out; Run Out does not credit the bowler; non-striker run outs place new batter at non-striker's end |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | W button now opens `WicketDetailsDialog`; new `WicketDetailsDialog` composable for selecting who's out, dismissal type, and optional fielder; `SelectNextBatter` dialog title adapts (striker vs non-striker replacement) |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScorecardScreen.kt` | `BattingTableRow` now displays proper dismissal text (e.g., "c Smith b Jones", "run out (Brown)") |
| `README.md` | Added this log entry |

**What was added/refactored:**

- **`DismissalType` enum** — six types: Bowled, Caught, LBW, Run Out, Stumped, Other.
- **`DismissalDetail` data class** — stores the dismissed batter, dismissal type, optional fielder (catcher / wicketkeeper / run-out fielder), and the bowler at the time of the wicket. Provides a `toScorecardString()` helper that produces standard cricket scorecard notation (e.g., `"c Jones b Smith"`, `"lbw b Smith"`, `"run out (Brown)"`). The `bowlerCredited` computed property returns `false` only for Run Out.
- **`ScoreEvent.Wicket` refactored** — carries a full `DismissalDetail` instead of being a singleton object, enabling the event log to record every dismissal.
- **`BattingEntry.dismissal`** — replaces the old plain-string `dismissalInfo` field with the structured `DismissalDetail?`.
- **Bowler credit** — `MatchViewModel` inspects `dismissal.bowlerCredited` when updating the bowler's `BowlingEntry`; Run Out deliveries increment the bowler's ball count but do **not** increment their wicket tally.
- **Non-striker run out** — when the scorer selects the non-striker as out, `MatchViewModel` correctly marks the non-striker's `BattingEntry` as dismissed, leaves the striker in place, and sets `SelectNextBatter(replacingStriker = false)` so the incoming batter fills the non-striker's end.
- **`WicketDetailsDialog`** — new Compose dialog shown when the W button is tapped. Step-by-step UI: (1) who got out (striker / non-striker filter chips); (2) dismissal type (6 filter chips); (3) fielder selector (shown only for Caught, Stumped, Run Out). After confirmation, dispatches `ScoreEvent.Wicket(dismissal)` and the existing next-batter selection dialog follows.
- **Scorecard display** — `BattingTableRow` renders the full dismissal description underneath the batter's name instead of a plain "out".



**Feature:** Make Saved Teams a first-class part of match creation — users can now choose between a saved team and a new team directly inside `CreateMatchScreen`, with an option to save newly created teams for later reuse.

**Files modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/CreateMatchScreen.kt` | Updated — explicit mode selectors, save-team checkbox, selected-team chip |
| `README.md` | Updated — added this log entry |

**What was corrected / added:**

Previously, `CreateMatchScreen` showed a small **"Saved"** `OutlinedButton` next to each team name field, but only when at least one saved team already existed. First-time users saw no hint that the feature existed, and the button was easy to overlook even for returning users.

**Changes in `CreateMatchScreen.kt`:**

1. **Explicit mode selector per team** — each team section now shows two `FilterChip`s labelled **"New Team"** and **"Use Saved Team"**. These are always visible, regardless of whether any saved teams exist, so the choice is obvious at first glance.

2. **"Use Saved Team" path:**
   - When no saved teams exist: an informational note is shown directing the user to the Saved Teams section in the menu.
   - When saved teams exist and none is selected yet: a full-width **"Select a saved team…"** `OutlinedButton` opens `SavedTeamPickerDialog`.
   - Once a team is selected: a `SavedTeamChip` surface card shows the team name, player count, and a **"Change"** `TextButton` to reopen the picker.
   - Players are deep-copied from the saved team template into local match state so the match remains fully independent of the template.
   - `PlayerSetupScreen` is pre-filled with those copied players and the user can still edit/add/remove them for this specific match.

3. **"New Team" path (updated):**
   - The existing name text field is shown as before.
   - A new **"Save this team for future matches"** `Checkbox` row appears below the name field.
   - When the user taps **"Next: Add Players →"** with the checkbox checked, `MatchSessionViewModel.addSavedTeam()` is called with the team name, persisting it to `SavedTeamRepository`. Players are not yet included in the saved template at this stage (they are set up match-specifically in `PlayerSetupScreen`).

4. **Visual separators** — `HorizontalDivider`s are added between the "Team A", "Team B", and format sections to clearly delineate the form layout.

5. **Derived team names** — `finalTeamAName` / `finalTeamBName` are resolved from either the saved-team selection or the typed name, and used consistently for toss chip labels and match object construction.

6. **`canProceed` guard** — now checks `teamAReady && teamBReady` where readiness is mode-aware: "New Team" requires a non-blank name; "Use Saved Team" requires a selection.

**What was NOT changed:**
- `PlayerSetupScreen` — unchanged; it already reads players from the pending match.
- `MatchSummaryScreen` — unchanged.
- `ScoringScreen` — unchanged.
- `SavedTeamsScreen` / `SavedTeamRepository` / `MatchSessionViewModel` — unchanged.
- All navigation routes — unchanged.
- `ScoreReducer`, `MatchState`, `ScoreEvent` — unchanged.

---

### 2026-03-07 – Improvement: Team-First Next-Batter Flow

**Goal:** Make the post-wicket batter-selection flow realistic by prioritising existing batting-team players before allowing a new player to be added.

**Files changed:**
| File | Change |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchViewModel.kt` | Renamed `availableBatters()` to `eligibleNextBatters()` (public); updated call site and log message |
| `app/src/main/java/com/example/scorebroadcaster/data/ScoringConsoleState.kt` | Renamed `availablePlayers` field to `teamPlayers` in `PendingAction.SelectNextBatter`; updated KDoc |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Updated `SelectPlayerDialog` with `teamSectionLabel` and `emptyTeamMessage` params; updated `SelectNextBatter` call site to pass team-first labels |
| `README.md` | Added this log entry |

**What was corrected:**

- **`eligibleNextBatters()` in `MatchViewModel`** — renamed from the private `availableBatters()` to the public `eligibleNextBatters()` to match the naming convention requested in the problem statement. The filtering logic is unchanged: excludes current striker, current non-striker, and already-dismissed batters. Derived entirely from the batting-team roster in `_activeMatch`, so mid-match additions via `addPlayerToTeam` are automatically reflected.
- **`PendingAction.SelectNextBatter.teamPlayers`** — the field was renamed from `availablePlayers` to `teamPlayers` to clarify that these are pre-existing batting-team players, not a generic "available" list.
- **`SelectPlayerDialog` — team-first UX** — two new optional parameters added:
  - `teamSectionLabel: String?` — when provided, a labelled section header ("Select from team") appears above the player list so the scorer immediately sees the team path is primary.
  - `emptyTeamMessage: String?` — when provided and the player list is empty, a descriptive message ("No unused players left in the batting team") is shown instead of a blank space. The scorer then sees only the secondary "Add new player" section and the "No more players / All out" button.
- **Call site** — the `SelectNextBatter` dialog now passes `teamSectionLabel = "Select from team"` and `emptyTeamMessage = "No unused players left in the batting team"`. The `SelectBowler` dialog is unaffected (uses the existing defaults).

**Architecture:**

- Eligibility logic remains entirely in `MatchViewModel.eligibleNextBatters()` — nothing pushed into the composable.
- `ScoreReducer`, `MatchState`, `ScoreEvent`, `InningsPhase`, the bowler-change flow, the wicket-details flow, innings transitions, scorecard, ball editing, and the streaming/camera/Facebook Live path are all unmodified.

---

### 2026-03-07 – Bug Fix: All Out option in wicket replacement flow

**Bug:** After a wicket, the next-batter dialog only allowed selecting an existing player or adding a new one. There was no way to declare "no more players", so the innings never ended via all-out unless exactly 10 wickets had fallen. The scorer could keep adding phantom players forever.

**Files changed:**
| File | Change |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchViewModel.kt` | Added `endInningsAsAllOut()` handler |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Added `onAllOut` parameter to `SelectPlayerDialog`; wired it for `SelectNextBatter` dialog |
| `README.md` | Added this log entry |

**What was fixed:**

- **`endInningsAsAllOut()` in `MatchViewModel`** — new public method that clears the pending batter action and immediately ends the innings. If the current innings is the first, it calls `endFirstInnings()` (moving to `INNINGS_BREAK`, preserving total, calculating target). If the current innings is the second, it calls `endMatch()` (moving to `MATCH_COMPLETE`).
- **`SelectPlayerDialog` — `onAllOut` parameter** — optional `(() -> Unit)?` callback. When non-null, a prominent "No more players / All out" button (styled with `errorContainer` colour) is added after the add-new-player section. For bowler-change dialogs the callback is not passed, so the button is never shown there.
- **Scoring blocked during wicket dialog** — no change needed; `ScoringButtonsSection` is already gated on `console.pendingAction == null`. After "All out" is chosen, `pendingAction` is cleared and the phase changes to `INNINGS_BREAK` or `MATCH_COMPLETE`, which correctly hides the scoring controls.

**Debug logs added (tag `WicketFlow`):**
1. `"All out selected by scorer"` — at the start of `endInningsAsAllOut()`.
2. `"Innings ended due to all out — moving to innings break"` — when first innings ends.
3. `"Innings ended due to all out — match completed"` — when second innings ends.

**Architecture unchanged:** `ScoreReducer`, `MatchState`, `ScoreEvent`, `ScoringConsoleState`, `PendingAction`, `InningsPhase`, and the bowler-change flow are all unmodified.

---

### 2026-03-06 – Bug Fix: Wicket-to-Next-Batter Flow

**Bug:** The `SelectNextBatter` dialog was never shown after a wicket, so scoring could continue without a replacement batter being selected.

**Root cause:**  
`MatchViewModel.updateConsoleAfterEvent()` used `availableBatters().isEmpty()` as a proxy for "all out". `availableBatters()` returns an empty list whenever the batting team has no pre-registered players beyond the two openers (or when no active match is loaded). Because the list was empty, the code always fell into the `Pair(null, false)` "all-out" branch instead of setting `PendingAction.SelectNextBatter`. As a result, `pendingAction` was always `null`, the dialog was never triggered, and scoring buttons stayed enabled.

**Files changed:**
| File | Change |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchViewModel.kt` | Fixed all-out guard; added `Log.d` debug points |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Added `Log.d` when next-batter dialog is rendered |
| `README.md` | Added this log entry |

**What was corrected:**

- **All-out check** — replaced `if (remaining.isNotEmpty())` with `val allOut = newState.wickets >= 10`. Ten wickets fallen is the only reliable all-out signal; it is independent of whether players are pre-registered in the team roster.
- **Dialog always shown** — `PendingAction.SelectNextBatter(remaining)` is now set for every wicket that is not the 10th. `remaining` can be an empty list; when it is, the `SelectPlayerDialog` already shows an "Add new player" inline field so the scorer can create a batter on the fly.
- **Scoring blocked** — no change needed here; `ScoringButtonsSection` was already gated on `console.pendingAction == null`, which now works correctly because `pendingAction` is properly set.

**Debug logs added (tag `WicketFlow`):**
1. `"Wicket button tapped"` — in `addEvent()` when `ScoreEvent.Wicket` is dispatched.
2. `"pendingAction set to SelectNextBatter (N available players)"` — in `updateConsoleAfterEvent()` after the fix.
3. `"Next batter dialog shown (N players available)"` — in `ScoringScreen` when the `SelectNextBatter` branch is entered.
4. `"Next batter selected: <name>"` — at the start of `selectNextBatter()`.
5. `"pendingAction cleared after next batter selection"` — after `_consoleState` is updated in `selectNextBatter()`.

**Architecture unchanged:** `ScoreReducer`, `MatchState`, `ScoreEvent`, `ScoringConsoleState`, `PendingAction`, and `InningsPhase` are all unmodified.

---

### 2026-03-06 – Phase 4: Wicket Replacement Flow, Add-Player After Start, and Saved Teams

**Feature:** Improve scorer realism with mid-match player management and reusable saved teams.

**Files created:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/data/entity/SavedTeam.kt` | New — `SavedTeam` data class (id, name, players list) |
| `app/src/main/java/com/example/scorebroadcaster/repository/SavedTeamRepository.kt` | New — in-memory repository for saved teams |
| `app/src/main/java/com/example/scorebroadcaster/ui/SavedTeamsScreen.kt` | New — list/create saved teams UI; also exports `CreateSavedTeamDialog` and `SavedTeamPickerDialog` |

**Files modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchViewModel.kt` | Added `addPlayerToTeam(player, addToBattingTeam)` and private `Match.updateTeamRef()` helper |
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchSessionViewModel.kt` | Added saved-team CRUD (`addSavedTeam`, `removeSavedTeam`, `savedTeams: StateFlow`) |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | `SelectPlayerDialog` extended with optional `onAddNewPlayer` callback; new `AddPlayerToMatchDialog`; "＋ Add player to team" `TextButton` shown during innings |
| `app/src/main/java/com/example/scorebroadcaster/ui/CreateMatchScreen.kt` | Added optional "Saved" button next to each team name field; `SavedTeamPickerDialog` wired up; players pre-filled when a saved team is selected |
| `app/src/main/java/com/example/scorebroadcaster/ui/MatchDetailsScreen.kt` | Added `onAddPlayer` parameter to `MatchActionButtons`; "＋ Add Player to Team" outlined button; `AddPlayerToTeamDialog` composable |
| `app/src/main/java/com/example/scorebroadcaster/ui/AppShell.kt` | Added "Saved Teams" navigation drawer item; `topBarTitle` mapping for `saved_teams` route |
| `app/src/main/java/com/example/scorebroadcaster/MainActivity.kt` | Added `saved_teams` composable route |
| `README.md` | Updated feature table; added this log entry |

**What was added:**

**Wicket replacement flow improvement:**
- `SelectPlayerDialog` (used for both wicket-replacement and bowler-change) now accepts an optional `onAddNewPlayer: ((String) -> Unit)?` callback.
- When the callback is supplied, a divider and "Add new player" inline section appear at the bottom of the dialog: a text field + "Add" button.
- For the wicket dialog: tapping "Add" calls `MatchViewModel.addPlayerToTeam(newPlayer, addToBattingTeam = true)` to register the player in the team, then immediately calls `selectNextBatter(newPlayer)` to resolve the pending action.
- For the bowler-change dialog: same pattern, but `addToBattingTeam = false`.
- No new pending-action variants were introduced; the existing `SelectNextBatter` and `SelectBowler` sealed classes are unchanged.

**Add players after match start:**
- `MatchViewModel.addPlayerToTeam(player, addToBattingTeam)` — new public method. Determines the correct batting/bowling team for the current innings, appends the player, and updates all `Match` references (teamA, teamB, battingFirst, bowlingFirst, tossWinner) atomically via the private `Match.updateTeamRef()` extension. Also persists via `MatchRepository.updateMatch()`.
- `ScoringScreen` — a "＋ Add player to team" `TextButton` is shown during `FIRST_INNINGS` and `SECOND_INNINGS`. Tapping it opens `AddPlayerToMatchDialog`, which lets the scorer type a name and pick which team (batting or bowling) via `FilterChip` selectors.
- `MatchDetailsScreen` — the `MatchActionButtons` section gains an optional "＋ Add Player to Team" `OutlinedButton` wired to `AddPlayerToTeamDialog`.

**Saved teams:**
- `SavedTeam` entity (`data/entity/SavedTeam.kt`) — a lightweight template holding an id, team name, and player list.
- `SavedTeamRepository` (`repository/SavedTeamRepository.kt`) — in-memory singleton with `addTeam`, `removeTeam`, `updateTeam`.
- `MatchSessionViewModel` — new `savedTeams: StateFlow<List<SavedTeam>>`, `addSavedTeam()`, `removeSavedTeam()`.
- `SavedTeamsScreen` — full-page list of saved teams with delete buttons. A "New Team" button opens `CreateSavedTeamDialog` (name field + dynamic player list, up to 11). Accessible via the navigation drawer.
- `CreateMatchScreen` — when saved teams exist, a compact "Saved" `OutlinedButton` appears next to each team name field. Tapping it opens `SavedTeamPickerDialog` (a dismissible alert listing saved team names with player count). Selecting a team pre-fills the team name field and copies its players into `teamAPlayers`/`teamBPlayers` local state. Players are deep-copied so the match remains independent of the saved team template.

**Architecture notes:**
- `ScoreReducer`, `MatchState`, `ScoreEvent` — completely unchanged.
- `ScoringConsoleState`, `PendingAction`, `InningsPhase` — completely unchanged.
- `CameraPreviewScreen`, Facebook Live streaming flow — completely unchanged.
- Create Match flow (3-screen wizard) — unchanged except for the optional saved-team picker added to `CreateMatchScreen`.
- No Room or backend integration was added in this phase.

---

### 2026-03-07 – Phase 5: Searchable Team Selector UX in CreateMatchScreen

**Feature:** Replace the two-mode (New Team / Use Saved Team) team selection flow in `CreateMatchScreen` with a unified, searchable editable-dropdown field for each team.

**Files modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/CreateMatchScreen.kt` | Replaced FilterChip mode-toggle + separate picker dialog with `TeamSelectorField`; simplified state; removed `SavedTeamChip` and `SavedTeamPickerDialog` composables |
| `README.md` | Added this log entry |

**New composable — `TeamSelectorField`:**
- A reusable `ExposedDropdownMenuBox`-based composable that combines an editable text field with a searchable saved-team dropdown.
- **Free typing:** the user can type any team name; if they never pick from the dropdown the name is used as-is for the match.
- **Dropdown filtering:** as the user types, saved teams are filtered case-insensitively; results appear immediately.
- **Select existing team:** tapping a team from the dropdown fills the name field and pre-populates the player list (players are deep-copied so the match stays independent of the saved-team template).
- **Create new team:** "＋ Create new team" is always shown at the bottom of the dropdown. Tapping it opens the existing `CreateSavedTeamDialog`, saves the new team to `SavedTeamRepository` via `MatchSessionViewModel.addSavedTeam()`, and auto-selects it (name + players) into the current team field.
- Reused for both Team A and Team B fields.

**State simplification in `CreateMatchScreen`:**
- Removed: `teamAUseSaved`, `teamBUseSaved`, `teamASelectedSaved`, `teamBSelectedSaved`, `saveTeamA`, `saveTeamB`, `showSavedTeamPickerForA`, `showSavedTeamPickerForB`.
- Kept: `teamAName`, `teamAPlayers`, `teamBName`, `teamBPlayers` — the same fields that fed the match creation before.
- `finalTeamAName`/`finalTeamBName` are now simply `teamAName.trim()` / `teamBName.trim()`.
- The "save team on proceed" checkbox is replaced by the in-dropdown create flow.

**Removed composables:**
- `SavedTeamChip` — no longer needed (dropdown handles post-selection display).
- `SavedTeamPickerDialog` — replaced by the inline dropdown in `TeamSelectorField`.

**What was improved:**
- Single field per team instead of two separate modes.
- Search-as-you-type filtering of saved teams.
- Creating a new saved team is one tap away from the same field.
- Free typing still works — PlayerSetupScreen and the rest of the match flow are unaffected.

---

### 2026-03-07 – Phase 4: Full Scorecard Screen

**Feature:** Add a proper cricket scorecard screen (`ScorecardScreen`) that displays a realistic batting and bowling summary for both innings.

**Files created:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/ScorecardFormatter.kt` | New — pure formatting helpers for SR, Economy, and overs display |

**Files modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/data/MatchState.kt` | Added `wides`, `noBalls`, `byes`, `legByes` fields |
| `app/src/main/java/com/example/scorebroadcaster/domain/ScoreReducer.kt` | Updated reducer to track per-extra-type tallies |
| `app/src/main/java/com/example/scorebroadcaster/data/ScoringConsoleState.kt` | Added `firstInningsOvers`, `firstInningsBalls`, and per-type extras snapshot fields |
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchViewModel.kt` | `endFirstInnings()` and `startSecondInnings()` now snapshot the new fields |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScorecardScreen.kt` | Full scorecard rewrite: SR column, Economy column, extras breakdown, overs in totals, LazyColumn, section labels |
| `app/src/main/java/com/example/scorebroadcaster/ui/AppShell.kt` | Added "Scorecard" entry to the navigation drawer |
| `README.md` | Updated feature table and added this log entry |

**Batting summary implementation:**
- Displays one row per batter who has faced a ball, ordered by batting position.
- Columns: **Batter** (name + dismissal string below), **R** (runs), **B** (balls faced), **4s**, **6s**, **SR** (strike rate as `runs × 100 / balls`, formatted to 1 d.p.).
- Dismissal string is generated by `DismissalDetail.toScorecardString()` which produces standard cricket notation (`b Bowler`, `c Fielder b Bowler`, `lbw b Bowler`, `st Keeper b Bowler`, `run out (Fielder)`).
- Not-out batters are labelled "not out" in the primary colour.
- Data comes directly from `ScoringConsoleState.allBattingEntries` (current innings) or `firstInningsBattingEntries` (first-innings snapshot).

**Bowling figures implementation:**
- Displays one row per bowler who has bowled, ordered by entry.
- Columns: **Bowler** (name), **O** (overs in `X.Y` notation), **R** (runs conceded), **W** (wickets), **Econ** (economy = `runs × 6 / totalBalls`, formatted to 2 d.p.).
- Data comes from `ScoringConsoleState.allBowlingEntries` (current innings) or `firstInningsBowlingEntries` (snapshot).

**Extras breakdown:**
- `MatchState` now tracks `wides`, `noBalls`, `byes`, and `legByes` separately, updated by `ScoreReducer` on each matching event.
- The scorecard displays: `Extras  X  (wd W, nb NB, b B, lb LB)`.
- First-innings values are snapshotted in `ScoringConsoleState` when `endFirstInnings()` is called.

**Totals row:**
- Displays `runs/wickets  (X.Y ov)` using `ScorecardFormatter.formatOvers()`.
- First-innings overs are snapshotted in `ScoringConsoleState` alongside the totals.

**Navigation:**
- Accessible from: **ScoringScreen** quick-nav bar → "Scorecard", **MatchDetailsScreen** → "View Scorecard" button, **navigation drawer** → "Scorecard" item (new).
- Route `scorecard` was already registered in `MainActivity`.

**Architecture:**
- No business logic inside composables: all formatting delegated to `ScorecardFormatter` (pure Kotlin object, no Android/Compose dependencies).
- `ScorecardScreen` reads only from `MatchViewModel` and `MatchSessionViewModel` state flows.
- `LazyColumn` used for scrolling so the screen handles arbitrarily long batting/bowling lists efficiently.

---

### 2026-03-06 – Phase 3: Match-Scoring Console

**Feature:** Turn `ScoringScreen` into a fully match-aware scoring console with batter/bowler tracking, innings management, and target/chase display.

**Files created:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/data/ScoringConsoleState.kt` | New — `InningsPhase` enum, `PendingAction` sealed class, `ScoringConsoleState` data class |

**Files modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchViewModel.kt` | Added `activeMatch`, `consoleState`, player/innings management methods |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Fully rewritten as match-scoring console |
| `app/src/main/java/com/example/scorebroadcaster/MainActivity.kt` | `initFromMatch` guard on "Resume Scoring" navigation |
| `README.md` | Updated feature table and added this log entry |

**What was added:**

**`ScoringConsoleState`** (`data/ScoringConsoleState.kt`) — a new data class that sits alongside `MatchState`. `MatchState` continues to hold raw cumulative totals produced by the pure `ScoreReducer`. `ScoringConsoleState` holds everything the scoring console needs on top: current innings phase (`SETUP` → `FIRST_INNINGS` → `SECOND_INNINGS` → `MATCH_COMPLETE`), striker/non-striker/bowler assignments with live `BattingEntry` and `BowlingEntry` stats, first-innings totals for target calculation, and a `pendingAction` slot for dialogs. `PendingAction` is a sealed class with two variants: `SelectNextBatter` (wicket fell) and `SelectBowler` (over ended).

**`MatchViewModel` extensions:**
- Stores the `Match` entity (via `initFromMatch`); exposes it as `activeMatch: StateFlow<Match?>`.
- Exposes `consoleState: StateFlow<ScoringConsoleState>`.
- `addEvent()` now preserves team names after each `reduce()` call and drives `updateConsoleAfterEvent()`, which updates live batter/bowler stats, detects over-end and wicket events, rotates strike correctly (including the double-rotation cancellation when odd runs are scored on the 6th ball), and sets `pendingAction`.
- New public methods: `setOpeners(striker, nonStriker, bowler)`, `selectNextBatter(player)`, `changeBowler(player)`, `endFirstInnings()`, `endMatch()`.
- `endFirstInnings()` saves first-innings totals, swaps batting/bowling teams, resets the event log for the second innings, and re-enters `SETUP` phase (or skips to `SECOND_INNINGS` if the teams have no players).

**`ScoringScreen` rewrite:**
- **Match header**: match title, format, overs limit, innings badge, batting/bowling team names.
- **Score display**: batting team name, runs/wickets in large type, overs.balls.
- **Chase panel** (2nd innings only): target, runs needed, balls remaining — all computed locally from `MatchState` + match overs limit.
- **Last 6 balls row**: coloured chips (red = wicket, amber = wide/no-ball, primary = runs).
- **Players card**: striker name* with runs(balls) + 4s/6s, non-striker, current bowler with overs–runs–wickets.
- **Scoring buttons**: 0–6 run buttons, W (red), Wd+1, NB+1, Bye, LB, Undo — disabled while a dialog is pending.
- **Innings controls**: "End 1st Innings" button during first innings; "End Match" button during second innings.
- **Match-complete banner**: result string (won by wickets / won by runs) and both innings totals.
- **Setup dialog**: `SetupOpenersDialog` — `ExposedDropdownMenuBox` pickers for striker, non-striker, opening bowler; shown automatically at the start of each innings when teams have players.
- **Player-selection dialogs**: `SelectPlayerDialog` — non-dismissible list for mid-over actions.

**Architecture notes:**
- The existing `ScoreReducer` is completely unchanged.
- `MatchState` is completely unchanged.
- `ScoreEvent` is completely unchanged.
- `ScoringConsoleState` is a separate state slice managed directly in `MatchViewModel`; no new ViewModel was introduced.
- `CameraPreviewScreen` and the Facebook Live streaming flow are completely unchanged.

---

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

