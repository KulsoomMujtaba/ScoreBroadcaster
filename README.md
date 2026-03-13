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
| Undo last ball | ✅ Done | `ScoringScreen` |
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
| Reusable player profiles (My Players — private) | ✅ Done | `MyPlayersScreen`, `PlayerProfile`, `SavedPlayerRepository` |
| Player picker (search-first, saved players, quick create, one-tap selection) | ✅ Done | `PlayerPickerDialog` |
| Player picker integrated into team setup | ✅ Done | `PlayerSetupScreen`, `SavedTeamsScreen` |
| Player picker integrated into match-time flows | ✅ Done | `ScoringScreen` |
| New player auto-saved as private profile in match flows | ✅ Done | `ScoringScreen`, `MatchSessionViewModel` |
| Scorecard view (batting + bowling summary, both innings) | ✅ Done | `ScorecardScreen` |
| Ball timeline / over history (per-ball, grouped by over) | ✅ Done | `BallTimelineScreen` |
| Run rate display (CRR always; RRR during chase) | ✅ Done | `ScoringScreen` |
| Recent-ball chip colours (W/4/6/extras visually distinct) | ✅ Done | `ScoringScreen` |
| Domain entities (Team, Player, Match, …) | ✅ Done | `data/entity/` |
| Publish-ready match model (MatchVisibility, ownerUserId, remoteId, shareCode) | ✅ Done | `data/entity/Match`, `data/entity/MatchVisibility` |
| Local in-memory repository | ✅ Done | `repository/MatchRepository` |
| Match session management | ✅ Done | `MatchSessionViewModel` |

---

## UX Improvements: Team Selection, Scoring Buttons, and Bowler Change Flow

### What changed

**Create Match — Team Selection (Part 1)**

- **Team A and Team B fields are now selection-only**: the `TeamSelectorField` composable no longer accepts free-text keyboard input.
- The field is rendered as a read-only tap-to-open selector (non-editable `OutlinedTextField` with `MenuAnchorType.PrimaryNotEditable`).
- Tapping the field opens a dropdown that lists all saved teams (excluding the team already selected on the other side).
- A **"+ Create new team"** option at the bottom of the dropdown opens the existing `CreateSavedTeamDialog`, saves the new team, and auto-selects it in the field.
- Text-based filtering of the team list has been removed (no typing = no need to filter by input).
- The same-team error validation, swap-teams button, and overall form logic are unchanged.

**Scoring Screen — Undo Button Visibility (Part 2)**

- **Undo button re-styled** from an `OutlinedButton` (which had near-invisible white text on a white/surface background due to `onSecondaryContainer = white` in the blue theme) to a **filled `Button`** using `secondary`/`onSecondary` colours.
- Result: clearly visible medium-blue filled button that is secondary in weight (not as prominent as the red Wicket button) but fully readable.

**Scoring Screen — Boundary Button Distinction (Part 3)**

- **4 button** now renders its label with `FontWeight.Bold`, visually reinforcing it as a boundary hit.
- **6 button** now renders its label with `FontWeight.ExtraBold`, giving it the strongest visual weight of the run buttons — a maximum-boundary feel without changing colours or layout.
- The existing custom container colours (`BoundaryFourContainer` / `BoundarySixContainer`) are retained; only text weight is added.

**Scoring Screen — Non-Blocking Bowler Change Flow (Part 4)**

- **After an over ends**, the "Select Bowler" prompt is now shown as a **dismissible `ModalBottomSheet`** instead of a blocking `AlertDialog`.
- If the user dismisses the sheet, they are free to navigate elsewhere in the app (other tabs, back to Home, etc.).
- **Scoring remains gated**: the run/wicket/extras buttons stay disabled until a bowler is chosen (`pendingAction == SelectBowler` keeps `scoringEnabled = false`).
- When the sheet is dismissed, an **inline "Next bowler required" card** appears on the Score tab showing:
  - Title: "Next bowler required"
  - Subtitle: "Select the bowler for the new over before continuing"
  - CTA button: "Select Bowler" (re-opens the bottom sheet)
- The card uses `secondaryContainer` / `onSecondaryContainer` colours — distinct from the red innings-setup banner but clearly visible.
- When the user returns to the screen after navigating away, the banner is still shown (pending bowler state lives in the ViewModel).
- Once a bowler is selected the pending action clears, the banner disappears, and scoring resumes normally.

### What did NOT change

- `ScoreReducer`, `BallEvent`, match scoring rules — untouched.
- Undo logic itself — untouched.
- Wicket logic, `SelectNextBatter` dialog — untouched.
- All other screens — untouched.

### Files changed

| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/CreateMatchScreen.kt` | Updated – `TeamSelectorField` made read-only/non-editable; removed `onTeamNameChange` parameter and text filtering |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Updated – Undo button filled; 4/6 label weights; `SelectBowlerBottomSheet` added; inline "Next bowler required" banner added |
| `README.md` | Updated – this development log entry |

---

## UX Improvement: Add Players Dialog

### What changed

- **MultiPlayerPickerSheet** redesigned as a full-screen player selection tool:
  - **Search bar** at the top (`OutlinedTextField` with "Search players" placeholder) filters the list instantly as the user types.
  - **Selected players summary** shows a count label ("Selected: N players") and a horizontally scrollable row of removable `InputChip` components — tapping a chip deselects that player.
  - **Players list** uses `LazyColumn` with 8 dp row spacing; each row is touch-friendly (min 48 dp height), full-width, checkbox on the left, and player name rendered in `FontWeight.Medium`.
  - **Add New Player section** (renamed from "Create new player") placed at the bottom behind a divider; the action button is labelled "Add Player". New players appear immediately in the list and are auto-selected.
  - **Sticky confirm button** shows dynamic text: "Add N Player(s)"; always visible at the bottom of the dialog.
  - **Empty state** message updated to: "No saved players yet. Create a player below to start building your team."
  - **Team size limit** helper text updated to: "Maximum N players per team."

### What did NOT change

- `MatchViewModel`, `ScoreReducer`, `BallEvent`, or any Room entities — untouched.
- All other screens — untouched.

---

## UI Improvement: Cricinfo-Inspired Theme Refresh

### What changed

- **Refreshed app colour system** with a cricket-green brand identity:
  - Primary Green: `#008F5A`, Primary Dark Green: `#006C44`, Primary Light Green: `#DDF4EA`
  - Background: `#F7F9F8` (very light neutral), Surfaces: white (`#FFFFFF`)
  - Surface Variant: `#EEF3F0`, Outline: `#D9E2DD`
  - Text Primary: `#0F1720`, Text Secondary: `#5B6871`
  - Error / Wicket: `#C83A3A` (strong red, unchanged intent)

- **Cleaner white surfaces and darker text hierarchy** — replaced purple/pink Material defaults with cricket-native greens and neutral text.

- **Reduced inconsistent blue styling** — disabled dynamic colour so the green brand identity is always applied consistently.

- **Updated tabs, navigation, scoring controls, chips, and forms**:
  - Selected tab indicator, primary buttons, and active states now use brand green.
  - Bottom navigation selected item uses primary green; unselected items are muted grey.
  - Top app bar uses white surface with dark title text.

- **Boundary ball chips now semantically distinct**:
  - **4**: light-green container (`#CEF0DF` / dark text) — subtly highlighted.
  - **6**: dark-green container (`#0E6B43` / white text) — stronger emphasis.
  - **W**: red (`errorContainer`) — clearly destructive.
  - **Extras (Wd/NB)**: warm amber container (`#FFE8B2`) — visible but restrained.
  - Applies in both the recent-ball chips row (`ScoringScreen`) and the ball-timeline chip grid (`BallTimelineScreen`).

- **Chase / target info panels** use `primaryContainer` (light green) instead of tertiary amber, giving a cleaner cricket-native look during run chases.

- **App now has a more cricket-native, stats-first visual feel** — less generic Material demo, less blue-heavy, more like a polished cricket utility app.

### What did NOT change

- `MatchViewModel`, `ScoreReducer`, `BallEvent`, Room entities — untouched.
- Navigation behaviour — untouched.
- All scoring logic — untouched.

---

## UI Improvement: Run Rate Display + Recent Ball Chip Colours

### What changed

- **ScoringScreen** now shows a compact run rate row in the `CompactMatchHeader` during active innings:
  - **First innings**: Current Run Rate (CRR) is always displayed.
  - **Second innings**: Both Current Run Rate and Required Run Rate (RRR) are shown side-by-side.
  - The row is hidden during Innings Break and Match Complete phases.

- **Run rate formulas**:
  - CRR = `runs × 6 / totalBallsBowled` (same cricket-over logic as bowling economy).
  - RRR = `runsNeeded × 6 / ballsRemaining`. Returns "-" when no balls remain or target already reached.
  - Both rounded to 2 decimal places. Display: `CRR 8.42  •  RRR 9.75`.

- **Recent-ball chips** (`LastBallsRow`) colour treatment updated:
  - `W` → `error` / `onError` (kept, strongest alert)
  - `4` → `secondaryContainer` / `onSecondaryContainer`
  - `6` → `tertiaryContainer` / `onTertiaryContainer`
  - `Wd` / `NB` → `tertiary` / `onTertiary`
  - All other balls (`.`, singles, etc.) → `surfaceVariant` / `onSurfaceVariant`
  - No hard-coded colours; all use Material3 theme tokens only.

- **ScorecardFormatter** extended with two new pure helpers:
  - `formatRunRate(runs, overs, balls)` – returns CRR as a 2 dp string.
  - `formatRequiredRunRate(runsNeeded, ballsRemaining)` – returns RRR as a 2 dp string.

### What did NOT change

- `ScoreReducer`, `MatchState` schema, event log logic — untouched.
- Wicket / extras behaviour — untouched.
- All other screens — untouched.

### Files changed

| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Updated – `RunRateRow` composable added, called from `CompactMatchHeader`; `LastBallsRow` chip colours revised |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScorecardFormatter.kt` | Updated – `formatRunRate` and `formatRequiredRunRate` helpers added |
| `README.md` | Updated – this development log entry |

---

## Phase 2: Immersive Broadcast Preview

### What changed

- **CameraPreviewScreen** refactored into an immersive broadcast monitor:
  - Scoring controls panel removed; scoring is only available in `ScoringScreen`.
  - Screen locked to landscape orientation while open; orientation is restored on exit.
  - All app chrome (top bar, bottom navigation, drawer) hidden for a true full-screen experience.
  - Close (×) button added in the top-right corner inside the system safe area — circular, semi-transparent black background.

- **StreamPreviewScreen** refactored to match:
  - Screen locked to landscape orientation while open; orientation is restored on exit.
  - All app chrome hidden.
  - Close (×) button added in the top-right corner (LIVE badge remains top-left).

- **AppShell** updated:
  - Introduces an `immersiveRoutes` set (`live_preview`, `stream_preview`).
  - When the current route is immersive the entire scaffold (top bar, bottom bar, drawer) is bypassed and the content fills the screen with zero inset padding.
  - All other routes are completely unaffected.

- **MainActivity** updated:
  - Passes `onBack = { navController.popBackStack() }` to both preview screens so the close button correctly pops the back stack and triggers camera/stream cleanup.

### What did NOT change

- Scoring engine, `MatchViewModel`, `LiveStreamViewModel` — untouched.
- All other screens and their navigation routes — untouched.
- RTMP streaming pipeline (`RtmpLiveStreamer`, `ScoreboardOverlayRenderer`) — untouched.

### Files changed in Phase 2

| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/CameraPreviewScreen.kt` | Updated – removed scoring controls, added close button, added landscape lock |
| `app/src/main/java/com/example/scorebroadcaster/ui/StreamPreviewScreen.kt` | Updated – added close button, added landscape lock |
| `app/src/main/java/com/example/scorebroadcaster/ui/AppShell.kt` | Updated – hide top/bottom bars for immersive routes |
| `app/src/main/java/com/example/scorebroadcaster/MainActivity.kt` | Updated – pass `onBack` to preview screens |
| `README.md` | Updated – Phase 2 development log |

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
│       ├── MatchVisibility.kt # PRIVATE, PUBLISHED, UNLISTED (publish-ready)
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
│   ├── SavedTeamsScreen.kt        ← Phase 4 + Player Picker: PlayerPickerDialog integrated
│   ├── PlayerPickerDialog.kt      ← Player Picker: new reusable picker composable
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

### 2026-03-13 – UI Improvement: Scoring Pad Button Styling and Overthrows Grouping

- Updated 0, 1, 2, 3 run buttons to use a lighter neutral style (`NormalRunContainer` — soft blue-grey `#DEE8F7` with dark navy text).
- Preserved stronger visual emphasis for 4 (accent blue) and 6 (deep navy), keeping boundaries clearly distinct from normal runs.
- Moved Overthrows from the Runs section into the Extras section, grouping it with Wide, No Ball, Bye, and Leg Bye for improved scoring-pad scanability.
- Overthrows dialog/flow is unchanged — only the UI entry point moved.

**Files changed:**

| File | Change |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/theme/Color.kt` | Added `NormalRunContainer` and `OnNormalRunContainer` colour tokens |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Removed Overthrows from `RunButtonsGrid`; added it to `ExtrasButtonsGrid`; applied neutral style to 0–3 buttons |
| `README.md` | Added this Development Log entry |

---

### 2026-03-12 – Player Flow Refactor: Team-First Structure with My Players

**Feature:** Renamed "Saved Players" to "My Players" and introduced team-first player management.

- **Renamed Saved Players to My Players**: updated all navigation labels, screen titles, and UI text throughout the app. The side drawer now shows "My Players". The top bar title for the screen now reads "My Players".
- **Introduced team-first player management**: when creating or editing a team, players are managed primarily within the team roster. The `MultiPlayerPickerSheet` now shows a clear "My Players" section label for the reusable player directory.
- **Added optional saving of players to My Players**: when creating a new player inside any team creation flow (`PlayerSetupScreen`, `CreateSavedTeamDialog`), a checkbox labelled "Save to My Players" is shown (default: unchecked). Players are only added to the My Players directory when the checkbox is checked; otherwise the player exists only in the team roster.
- **Prevented players from appearing on both teams in the same match**: cross-team conflict detection was already implemented; this refactor preserves that behaviour.
- **Simplified team creation UX**: "Add Players" button in the Create Saved Team dialog is now labelled "Add from My Players" to make the source explicit. The Create New Player section in `MultiPlayerPickerSheet` is labelled "Create New Player" (was "Add New Player").

**Files created/modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/MyPlayersScreen.kt` | Created — `MyPlayersScreen` composable (renamed from `SavedPlayersScreen`) with updated UI text |
| `app/src/main/java/com/example/scorebroadcaster/ui/SavedPlayersScreen.kt` | Gutted — composables moved to `MyPlayersScreen.kt` |
| `app/src/main/java/com/example/scorebroadcaster/ui/AppShell.kt` | Updated — drawer label and top bar title changed to "My Players" |
| `app/src/main/java/com/example/scorebroadcaster/ui/MultiPlayerPickerSheet.kt` | Updated — added "My Players" section label, "Create New Player" label, "Save to My Players" checkbox, updated empty state text |
| `app/src/main/java/com/example/scorebroadcaster/ui/PlayerSetupScreen.kt` | Updated — `onCreatePlayer` lambda now respects the `saveToMyPlayers` flag |
| `app/src/main/java/com/example/scorebroadcaster/ui/SavedTeamsScreen.kt` | Updated — "Add from My Players" button label; `onCreatePlayer` lambda now respects the `saveToMyPlayers` flag |
| `app/src/main/java/com/example/scorebroadcaster/ui/PlayerPickerDialog.kt` | Updated — section label and subtitle changed to "My Players" |
| `app/src/main/java/com/example/scorebroadcaster/MainActivity.kt` | Updated — uses `MyPlayersScreen` instead of `SavedPlayersScreen` |
| `README.md` | Updated |

---

### 2026-03-12 – UI Improvement: Blue Sports Theme

- **Replaced green brand palette with modern cricket-style blue theme**: primary colour changed from `#008F5A` green to `#1E5EFF` blue across all Material3 colorScheme roles.
- **Updated tabs, navigation, scoring chips and forms**: selected tabs, active buttons, selected chips, and important highlights now use the primary blue.
- **Improved readability and sports-app feel**: cleaner light background (`#F6F8FB`), white surfaces, and high-contrast dark text (`#0F172A`) for outdoor readability during live matches.
- **Boundary chips updated**: FOUR chip uses primary blue (`#1E5EFF`), SIX chip uses darker blue (`#0B3FB3`), extras remain warm amber (`#F59E0B`), wickets remain red (`#D32F2F`).
- **Background stays light**: app background is a very light blue tone; the entire app is not blue — only interactive/highlighted elements use blue.

**Files modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/theme/Color.kt` | Replaced green palette with blue palette (`CricketBlue`, `CricketDarkBlue`, `CricketLightBlue`, updated boundary and extras colours) |
| `app/src/main/java/com/example/scorebroadcaster/ui/theme/Theme.kt` | Updated Material3 colorScheme roles to map to new blue palette |
| `README.md` | Updated |

---

### 2026-03-12 – UI Improvement: Compact Yet-to-Bat Display

- **Converted Yet-to-Bat player list from vertical layout to comma-separated line**: `YetToBatSection` now renders all players as a single `joinToString(", ")` text instead of one `Text` per player.
- **Reduced scorecard vertical height**: the section now occupies one or two lines instead of up to nine, keeping the scorecard compact.
- **Improved readability and alignment with real cricket scorecards**: names wrap automatically if they exceed screen width; the list is never truncated.
- **"YET TO BAT" label uses `FontWeight.SemiBold`**: slightly bolder label for visual hierarchy without overpowering the section.

**Files modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/ScorecardScreen.kt` | Replaced per-player `Column` with a single comma-separated `Text`; updated label weight and spacing |
| `README.md` | Updated |

---

### 2026-03-12 – Scorecard Improvement: Yet To Bat

- **Scorecard now shows players who have not yet batted**: a "Yet To Bat" section appears below the batting table in each innings.
- **Yet To Bat section appears below batting table**: the section is shown only when there are players remaining who have not yet come in to bat.
- **Players derived from team roster minus batting entries**: `deriveYetToBatPlayers` filters the batting team's player list by excluding any player who already has a `BattingEntry`, preserving original team order.

**Files modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/ScorecardScreen.kt` | Added `deriveYetToBatPlayers` helper, `YetToBatSection` composable, and wired team players into `InningsScorecardSection` |
| `README.md` | Updated |

---

### 2026-03-12 – UX Improvement: Bulk Team Player Selection

- **Removed one-by-one player entry from team creation flow**: `CreateSavedTeamDialog` and `PlayerSetupScreen` no longer show repeated text-field rows for individual player slots.
- **Team creation now uses a dedicated Add Players flow**: a single **Add Players** button opens `MultiPlayerPickerSheet` — the existing full-screen bulk-selection screen.
- **Users can search and multi-select saved players at once**: the picker supports instant search, checkbox-based multi-selection with ordered results, and a hard cap of 11 players per team.
- **New players can be created inline and are immediately added and auto-selected**: typing a name in the "Create new player" field within the picker persists the new `PlayerProfile` and auto-selects it without closing the sheet.
- **Selected players shown as a compact list with remove icons**: after confirming the picker, each team's section shows the selected player names with a count and individual remove buttons.
- **Cross-team conflict prevention preserved**: `PlayerSetupScreen` continues to exclude the opposing team's players from the picker so the same player cannot be assigned to both teams in one match.
- **Faster and cleaner team-building experience**: the old repeated-input UX is gone; the only path is Add Players → select / create → confirm.

**Files modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/SavedTeamsScreen.kt` | Refactored `CreateSavedTeamDialog` to use bulk picker |
| `app/src/main/java/com/example/scorebroadcaster/ui/PlayerSetupScreen.kt` | Replaced `PlayerListEditor` with `TeamPlayerSection` + bulk picker |
| `README.md` | Updated |

---

### 2026-03-12 – UI + Scoring Improvement: Bowler Figures Cleanup and Overthrows

- **Bowler figures now use standard cricket notation**: `Overs-Maidens-Runs-Wickets` (e.g. `3.2-1-18-2`). The previous non-standard `w` suffix after wickets has been removed.
- **Cleaner overs display**: `ScorecardFormatter.formatOvers` now omits the decimal part when there are zero partial balls (e.g. `4` instead of `4.0`). Partial overs continue to show as `2.3`.
- **Removed the bowler icon** (⚾) from the scoring screen's player panel — the bowler name and figures are now shown without a prefix icon.
- **Overthrow handling for normal runs**: a new **Overthrows** button in the Runs section opens an `OverthrowRunDialog` where the scorer specifies base runs off bat and additional overthrow runs. The final `BallEvent` has `runsOffBat = baseRuns + overthrowRuns`.
- **Overthrow handling for Bye / Leg Bye**: the `ExtrasEntryDialog` now includes an "Overthrows happened" toggle (only shown for Bye and Leg Bye types). When enabled, the scorer picks additional overthrow runs which are folded into the byes/legByes total: `byes = baseByeRuns + overthrowRuns`.

**Files modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | BowlerRow icon/format fix; overthrow dialog and UI |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScorecardFormatter.kt` | `formatOvers` updated for clean cricket notation |
| `app/src/test/java/com/example/scorebroadcaster/ScorecardFormatterTest.kt` | Added `formatOvers` tests |
| `README.md` | Updated |

---

### 2026-03-12 – UX Improvement: Bulk Player Selection

- **Multi-select player picker**: new `MultiPlayerPickerSheet` composable provides a full-screen picker for bulk team-building flows.
- **Search and select multiple players**: users can search saved players and select many at once with checkboxes; selection is preserved while searching.
- **Inline player creation**: a "Create new player" form at the bottom lets users create and immediately select new players without leaving the picker.
- **Ordered selection**: selected players are added to the team in the order they were tapped.
- **Team size enforcement**: selection is capped at the remaining available slots (max 11 per team); further selection is disabled once the limit is reached with a helper text shown.
- **Empty state**: when no saved players exist, a friendly empty-state message guides the user to create players instead.
- **Duplicate prevention**: players already assigned to the team are excluded from the picker list.
- **"Pick from saved players" button**: added to `PlayerSetupScreen` (for Team A and Team B) and `CreateSavedTeamDialog` in `SavedTeamsScreen`.
- **Single-player picker unchanged**: `PlayerPickerDialog` and all single-select flows (next batter, bowler change, innings setup) are unaffected.

**Files created/modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/MultiPlayerPickerSheet.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/ui/PlayerSetupScreen.kt` | Updated |
| `app/src/main/java/com/example/scorebroadcaster/ui/SavedTeamsScreen.kt` | Updated |
| `README.md` | Updated |

---

### 2026-03-11 – UX Improvement: Add Player Dialog

- **Search-first player picker**: `PlayerPickerDialog` redesigned with a clean three-section layout — search field at the top, scrollable saved-player list in the middle, quick-create form at the bottom.
- **One-tap player selection**: saved-player rows are now full-width clickable surfaces (minimum 48 dp) with a person icon and an optional "Saved player" label — tapping instantly selects and closes the dialog.
- **Cleaner quick-create flow**: the create-new section uses a full-width `OutlinedTextField` and a full-width "Add Player" button; pressing Enter also submits.
- **Smart empty state**: when there are no eligible saved players the search field and list are hidden; only the "Create new player" section is shown.
- **Removed clutter**: the "Scored Users · coming soon" placeholder section has been removed; title updated from "Pick Player" to "Add Player".
- **Reused across all entry points**: `PlayerPickerDialog` is now the single Add Player surface used in `PlayerSetupScreen`, `SavedTeamsScreen`, `AddPlayerToMatchDialog`, next-batter / next-bowler (`SelectPlayerDialog`), and the openers setup bottom sheet (`SetupOpenersBottomSheet`).
- **Fewer taps in innings setup**: tapping "+ Add Batter" or "+ Add Bowler" now opens `PlayerPickerDialog` directly — the intermediate `AddPlayerChoiceDialog` step has been removed.
- **Simplified `SelectPlayerDialog`**: the inline "Add new player" text-field form has been removed; the consolidated "Add Player" button opens `PlayerPickerDialog` which covers both pick and create.
- **Simplified `AddPlayerToMatchDialog`**: the inline create form and separate "Pick from saved players" button have been replaced with a team-selector and a single "Pick / Add Player" button that opens `PlayerPickerDialog`.



- BallTimeline now shows compact over summaries for each completed and in-progress over.
- Each over card displays: over number, bowler name, ball-by-ball label sequence, and total runs or "Maiden".
- Maiden overs are automatically detected (zero runs conceded by the bowler, excluding byes and leg-byes).
- Data is derived from the BallEvent history, ensuring correctness after undo, ball edits, and ball deletions.
- Works for both innings.

**Files created/modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/domain/BallTimelineFormatter.kt` | Updated – `OverSummary` extended with `bowlerName`, `runsInOver`, and `isMaiden` fields; `groupByOver` computes these fields |
| `app/src/main/java/com/example/scorebroadcaster/domain/OverSummaryCalculator.kt` | Created – `deriveOverSummaries(events)` wraps `BallTimelineFormatter.groupByOver`; `ballLabel(event)` formats compact delivery labels ("0", "W", "Wd", "Nb", "B1", "Lb1") |
| `app/src/main/java/com/example/scorebroadcaster/data/ScoringConsoleState.kt` | Updated – added `firstInningsOverSummaries` and `secondInningsOverSummaries` fields |
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchViewModel.kt` | Updated – `refreshCurrentInningsOverSummaries()` helper keeps summaries in sync after every ball event mutation; over summaries populated in all `resumePersistedState` branches |
| `app/src/main/java/com/example/scorebroadcaster/ui/BallTimelineScreen.kt` | Updated – `OverCard` now shows bowler name in header, compact ball-label sequence, interactive ball chips, and runs/maiden footer |
| `README.md` | Updated |

---

### 2026-03-11 – Feature: Fall of Wickets Tracking

- Each wicket is now recorded as a Fall of Wicket entry.
- FoW entries store wicket number, team score at fall, dismissed batter, and over.
- FoW is shown in the scorecard for both innings beneath the bowling summary.
- FoW stays correct after undo, ball edits, and ball deletes because it is rebuilt from replayable innings state.

**Files created/modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/data/ScoringConsoleState.kt` | Updated – added `currentInningsFallOfWickets` and `firstInningsFallOfWickets` fields |
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchViewModel.kt` | Updated – FoW lists kept in sync with `_consoleState` after every ball event mutation; snapshotted to `firstInningsFallOfWickets` at innings end; restored correctly on resume |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScorecardScreen.kt` | Updated – `InningsScorecardSection` now accepts and renders a `fallOfWickets` list; `FallOfWicketsSection` composable added |
| `README.md` | Updated |

---

### 2026-03-11 – Feature: Live Partnership Tracking

- The scoring engine now tracks the current batting partnership.
- Partnership runs include all runs scored while the pair is batting (bat + extras).
- Partnership balls count only legal deliveries (`countsAsBall == true`).
- The partnership resets when a wicket falls or a new innings begins.
- The current partnership is displayed on the Scoring screen under the batter stats.

**Files created/modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/data/ScoringConsoleState.kt` | Updated – added `currentPartnershipRuns`, `currentPartnershipBalls`, and `partnershipOversText` |
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchViewModel.kt` | Updated – `updateConsoleAfterEvent` now updates partnership fields; reset in `setOpeners` and `selectNextBatter` |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Updated – `PlayersSection` shows "Partnership: runs (balls)" below the batter rows |
| `README.md` | Updated |

---

### 2026-03-10 – Cross-team player exclusivity in match setup

Player cannot belong to both teams in the same match.

During team setup, a player already assigned to one side is now excluded from the other side's picker and cannot be added manually to both teams. Identity is resolved using `sourceProfileId` when available, with normalized-name fallback for ad-hoc players. Inline validation is shown and Continue remains disabled until cross-team conflicts are resolved.

**Files created/modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/PlayerIdentityHelper.kt` | Created – `normalizePlayerName`, `Player.sameIdentityAs`, `hasCrossTeamDuplicate`, `crossTeamConflicts` helpers |
| `app/src/main/java/com/example/scorebroadcaster/ui/PlayerSetupScreen.kt` | Updated – derived cross-team exclusion sets, per-slot conflict flags, inline error text, `enabled = !hasCrossTeamConflict` on Continue |
| `app/src/main/java/com/example/scorebroadcaster/ui/PlayerPickerDialog.kt` | Updated – `excludedProfileIds` / `excludedNames` params; filters saved-player list; blocks "create new" for conflicting names |
| `README.md` | Updated |

**Architecture:**

*`PlayerIdentityHelper.kt`* — thin helper file next to the UI layer. Contains:
- `normalizePlayerName(name)` — trims and lowercases for ad-hoc name comparison.
- `Player.sameIdentityAs(other)` — prefers `sourceProfileId` equality; falls back to normalized name.
- `hasCrossTeamDuplicate(teamA, teamB)` — returns `true` if any overlap exists.
- `crossTeamConflicts(teamA, teamB)` — returns the set of conflicting `Player` entries from Team A's perspective.

*`PlayerSetupScreen`* — maintains `derivedStateOf` blocks that compute `excludedForA_ProfileIds`, `excludedForA_Names`, `excludedForB_ProfileIds`, `excludedForB_Names`, `teamAConflicts`, `teamBConflicts`, and `hasCrossTeamConflict` from the live local roster state. The Continue button has `enabled = !hasCrossTeamConflict`. A section-level error is shown below the button while the conflict persists. Per-slot `isError` styling and inline error text appear on each conflicting field.

*`PlayerPickerDialog`* — two new optional parameters (`excludedProfileIds: Set<String>`, `excludedNames: Set<String>`) filter the visible saved-player list. If all saved players are excluded, a muted info row ("No eligible players available — already assigned to the other team.") is shown. The "Create new player" inline field has its button disabled and shows an inline error when the typed name matches an excluded ad-hoc name.

### 2026-03-10 – Bug Fix: Striker / non-striker can now be swapped correctly in innings setup

Previously, when only two batting players existed and both were already selected, the dropdown filtering logic prevented swapping them unless both fields were manually cleared. The innings setup UI now supports direct swapping in two ways:
- selecting the opposite batter automatically swaps the two selections
- a new "⇅ Swap batters" action allows one-tap reversal

Duplicate batter selection is still prevented, but the scorer no longer needs to clear both fields to switch ends.

**Files changed:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Updated – striker/non-striker dropdowns now show the full batting roster; auto-swap logic added to selection handlers; "⇅ Swap batters" button added between the two selectors |
| `README.md` | Updated |

---

### 2026-03-10 – Bug Fix: Resume Match incorrectly reopening innings setup

**Files changed:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/domain/BallEvent.kt` | Updated – added `striker` and `nonStriker` fields so the live batting state is persisted with every delivery |
| `app/src/main/java/com/example/scorebroadcaster/data/local/BallEventEntity.kt` | Updated – added `eventStrikerName`, `eventStrikerSourceProfileId`, `eventNonStrikerName`, `eventNonStrikerSourceProfileId` columns; updated `toDomain()` and `toEntity()` mappers |
| `app/src/main/java/com/example/scorebroadcaster/data/local/ScoredDatabase.kt` | Updated – bumped schema version to 6 |
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchViewModel.kt` | Updated – `addBallEvent()` stamps striker/nonStriker onto each event; added `deriveCurrentBatters()` helper; `resumePersistedState()` now restores striker, nonStriker, batting entries, and bowling entries from stamped events |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Updated – `LaunchedEffect` now also dismisses the setup dialog when `needsInningsSetup` transitions to false (secondary guard for async restore) |
| `README.md` | Updated |

**Explanation:**

Resuming an already-started match after app restart now restores the live innings state correctly. The innings setup popup is only shown when setup is genuinely required, not for matches already in progress.

**Root cause:** `resumePersistedState()` could reconstruct the current bowler from stored events (via `BallEvent.bowler`) but had no way to reconstruct the striker and non-striker, because `BallEvent` did not carry batter information. As a result, both positions were always null after an app restart, which caused `needsInningsSetup` in `ScoringScreen` to evaluate to `true` and re-open the innings-setup bottom sheet even when the innings was already underway.

**Fix — primary (ViewModel):**
- Added `striker: Player?` and `nonStriker: Player?` fields to `BallEvent`. `addBallEvent()` stamps the current console state's striker and non-striker onto every delivery before persisting it, exactly as the bowler field is already stamped.
- Added `deriveCurrentBatters(events)` — a pure helper that locates the last stamped event, then applies the same over-end rotation and wicket-replacement logic used by `updateConsoleAfterEvent` to compute the post-delivery batting positions.
- `resumePersistedState()` calls `deriveCurrentBatters()` for both the first-innings and second-innings in-progress paths and populates `striker`, `nonStriker`, `strikerEntry`, `nonStrikerEntry`, `allBattingEntries`, `currentBowlerEntry`, and `allBowlingEntries` in the restored `ScoringConsoleState`. Events recorded before this change (null striker fields) fall back gracefully: the derived positions are null, `needsInningsSetup` remains true, and the setup dialog is shown as before.

**Fix — secondary (UI guard):**
- The `LaunchedEffect(needsInningsSetup)` in `ScoringScreen` now resets `setupDialogVisible` to `false` when `needsInningsSetup` transitions to false. This ensures that even if the dialog was transiently opened during the brief async window between `initFromMatch()` and `resumePersistedState()` completing, it is automatically dismissed once the ViewModel finishes restoring the full live state.

---

### 2026-03-10 – Bug Fix: Target Reached Condition

**Files changed:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchViewModel.kt` | Updated – win-condition check added at end of `updateConsoleAfterEvent`; `endMatch()` now also clears `pendingAction` |
| `README.md` | Updated |

**Explanation:**
When the chasing team reaches or exceeds the target in the second innings, the match now automatically ends and the chasing team is declared the winner. A win-condition check was added at the end of `updateConsoleAfterEvent` in `MatchViewModel`: if the current phase is `SECOND_INNINGS` and `newState.runs >= console.target`, `endMatch()` is called immediately. `endMatch()` was also updated to clear `pendingAction` so that any pending wicket/bowler dialogs are dismissed at the moment the match ends. Scoring buttons are disabled automatically because `scoringEnabled` requires `phase == SECOND_INNINGS`, which is no longer true once `MATCH_COMPLETE` is set.

---

### 2026-03-10 – UI Improvement: Broadcast Overlay Spacing Reduction and Portrait Orientation Fix

**Files changed:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoreboardOverlay.kt` | Updated – reduced vertical spacing between overlay rows; fixed orientation detection to use `Configuration.ORIENTATION_PORTRAIT` |
| `app/src/main/java/com/example/scorebroadcaster/streaming/ScoreboardOverlayRenderer.kt` | Updated – reduced vertical offsets for second row in each section for tighter layout |
| `app/src/main/java/com/example/scorebroadcaster/ui/CameraPreviewScreen.kt` | Updated – removed forced landscape orientation lock so the overlay can switch between portrait and landscape |
| `app/src/main/java/com/example/scorebroadcaster/ui/StreamPreviewScreen.kt` | Updated – removed forced landscape orientation lock so the overlay can switch between portrait and landscape |
| `README.md` | Updated – added this development log entry |

**Explanation:**

- **Reduced vertical spacing between overlay rows to further reduce overlay height.** In the Compose overlay (`ScoreboardOverlay.kt`), the outer Row's vertical padding was reduced from 4 dp to 2 dp, and the `BowlerSection` Column's `verticalArrangement` was tightened from `spacedBy(2.dp)` to `spacedBy(1.dp)`. In the Canvas renderer (`ScoreboardOverlayRenderer.kt`), the top padding in `drawBattersSection` was reduced from 4 px to 2 px and the row height formula updated accordingly; the centre section's run-rate line was moved from `totalH × 0.82` to `totalH × 0.78`; and the bowler ball-label row was moved from `totalH × 0.80` to `totalH × 0.76`. The overall overlay strip is now visibly slimmer while keeping all text readable and non-overlapping.
- **Fixed bug where portrait mode incorrectly displayed the landscape overlay.** The root cause was that `CameraPreviewScreen` and `StreamPreviewScreen` both forced `ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE`, which prevented `configuration.orientation` from ever reporting portrait. Both screens now allow the device's natural orientation. In `ScoreboardOverlay.kt`, orientation detection was changed from the previous `configuration.screenWidthDp >= configuration.screenHeightDp` comparison to the standard `configuration.orientation == Configuration.ORIENTATION_PORTRAIT` API, which correctly triggers recomposition when the device rotates.
- **Overlay now switches correctly between portrait and landscape layouts.** The Compose overlay recomposes automatically whenever `LocalConfiguration.current` reports an orientation change, applying narrower section weights (0.8 f vs 1 f), tighter horizontal padding (6 dp vs 10 dp), and smaller font sizes in portrait mode. The Canvas renderer continues to derive orientation from `streamWidth < streamHeight`, unchanged, since the stream dimensions are supplied as constructor parameters.

---

### 2026-03-10 – UI Improvement: Broadcast Overlay Layout Refinement (Plain Ball Labels, Right-Side Alignment, Portrait Layout Fix)

**Files changed:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoreboardOverlay.kt` | Updated – removed circle borders from ball indicators, fixed bowler section left-alignment, improved portrait orientation detection |
| `app/src/main/java/com/example/scorebroadcaster/streaming/ScoreboardOverlayRenderer.kt` | Updated – removed circle drawing, fixed ball row to start left-to-right from section left edge, added separate portrait/landscape constants |
| `README.md` | Updated – added this development log entry |

**Explanation:**

- **Current-over run indicators simplified from circles to plain text labels.** The `BallIndicator` composable no longer renders a bordered circular `Box` around each ball outcome. It now renders a plain `Text` token with the same color rules: warm gold for 4 and 6, red for wickets, amber for wides and no-balls, white for dots and normal runs. In the Canvas renderer, `drawBallIndicator` was replaced with `drawBallLabel` which draws only text (no `canvas.drawCircle` call), and the unused STROKE-style ball border paints were removed entirely. The ball row becomes visibly slimmer as a result.
- **Right-side bowler section is now left-aligned and anchored after the center score block.** The `BowlerSection` Compose column was changed from `horizontalAlignment = Alignment.End` to `Alignment.Start`, so the bowler name and current-over labels start from the left edge of the right section (immediately after the center panel). In the Canvas renderer, `drawBowlerSection` now draws the ball row left-to-right starting from the section's left edge with `sectionLeft + pad`, replacing the previous right-to-left drawing from the section's right edge. Both the bowler info line and the ball label row are aligned under the same left anchor.
- **Portrait mode now correctly uses a portrait-specific overlay layout instead of reusing landscape values.** In the Compose overlay, orientation detection was changed from `configuration.orientation == ORIENTATION_LANDSCAPE` to `configuration.screenWidthDp >= configuration.screenHeightDp`, which compares actual screen dp dimensions directly and is resilient to locked-orientation edge cases. In the Canvas renderer, the previous `fontScale = 0.85f` multiplier approach was replaced with two explicit sets of named constants (`LS_*` for landscape, `PT_*` for portrait) covering font sizes, side-section width fraction, padding, and ball-label gap. Portrait-mode sizes are intentionally smaller and the side-section fraction is narrower (30 % vs 36 %) to give the center score block more room on a narrow screen.

---

### 2026-03-10 – UI Improvement: Broadcast Overlay Compact Redesign (Orientation-Responsive, Center Panel, Slimmer Balls)

**Files changed:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoreboardOverlay.kt` | Updated – reduced ball circle sizes, added center section background panel, added orientation-responsive layout |
| `app/src/main/java/com/example/scorebroadcaster/streaming/ScoreboardOverlayRenderer.kt` | Updated – reduced ball radius/spacing, added center section rounded-rect panel, added portrait/landscape orientation detection |
| `README.md` | Updated – added this development log entry |

**Explanation:**

- **Current-over ball circles reduced in size for a slimmer overlay.** Ball indicators in the Compose overlay have been reduced from 20 dp to 14 dp, border stroke from 1.5 dp to 1 dp, text size from 8 sp to 7 sp, and horizontal spacing between balls from 2 dp to 1 dp. In the Canvas renderer, ball radius has been reduced from 7 px to 5.5 px and center-to-center spacing from 16 px to 12 px. The result is a noticeably slimmer over row while labels remain centered and readable.
- **Center score section now uses its own highlighted background panel.** The center column (Team A vs Team B, score, overs, run rate / chase context) is now wrapped in a distinct darker rounded capsule (`#0D2137` at 87 % opacity) set against the outer deep-blue strip. In Compose this is a `RoundedCornerShape(5.dp)` Box; in the Canvas renderer a `drawRoundRect` is drawn behind the center section text. The left and right side sections remain outside this panel.
- **Overlay now adapts its sizing and layout for landscape vs portrait.** In Compose, `LocalConfiguration.current.orientation` is checked each recomposition; portrait mode uses tighter horizontal padding (6 dp vs 10 dp), narrower side-section weights (0.8 f vs 1 f), and slightly smaller text sizes (name 10 sp, stats 9 sp, score 12 sp). In the Canvas renderer, `streamWidth < streamHeight` detects portrait; portrait applies an 85 % font-scale factor, a narrower side-section fraction (32 % vs 36 %), and proportionally smaller ball dimensions.
- **Overlay is flush to the bottom edge in immersive preview and stream output.** No external bottom margin or safe-area inset is applied to the scoreboard overlay in `CameraPreviewScreen`; the `Alignment.BottomCenter` anchor keeps it pinned to the screen edge. The Canvas renderer draws the full overlay height bitmap with no bottom gap.

---

### 2026-03-10 – UI Improvement: Broadcast Overlay Styling Refinement (Text Hierarchy + Outlined Ball Indicators)

**Files changed:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoreboardOverlay.kt` | Updated – middle section text hierarchy improved; ball indicators changed to outlined circles |
| `app/src/main/java/com/example/scorebroadcaster/streaming/ScoreboardOverlayRenderer.kt` | Updated – matching text hierarchy and outlined ball indicators in Canvas renderer |
| `README.md` | Updated |

**Explanation:**

- **Middle section text hierarchy improved with multiple highlight colors.** The centre score section now uses separate `Text` composables for each element, each with its own colour and weight. Team names use light grey (`#D0D0D0`) with normal weight. The score (`177-2`) remains pure white and extra-bold so it visually stands out the most. Overs are now rendered in warm gold (`#F2C94C`) to draw attention. The run rate label is muted grey while the run rate value is rendered in gold, giving a two-tone "RR 6.21" display.
- **Score and overs now visually emphasized.** The score uses `FontWeight.ExtraBold` and a slightly larger font size than the surrounding text. Overs use the accent gold colour to distinguish them from the team name text.
- **Current-over balls changed from filled circles to outlined broadcast-style indicators.** Ball indicators no longer use a coloured fill background. Instead every ball is an outlined circle with a thin border and the label centred inside, matching professional cricket broadcast graphics. Border and text colours follow the same rules: light grey for dots and normal runs (white text), gold border and text for boundary 4, stronger gold border with white text for six, red border and text for wickets, and amber border and text for wides and no-balls.

### 2026-03-10 – UI Improvement: Broadcast Overlay Visual Refinement (Ball Centering + Blue/Gold Palette)

**Files changed:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoreboardOverlay.kt` | Updated – ball indicator text properly centered; overlay color scheme updated |
| `app/src/main/java/com/example/scorebroadcaster/streaming/ScoreboardOverlayRenderer.kt` | Updated – ball color palette updated; wide/no-ball handling added |
| `README.md` | Updated |

**Explanation:**

Two targeted visual fixes applied to both the Compose overlay and the Canvas stream renderer:

- **Ball indicator text properly centered.** Each ball indicator circle in the current-over row is now a fixed 20 dp container using `clip(CircleShape)` + `background()` with `contentAlignment = Alignment.Center`, ensuring the label is exactly centred both horizontally and vertically. Font size updated to 8 sp to fit comfortably inside the larger circle. The renderer already used the correct `cy - (ascent + descent) / 2` formula for vertical centering; no change needed there.
- **Overlay color scheme updated from single yellow to blue + gold broadcast palette.** Background strip changed from plain black to deep blue (`#1F3A5F`, 80 % opacity). Score text changed from amber to white for maximum contrast. Run-rate / context line changed from amber to light gray. Ball indicators now use a two-tone scheme: boundary 4 uses warm gold (`#F2C94C`), six uses a stronger gold (`#FFAA00`), wide and no-ball use a lighter amber (`#F5A623`), regular run deliveries use a dark blue-neutral (`#2A3A4A`), wickets remain red, and dot balls remain an outlined circle. The striker indicator dot and accent color use warm gold throughout.
- **Improved readability for live preview and stream overlay.** The new palette avoids large areas of yellow, uses high contrast white text on dark blue, and produces a TV-broadcast aesthetic that is easier to read over camera video.

### 2026-03-10 – UI Improvement: Broadcast Overlay Restructured into Left / Middle / Right Lower-Third

**Files changed:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/BroadcastOverlayMapper.kt` | Updated – added `oversText` field to `BowlerOverlayInfo` |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoreboardOverlay.kt` | Updated – restructured center and bowler sections, added ball label text |
| `app/src/main/java/com/example/scorebroadcaster/streaming/ScoreboardOverlayRenderer.kt` | Updated – restructured center and bowler sections to match Compose overlay |
| `app/src/main/java/com/example/scorebroadcaster/ui/CameraPreviewScreen.kt` | Updated – removed bottom padding so overlay sits flush to screen edge |
| `README.md` | Updated |

**Explanation:**

The broadcast overlay was restructured into a slimmer three-part horizontal lower-third layout with tighter spacing:

- **Broadcast overlay restructured into left / middle / right layout.** Batter, score, bowler, and current-over balls are now aligned into a compact two-row lower-third. The left section shows striker and non-striker (name + runs/balls with a small amber strike indicator). The middle section is exactly two lines: line 1 shows team names, score, and overs all on the same row with the score slightly bolder; line 2 shows the run rate or chase info. The right section shows bowler name, figures, and overs all on one line, with compact ball-indicator circles below.
- **Ball indicators now show result labels.** Each circle in the current-over row displays the delivery outcome (0, 1, 2, 4, 6, W, W+, N+) in a small label. Colour coding is preserved: red for wickets, blue for boundaries, outlined-only for dots, amber for extras, grey for other runs.
- **Overlay now sits flush to the bottom with reduced height and tighter spacing.** The bottom padding that previously left a visible gap under the overlay has been removed. The center section was reduced from four stacked elements to two lines, significantly lowering the overall strip height toward the 64–80 dp target.


**Files changed:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoreboardOverlay.kt` | Updated |
| `app/src/main/java/com/example/scorebroadcaster/streaming/ScoreboardOverlayRenderer.kt` | Updated |
| `app/src/main/java/com/example/scorebroadcaster/ui/CameraPreviewScreen.kt` | Updated |
| `README.md` | Updated |

**Explanation:**
Overlay compacted further to feel like a real cricket TV lower-third bar.

- **Overlay redesigned into left / center / right broadcast strip** closer to TV style: a single slim `Row` replaces the previous two-row `Column`; the second row (balls + context + innings badge) is eliminated.
- **Left block** – striker and non-striker, name (11 sp) + runs/balls (10 sp), stacked two lines, 1 dp gap.
- **Center capsule** – match short title and innings badge on one line (9 sp), large score (18 sp, amber), overs (9 sp), run rate / chase info (9 sp) all stacked; this is the visual focus.
- **Right block** – bowler name (11 sp) + figures (10 sp), then a compact row of 9 dp ball circles directly below.
- **Outer strip padding** reduced to 4 dp vertical / 10 dp horizontal (was 6 dp / 12 dp); inter-element spacing 1 dp.
- **Canvas renderer** (`ScoreboardOverlayRenderer`) default height reduced from 130 px to 90 px; all paint text sizes reduced proportionally; ball indicators shrunk from 10 px radius to 7 px; `drawRow2` removed; context line and innings badge drawn inside `drawCentreSection`; ball circles drawn inside `drawBowlerSection` right-aligned.
- **CameraPreviewScreen** overlay given an 8 dp bottom safe margin so the strip sits just above the very bottom edge of the preview.

### 2026-03-09 – UX Improvement: Create Match Screen

**Files changed:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/CreateMatchScreen.kt` | Updated |
| `README.md` | Updated |

**Summary of changes:**

- **Three-section layout:** The screen is now organised into three clearly labelled sections — **Teams**, **Match Format**, and **Toss** — separated by `HorizontalDivider`s, giving the form the feel of a short guided setup rather than a long scroll.
- **Swap Teams button:** An `IconButton` with a `SwapVert` icon sits between the Team A and Team B fields. Tapping it swaps both team names and any associated saved-team references and pre-loaded player lists so the UI recomposes correctly.
- **Format selection with chips:** The format dropdown menu has been replaced with `FilterChip`s displaying concise labels (T20, T10, ODI, Tape Ball, Custom). This lets scorers select a format in one tap without opening a menu.
- **Custom overs field shown conditionally:** The "Overs per side" text input is only visible when the **Custom** chip is selected; it is hidden for all predefined formats, reducing visual clutter.
- **Toss section clarified:** The toss winner prompt was relabelled "Who won the toss?" and the decision prompt was relabelled "Decision" for plain-language clarity.
- **Bottom summary:** A compact read-only summary (e.g. *Falcons vs Strikers · T20 • Toss: Falcons chose to bat*) is shown above the CTA button using `bodySmall` / `onSurfaceVariant` so scorers can review the setup at a glance before proceeding.
- **No logic changes:** All existing ViewModel APIs, navigation routes, saved-team behaviour, and match-creation logic are unchanged; this is a purely presentational refactor.

---

### 2026-03-09 – Bug Fix: Innings setup dialog incorrectly reopening during wicket flow

**Root cause:** The `needsInningsSetup` derived value in `ScoringScreen.kt` fired `true` whenever `console.striker` or `console.nonStriker` was `null`.  After a wicket falls, `updateConsoleAfterEvent` deliberately sets the dismissed batter's slot to `null` (so the incoming batter can fill it) while simultaneously placing a `PendingAction.SelectNextBatter` on the console state.  Because the null-check ran *before* the pending-action check, `needsInningsSetup` became `true` momentarily, and the `LaunchedEffect(needsInningsSetup)` triggered `setupDialogVisible = true` — incorrectly reopening the innings-setup bottom sheet in the middle of the wicket replacement flow.

**What was corrected:** A single guard clause was added to the `needsInningsSetup` condition:
```kotlin
// before
val needsInningsSetup = console.phase == InningsPhase.SETUP ||
        ((console.phase == InningsPhase.FIRST_INNINGS || …) &&
                (console.striker == null || console.nonStriker == null || …))

// after
val needsInningsSetup = console.phase == InningsPhase.SETUP ||
        ((console.phase == InningsPhase.FIRST_INNINGS || …) &&
                console.pendingAction !is PendingAction.SelectNextBatter &&   // ← new guard
                (console.striker == null || console.nonStriker == null || …))
```
When a wicket pending action is already in progress the null batter slot is expected and intentional; setup should not be shown.

**Confirmation:** After a wicket the flow is now:
1. `WicketDetailsDialog` — scorer picks dismissal type.
2. `SelectPlayerDialog` (next batter) — scorer picks incoming batter.
3. Scoring resumes normally. No innings-setup bottom sheet appears.

Genuine first- and second-innings setup (phase == `SETUP`, or batter/bowler genuinely absent after an app restart) is unaffected.

**Files changed:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Added `PendingAction.SelectNextBatter` guard to `needsInningsSetup` |
| `README.md` | Added this log entry |

---

### 2026-03-09 – Bug Fix: Home tab in bottom navigation

**Problem:** After tapping any bottom-nav tab other than Home, tapping the Home tab no longer navigated back to the home screen. The root cause was that `popUpTo(startDestination) { saveState = true }` saves the popped back-stack entries under the start destination's ID as the key. When the Home tab was then tapped with `restoreState = true`, the NavController found that saved state (keyed to the home route) and incorrectly restored the previously-popped destinations on top of the home screen — leaving the user on the wrong screen instead of Home.

**What changed:**
- **Home now always navigates correctly after switching tabs** — `restoreState` is set to `false` for the Home tab (the start destination) so the NavController never finds and incorrectly restores previously-popped tab back stacks when returning to Home. All other tabs continue to use `restoreState = true` (which, in this flat-graph setup, is a no-op but correctly signals intent for future nested-graph adoption).
- **Selected-tab state fixed** — `selectedTab()` now references `BottomNavTab.route` directly for the primary tab routes instead of duplicating the route strings as bare literals. This makes the selection logic and the click-target routing share a single source of truth (`BottomNavTab.route`), preventing them from drifting apart if routes are ever renamed.
- **Bottom-nav back stack handling improved** — `popUpTo(findStartDestination().id) { saveState = true }` with `launchSingleTop = true` is preserved for all tabs, ensuring each tab switch clears the back stack down to the start destination without unnecessary duplicate entries.

**Files changed:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/AppShell.kt` | Updated – `restoreState` conditional on non-HOME tab; `selectedTab()` references `BottomNavTab.route` |
| `README.md` | Updated |

---

### 2026-03-09 – UX Improvement: Simplified Innings Setup Add-Player Flow

The inline player-creation fields inside the innings setup bottom sheet have been replaced with clean, single-tap buttons that open purpose-built dialogs.

**What changed:**
- **Inline fields removed** — the `OutlinedTextField` + "Add" button rows that previously cluttered the batting and bowling sections of `SetupOpenersBottomSheet` have been removed.
- **"+ Add Batter" / "+ Add Bowler" buttons added** — a single `OutlinedButton` now sits below each section's dropdowns. Tapping it opens a focused dialog instead of cluttering the sheet with a persistent text field.
- **`AddPlayerChoiceDialog`** — a small `AlertDialog` offering two options: **"Pick from saved players"** or **"Add new player"**, plus a Cancel button. Replaces the previous icon-button-to-picker shortcut.
- **`AddNewPlayerDialog`** — a minimal `AlertDialog` with a single name text field and an **"Add"** confirm button (disabled until name is non-blank). Replaces the inline text field + button pattern.
- **`PlayerPickerDialog` reused** — the existing picker (search + saved-player list + inline create) is still used when the scorer chooses "Pick from saved players", preserving that flow without duplication.
- **Newly added players appear immediately** — the existing `LaunchedEffect` on `battingTeam.players` / `bowlingTeam.players` inside the bottom sheet refreshes the striker/non-striker/bowler dropdowns as soon as a player is added, unchanged.
- **No logic changes** — `MatchViewModel.setOpeners`, the scoring reducer, pending-action system, wicket flow, and bowler-change flow are completely untouched. This is a pure UI/UX improvement.

**Files changed:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Replaced inline add-player rows with buttons; added `AddPlayerChoiceDialog` and `AddNewPlayerDialog` composables |
| `README.md` | Updated |

---

### 2026-03-09 – UI Improvement: Innings Setup Bottom Sheet

The innings setup flow has been redesigned from a cramped `AlertDialog` to a spacious `ModalBottomSheet`, giving scorers a cleaner, more comfortable surface for picking opening batters and bowler before a new innings begins.

**What changed:**
- **Replaced dialog with modal bottom sheet** — `SetupOpenersDialog` (using `AlertDialog`) was removed and replaced with `SetupOpenersBottomSheet` (using Material3 `ModalBottomSheet`). The sheet slides up from the bottom of the screen, providing a larger, more ergonomic canvas on a phone.
- **Clearer batter / bowler grouping** — the sheet is organised into three visually separated sections with labelled headers coloured in the primary theme colour: **Batting Team**, **Batters** (striker + non-striker), and **Opening Bowler** (including the bowling team name). A `HorizontalDivider` cleanly separates the batting section from the bowling section.
- **Easier player selection** — dropdowns for striker, non-striker, and opening bowler are now given full-width treatment with comfortable vertical spacing. The same player cannot be selected for both the striker and non-striker roles (the opposing dropdown automatically excludes the already-selected player).
- **Default selections** — when players are already on the roster the first two batting-team players are pre-selected as striker and non-striker, and the first bowling-team player is pre-selected as opening bowler. Scorers can typically confirm in one tap.
- **"+ Add Player to [Team]" shortcut** — the add-player text fields are now labelled with the actual team name (e.g. `+ Add Player to Mumbai Indians`) so it is immediately clear which roster is being extended. The saved-player picker icon is preserved.
- **"Start Innings" CTA** — the confirm button now reads "Start 1st Innings" / "Start 2nd Innings" (matching the sheet title) and spans the full width of the sheet. It remains disabled until striker, non-striker, and bowler are all chosen.
- **Improved mobile usability** — proper padding (24 dp horizontal, 32 dp bottom), consistent 8 dp gaps between controls, and scrollable content ensure the sheet works well on both small and large phone screens.
- **No logic changes** — `MatchViewModel.setOpeners`, the scoring reducer, pending-action system, and innings-phase logic are untouched. This is a UI-only change.

**Files changed:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Updated |
| `README.md` | Updated |

---

### 2026-03-09 – UI Improvement: Compact Match Header

The scoring screen header has been redesigned into a compact, single-block `CompactMatchHeader` composable that sits above the tab row and remains visible at all times.

- **Score and overs prioritised** — the batting team name, runs/wickets score, and current overs are displayed in a single prominent line, making the live score instantly readable.
- **Vertical space reduced** — small vertical padding (10 dp) and minimal line spacing replace the previous multi-row stacked layout, moving the tab row and scoring controls higher on the screen.
- **Chase information shown as subtitle** — during the second innings a concise subtitle line (e.g. "Need 23 runs from 15 balls") appears directly below the score; during the innings break "Target N" is shown instead. The subtitle is hidden when not relevant.
- **Improves scorer visibility and control access** — the more compact header means scoring buttons are reachable without scrolling on small Android devices.
- **No scoring logic changes** — this is a UI-only refactor. All existing `MatchState` and `ScoringConsoleState` flows are observed unchanged; the header updates automatically on every `BallEvent`.

**Files changed:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Updated |
| `README.md` | Updated |

---

### 2026-03-10 – UI Update: Navigation and Boundary Button Styling

**Removed Scorecard and Ball Timeline from side navigation** to simplify the navigation drawer. Both screens remain fully accessible via the internal tab navigation within the Scoring screen (Score / Timeline / Scorecard tabs).

**Run buttons now visually distinguish boundaries** (4 and 6) from normal runs, improving scoring feedback during live matches.

| Button | Background | Text | Rationale |
|--------|-----------|------|-----------|
| 0, 1, 2, 3 | Default (primary container) | Default | Neutral — normal delivery |
| **4** | Amber gold (`#FFB300`) | Dark (`#1A1100`) | Boundary highlight — gold |
| **6** | Deep orange (`#E65100`) | White | Six highlight — strongest emphasis |
| Wicket | Error container | On-error | Destructive / significant event |

**Files changed:**

| File | Change |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/AppShell.kt` | Removed Scorecard and Ball Timeline drawer items |
| `app/src/main/java/com/example/scorebroadcaster/ui/theme/Color.kt` | Added `BoundaryFourContainer`, `OnBoundaryFourContainer`, `BoundarySixContainer`, `OnBoundarySixContainer` |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Applied boundary colours to 4 and 6 run buttons |
| `README.md` | Added this Development Log entry |

---

### 2026-03-09 – UI Improvement: Scoring Buttons Layout

The scoring control area in `ScoringScreen` has been reorganised into a structured scorer pad with three clearly separated sections, making live scoring faster and more comfortable during a real match.

**Sections introduced:**

| Section | Buttons | Layout |
|---------|---------|--------|
| Runs | 0, 1, 2, 3, 4, 6 | 2 rows × 3 columns |
| Extras | Wide, No Ball, Bye, Leg Bye | 2 rows × 2 columns |
| Actions | Wicket, Undo | Side-by-side |

**What changed:**

- Scoring controls reorganised into **Runs**, **Extras**, and **Actions** sections, each with a small section label and a `Surface`-backed container.
- Run buttons are larger (52 dp minimum height) for easier one-handed tapping; **4** and **6** use `secondaryContainer`/`tertiaryContainer` to subtly highlight boundaries.
- Extras buttons use `OutlinedButton` to appear secondary but remain easy to reach.
- **Wicket** uses `errorContainer` for a clear destructive/important appearance; renamed from "W" to "Wicket" for readability.
- **Undo** placed alongside Wicket in the Actions section; always enabled (unchanged from original).
- Full labels used throughout: "Wide", "No Ball", "Bye", "Leg Bye", "Wicket", "Undo".
- Consistent `6 dp` / `8 dp` spacing between buttons; `12 dp` spacing between sections.
- No hard-coded colours — all colours sourced from Material3 theme roles.

**New private composables:**

| Composable | Purpose |
|-----------|---------|
| `ScoringActionButton` | Single scoring button with minimum tap size |
| `ScoringControlsSection` | Labelled `Surface` container for a button group |
| `RunButtonsGrid` | 2 × 3 grid of run buttons |
| `ExtrasButtonsGrid` | 2 × 2 grid of extras buttons |
| `ActionButtonsRow` | Side-by-side Wicket + Undo buttons |

**Files modified:**

| File | Change |
|------|--------|
| `ui/ScoringScreen.kt` | Replaced `ScoringButtonsSection` body; added four helper composables; added `defaultMinSize` import |
| `README.md` | Added this Development Log entry |

**What is NOT changed:**

- Scoring logic — zero changes to `MatchViewModel`, `ScoreReducer`, `BallEvent`, or any domain model.
- Extras dialog flow — tapping an extras button still opens the same entry dialog.
- Wicket flow — tapping Wicket still opens the same wicket details dialog.
- Undo behaviour — unchanged.
- Disabled state logic — all buttons respect the same `scoringEnabled` guard as before.
- All other screens — no changes.

---

### 2026-03-09 – UI Improvement: Scoring Screen Tab Navigation

The `ScoringScreen` top navigation has been refactored from `FilterChip`-style buttons to a proper Material3 `ScrollableTabRow`, replacing the old `QuickNavBar` row that wrapped badly on smaller screens.

**Tabs introduced:**

| Tab | Content |
|-----|---------|
| Score | Existing ball-by-ball scoring console |
| Timeline | Inline `BallTimelineScreen` — ball-by-ball delivery history |
| Scorecard | Inline `ScorecardScreen` — full batting/bowling table |

**What changed:**

- `FilterChip` / `OutlinedButton` chip row (`QuickNavBar`) removed from `ScoringScreen`.
- Three tabs — **Score**, **Timeline**, **Scorecard** — rendered via `ScrollableTabRow` at the top of `ScoringScreen`.
- **Camera is not included** in the tab bar; broadcasting/live streaming remains accessible only through the separate **Live** section and existing navigation routes.
- Tab switching is instant and stays within `ScoringScreen` — no navigation events are fired.
- `MatchViewModel` state (score, innings, batters, bowler, pending actions) is fully preserved when switching tabs because the ViewModel is never recreated.
- Tab selection uses `rememberSaveable` so it survives recomposition.

**Files modified:**

| File | Change |
|------|--------|
| `ui/ScoringScreen.kt` | Added `ScoringScreenTab` enum; added `matchSessionViewModel` param; added `selectedTab` state; replaced `QuickNavBar` with `ScrollableTabRow`; renders `BallTimelineScreen` / `ScorecardScreen` inline per tab; removed private `QuickNavBar` composable |
| `MainActivity.kt` | Passes `matchSessionViewModel` to both `ScoringScreen` call sites (`score_tab`, `scoring_only`) |
| `README.md` | Added this Development Log entry |

**What is NOT changed:**

- Scoring logic — zero changes to `MatchViewModel`, `ScoreReducer`, or any domain model.
- Navigation routes `scorecard` and `ball_timeline` — still registered and used by `MatchDetailsScreen`, `HomeScreen`, and the navigation drawer.
- Camera / Live / streaming flow — entirely untouched.
- All other screens — no changes.

---

### 2026-03-12 – Bug Fix: Saved Players Sync Across Team Creation

Fixed an inconsistency where the **Create Match → Create new team → Add Players** flow did not show Saved Players and silently discarded any newly created players.

**Root cause:**
`CreateMatchScreen`'s `TeamSelectorField` composable was calling `CreateSavedTeamDialog` without passing `savedPlayers` or `onCreatePlayer`.  This caused:
- The Add Players picker inside the dialog to always show an empty list (fell back to the default `emptyList()`).
- New players created from that flow to be silently dropped (fell back to the default no-op callback `{}`).

**Additional improvement:**
`MultiPlayerPickerSheet` now shows a newly created player in the checkbox list **immediately** after creation — before Room emits the updated snapshot — by maintaining a local `pendingProfiles` list that is merged with `savedPlayers` for the eligible-player computation.

**Files modified:**

| File | Change |
|------|--------|
| `ui/CreateMatchScreen.kt` | `TeamSelectorField` now accepts `savedPlayers: List<PlayerProfile>` and `onCreatePlayer: (PlayerProfile) -> Unit`; `CreateSavedTeamDialog` receives both; `CreateMatchScreen` collects `savedPlayers` from the ViewModel and passes it through |
| `ui/MultiPlayerPickerSheet.kt` | Added `pendingProfiles` local state; newly created profiles are added there immediately so they appear in the checkbox list before Room emits; removed stale `remember(savedPlayers, …)` memoisation in favour of direct derivation; updated empty-state copy |

**What is NOT changed:**
- `MatchSessionViewModel`, `SavedPlayerRepository`, `SavedPlayersScreen`, `PlayerSetupScreen`, `SavedTeamsScreen`, `ScoringScreen` — no changes.
- Scoring logic, streaming, and all other flows — untouched.

---

### 2026-03-08 – Phase 11: Saved Player Profiles Persistence with Room

Room persistence has been activated for Saved Player Profiles. Profiles now survive app restarts — creating a player profile, killing the app, and reopening it will show the profile exactly as saved.

**No schema changes required** — the `player_profiles` table was already introduced in Phase 9. This phase activates it by wiring the existing `PlayerProfileDao` into `SavedPlayerRepository` and `MatchSessionViewModel`.

**Files modified:**

| File | Change |
|------|--------|
| `data/local/PlayerProfileEntity.kt` | Added `toDomain()` and `PlayerProfile.toEntity()` mapping helpers |
| `data/local/PlayerProfileDao.kt` | Added `observeAll(): Flow<List<PlayerProfileEntity>>` for reactive updates |
| `repository/SavedPlayerRepository.kt` | Replaced in-memory `object` with Room-backed `class` — `PlayerProfileDao` + `CoroutineScope`, reactive `playerFlow`, non-suspending mutations (`addPlayer`, `removePlayer`, `updatePlayer`) |
| `viewmodel/MatchSessionViewModel.kt` | Instantiates `SavedPlayerRepository` with `playerProfileDao` + `viewModelScope`; `savedPlayers` is now a reactive `StateFlow` driven by Room Flow |

**What is NOT changed:**
- `MatchRepository` — still in-memory; Matches migration is a future phase.
- All screens (`SavedPlayersScreen`, `PlayerPickerDialog`, `PlayerSetupScreen`, `SavedTeamsScreen`, `ScoringScreen`) — zero UX changes.
- Domain model `PlayerProfile` and `PlayerSourceType` — unchanged.
- Scoring logic, streaming, and all other flows — untouched.

---

### 2026-03-08 – Phase 10: Saved Teams Persistence with Room

Room persistence has been activated for Saved Teams. Teams now survive app restarts — creating a team, killing the app, and reopening it will show the team exactly as saved.

**Persistence approach: TypeConverter for `List<Player>`**

`SavedTeam` holds a `List<Player>` where each `Player` is a lightweight value object (`id`, `name`, `sourceProfileId`). Rather than a separate join table, the player list is stored as a JSON string in a single column using a `@TypeConverter`. This was chosen over the existing `SavedTeamPlayerCrossRef` junction table because:
- `Player` is a match-level snapshot, not the same entity as `PlayerProfile`
- Ad-hoc players (no `sourceProfileId`) have no counterpart in `player_profiles`
- A TypeConverter is simpler and keeps the schema flat while the data is small
- The `SavedTeamPlayerCrossRef` table is retained for potential future use in a many-to-many profile linkage

**Files added:**

| File | Purpose |
|------|---------|
| `data/local/PlayerListTypeConverter.kt` | `@TypeConverter` pair: `List<Player>` ↔ JSON string using `org.json` (no extra deps) |

**Files modified:**

| File | Change |
|------|--------|
| `data/local/SavedTeamEntity.kt` | Added `players: List<Player>` column; added `toDomain()` / `SavedTeam.toEntity()` converters |
| `data/local/SavedTeamDao.kt` | Added `observeAll(): Flow<List<SavedTeamEntity>>` for reactive updates |
| `data/local/ScoredDatabase.kt` | Added `@TypeConverters(PlayerListTypeConverter::class)`; bumped version to 2; added `fallbackToDestructiveMigration()` (dev-phase only) |
| `repository/SavedTeamRepository.kt` | Replaced in-memory `object` with Room-backed `class` — mirrors `SavedPlayerRepository` pattern (DAO + CoroutineScope, reactive `teamFlow`, non-suspending mutations) |
| `viewmodel/MatchSessionViewModel.kt` | Instantiates `SavedTeamRepository` with `savedTeamDao` + `viewModelScope`; `savedTeams` is now a reactive `StateFlow` driven by Room Flow; `refresh()` no longer manually resets saved-teams state |

**What is NOT changed:**
- `MatchRepository` — still in-memory; Matches migration is a future phase.
- All screens (`SavedTeamsScreen`, `CreateMatchScreen`, `CreateSavedTeamDialog`, team picker) — zero UX changes.
- Domain model `SavedTeam` and `Player` — unchanged.
- Scoring logic, streaming, and all other flows — untouched.

---

### 2026-03-08 – Phase 9: Room Database Foundation

Room database infrastructure has been introduced alongside the existing in-memory repositories.
The in-memory repositories (`MatchRepository`, `SavedTeamRepository`, `SavedPlayerRepository`) remain the active data sources and are **not yet migrated**. Room is present but dormant — no repository uses it yet.

**New `data/local` package:**

| File | Purpose |
|------|---------|
| `PlayerProfileEntity.kt` | Room table for reusable player profiles |
| `SavedTeamEntity.kt` | Room table for reusable saved team templates |
| `SavedTeamPlayerCrossRef.kt` | Junction table linking teams to player profiles |
| `MatchEntity.kt` | Room table for match records (flat publish-ready columns) |
| `BallEventEntity.kt` | Room table for individual ball delivery events |
| `PlayerProfileDao.kt` | DAO with insert / update / delete / getAll / getById |
| `SavedTeamDao.kt` | DAO with insert / update / delete / getAll / getById |
| `MatchDao.kt` | DAO with insert / update / delete / getAll / getById |
| `BallEventDao.kt` | DAO with insert / update / delete / getAll / getById |
| `ScoredDatabase.kt` | `RoomDatabase` singleton — `ScoredDatabase.getInstance(context)` |

**Dependencies added (`libs.versions.toml` + `app/build.gradle.kts`):**
- `androidx.room:room-runtime:2.6.1`
- `androidx.room:room-ktx:2.6.1`
- `androidx.room:room-compiler:2.6.1` (via `kapt`)
- `kotlin-kapt` plugin enabled

**What is NOT changed:**
- `MatchRepository`, `SavedTeamRepository`, `SavedPlayerRepository` — all untouched and still in use.
- No ViewModel, screen, or domain layer is modified.
- Repository migration to Room is deferred to a future phase.

---

### 2026-03-07 – Prevent Same Team Match Creation

**Validation rule:**
Team A and Team B must not be the same name (comparison is case-insensitive). This applies both to freely typed names and to saved teams selected from the dropdown. For example, "Falcons" vs "Falcons" and "Falcons" vs "falcons" are both invalid.

**UI error message:**
When the two team names are equal (case-insensitive), a Material3-styled error message — _"Both teams cannot be the same."_ — is displayed immediately below the Team B field. The "Next: Add Players →" button is disabled until the conflict is resolved.

**Files modified:**
| File | Change |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/CreateMatchScreen.kt` | Added `sameTeamError` derived state; updated `canProceed` to include `!sameTeamError`; added error `Text` composable below Team B field. |
| `README.md` | Added this Development Log entry. |

---

### 2026-03-07 – Publish-Ready Match Model

**What changed:**
Refactored the `Match` entity and app structure to be ready for future match publishing, without implementing any backend or authentication logic.  All existing behaviour is unchanged — matches remain local/private, scoring is unaffected, and all screens continue to work exactly as before.

**New `MatchVisibility` enum:**

| Value | Meaning |
|-------|---------|
| `PRIVATE` | Local/scorer-only.  Default for all new matches. |
| `PUBLISHED` | Publicly listed via backend (future). |
| `UNLISTED` | Accessible by direct link / share-code but not listed publicly (future). |

**New fields on `Match` (all nullable / defaulted — zero breaking changes):**

| Field | Type | Purpose |
|-------|------|---------|
| `localId` | `String` | Stable on-device key (mirrors `id` today; kept separate for future local-vs-remote clarity) |
| `remoteId` | `String?` | Backend-assigned identifier after publishing (null until sync exists) |
| `ownerUserId` | `String?` | Account ID of the scorer/owner (null until auth exists) |
| `visibility` | `MatchVisibility` | Defaults to `PRIVATE` |
| `publishedAt` | `Long?` | Epoch-ms timestamp of first publication (null while private) |
| `shareCode` | `String?` | Short public slug for viewer links (null until backend assigns one) |

**Computed property added to `Match`:**
- `isPublished: Boolean` — `true` when `visibility == PUBLISHED && publishedAt != null`.

**Publishing placeholder in `MatchDetailsScreen`:**
A new `MatchPublishingSection` composable is added below the action buttons.  It shows:
- **Match Visibility** row displaying the current `visibility` label (always "Private" today).
- **Publish Match · coming soon** button (disabled).
- **Share Match · coming soon** button (disabled).

This makes the one-scorer / many-viewers product direction visible in the UI without pretending any backend exists.

**Architecture notes — one-scorer / many-viewers:**
- Only one scorer (the device that created the match) can edit a match.  This assumption is preserved.
- Viewer access will later be gated on `MatchVisibility`; the local repository never enforces viewer permissions — that is the remote backend's responsibility.
- `MatchRepository` documentation updated to reflect local-vs-remote identity separation and future viewer architecture.

**Files created/modified:**

| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/data/entity/MatchVisibility.kt` | **Created** – new `MatchVisibility` enum with `PRIVATE`, `PUBLISHED`, `UNLISTED` |
| `app/src/main/java/com/example/scorebroadcaster/data/entity/Match.kt` | **Updated** – new publish-ready fields; `isPublished` computed property |
| `app/src/main/java/com/example/scorebroadcaster/repository/MatchRepository.kt` | **Updated** – documentation updated for future remote-sync and viewer architecture |
| `app/src/main/java/com/example/scorebroadcaster/ui/MatchDetailsScreen.kt` | **Updated** – `MatchPublishingSection` placeholder added |
| `README.md` | **Updated** |

**What was prepared now vs. what is still future work:**

| Prepared now | Still future work |
|-------------|-------------------|
| `MatchVisibility` enum | Backend API for publishing |
| Publish-ready fields on `Match` | Authentication / `ownerUserId` population |
| `isPublished` helper | Remote match sync |
| Placeholder publishing UI in `MatchDetailsScreen` | Enabling "Publish" / "Share" buttons |
| Architecture documentation | Viewer-mode read-only screens |
| `MatchRepository` future-readiness comments | Remote `MatchRepository` implementation |

**What did NOT change:**
- Scoring engine (`ScoreReducer`, `MatchState`, `ScoreEvent`, `BallEvent`) — untouched.
- All scoring, scorecard, timeline, camera, and streaming screens — untouched.
- Create Match flow — untouched (new `Match` fields all have defaults).
- `MatchViewModel`, `MatchSessionViewModel`, `LiveStreamViewModel` — untouched.

---

### 2026-03-07 – Player Picker Integration

**What changed:**
Replaced the basic `SavedPlayerPickerDialog` with a richer `PlayerPickerDialog` and integrated it throughout every player-entry flow in the app.  All flows now create reusable private `PlayerProfile` records when a new player is typed, and all picker interactions preserve the `sourceProfileId` link in the match-level `Player` snapshot so historical scorecards remain stable.

**Flows migrated:**

| Flow | Screen | Change |
|------|--------|--------|
| Team player setup | `PlayerSetupScreen` | Person-icon always visible; opens `PlayerPickerDialog`; creates profile on inline create; preserves `sourceProfileId` in `Player` snapshots |
| Saved team creation | `SavedTeamsScreen` | Same picker integration; `CreateSavedTeamDialog` now accepts `onCreatePlayer` to persist new profiles |
| Next batter selection (after wicket) | `ScoringScreen` | "Pick from saved players" button added; typing a new name also saves a `PlayerProfile` |
| Bowler selection (end of over) | `ScoringScreen` | Same as batter flow |
| Innings setup (openers + opening bowler) | `ScoringScreen` | Person-icon added to "Add batter" and "Add bowler" rows; opens `PlayerPickerDialog`; typing also saves profile |
| Add player during active match | `ScoringScreen` | "Pick from saved players" button added to `AddPlayerToMatchDialog`; typing also saves profile |

**Files created/modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/PlayerPickerDialog.kt` | Created – reusable `PlayerPickerDialog` composable |
| `app/src/main/java/com/example/scorebroadcaster/ui/PlayerSetupScreen.kt` | Updated – `PlayerPickerDialog`, `sourceProfileId` tracking, removed `SavedPlayerPickerDialog` |
| `app/src/main/java/com/example/scorebroadcaster/ui/SavedTeamsScreen.kt` | Updated – `PlayerPickerDialog`, `sourceProfileId` tracking, `onCreatePlayer` callback |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Updated – `savedPlayers`/`onSavePrivatePlayer` params; picker in `SelectPlayerDialog`, `AddPlayerToMatchDialog`, `SetupOpenersDialog` |
| `app/src/main/java/com/example/scorebroadcaster/MainActivity.kt` | Updated – collects `savedPlayers`; passes it + `onSavePrivatePlayer` to both `ScoringScreen` invocations |
| `README.md` | Updated |

**`PlayerPickerDialog` architecture:**

The new composable has three sections:

1. **Search field** — real-time prefix/substring filter over `savedPlayers`.
2. **Saved Players** — scrollable list of matching `PlayerProfile` entries; one tap selects and closes.
3. **Scored Users · coming soon** — clearly-labelled placeholder for future backend player search.  No backend is wired; the section is a static label so the UI slot is reserved without any behavioural cost.
4. **Create new player** — inline `OutlinedTextField` + Add button.  Tapping Add calls `onCreateAndSelect(PlayerProfile(displayName = name, playerSourceType = PRIVATE))`.  The caller persists the profile via `MatchSessionViewModel.addSavedPlayer`.

Two separate callbacks (`onSelect` for existing profiles, `onCreateAndSelect` for new ones) let every call-site decide whether to persist the profile, keeping the dialog stateless and reusable.

**Snapshot stability:**

- `PlayerSetupScreen` and `CreateSavedTeamDialog` now track a `HashMap<Int, String>` (slot index → `profileId`) alongside the name-string list.  The final `Player(name, sourceProfileId)` is built from both when the flow is confirmed.
- During active match play, `profile.toMatchPlayer()` is used to build the `Player` snapshot, retaining the `sourceProfileId` so future scorecard lookup can trace which profile a player came from.
- Subsequent edits to a `PlayerProfile` never affect existing `Player` snapshots — the three-copy model (profile → team snapshot → match player) is preserved.

**Scorer speed:**

- Existing team-roster players remain the primary one-tap path in `SelectPlayerDialog`.
- The "Pick from saved players" button and the "Create new player" inline entry are visually secondary (below a divider), so the common case (tap from team list) is unchanged.
- The person-icon in `PlayerSetupScreen` and `CreateSavedTeamDialog` is now always visible (previously hidden when the saved-player list was empty), so the scorer can always create a player without leaving the screen.

**What did NOT change:**
- `MatchViewModel` scoring engine, reducer, event log — untouched.
- `ScoreReducer`, `BallEvent`, `MatchState` — untouched.
- Existing team-player list in all dialogs remains the primary selection path.
- Camera preview, RTMP streaming, scorecard — untouched.

---

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
- *PlayerSetupScreen* — each player slot has a person-icon button (now always visible) that opens `PlayerPickerDialog` to fill the slot from a saved profile or create a new one inline.  Manual typing is still fully supported.
- *SavedTeamsScreen* `CreateSavedTeamDialog` — same person-icon button added to each player row so teams can be assembled from existing saved profiles or new inline creations.

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

### 2026-03-12 – UX Improvement: Wide and No Ball dialogs

**What changed:**
Wide and No Ball scoring now uses quick-select buttons instead of a multi-step form. The scorer taps a single button to record the full result — no confirmation step required.

**Key changes:**

- `WideNoBallEntryDialog` replaced with a quick-selection grid dialog:
  - Title shows "Wide" or "No Ball".
  - 7 buttons displayed in a 3-column grid: `Wd` / `Nb` through `Wd +6` / `Nb +6`.
  - Tapping a button immediately creates the `BallEvent`, dispatches it, and closes the dialog.
  - No confirmation button; no additional inputs.

- **Bye and Leg Bye** taps continue to open the existing `ExtrasEntryDialog` — behaviour unchanged.

**Scoring rules (unchanged):**
- Wide: `extras.wides = 1 + additionalRuns`, `countsAsBall = false`.
- No Ball: `extras.noBalls = 1`, `runsOffBat = additionalRuns`, `countsAsBall = false`.

**Files changed:**

| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Replaced `WideNoBallEntryDialog` with quick-select grid; simplified `buildWideNoBallEvent` signature; updated call site |
| `README.md` | Updated |

**What did NOT change:**
- `BallEvent` structure — untouched.
- `ScoreReducer` — untouched.
- Undo / timeline logic — untouched.
- Bye / Leg Bye `ExtrasEntryDialog` — untouched.

---

### 2026-03-10 – UI Improvement: Separate Wide / No Ball Extras Dialog

**What changed:**
Wide and No Ball now use a dedicated `WideNoBallEntryDialog` instead of the generic `ExtrasEntryDialog`.
The new dialog makes the automatic +1 extra run explicit and separates it from any additional runs taken by running.

**Key changes:**

- **Wide and No Ball** taps open `WideNoBallEntryDialog`:
  - Title shows "Wide" or "No Ball".
  - Supporting text: "Includes 1 automatic extra run" and "Add any additional runs taken by running".
  - Extra type selector limited to Wide / No Ball.
  - Additional runs selector (0 / 1 / 2 / 3 / 4 / 5+) — represents runs taken **after** the automatic +1, not the total.
  - 5+ reveals a free-text numeric input for additional runs.
  - Summary text near the Confirm button shows the total breakdown, e.g. "Total extras on this ball: 3 (1 wide + 2 runs)".
  - Wicket toggle and run-out detail section behave identically to the existing dialog.

- **Bye and Leg Bye** taps continue to open the existing `ExtrasEntryDialog` — behaviour unchanged.

**Event construction:**
- Wide: `extras.wides = 1 + additionalRuns`, `runsOffBat = 0`, `countsAsBall = false`.
- No Ball: `extras.noBalls = 1`, `runsOffBat = additionalRuns`, `countsAsBall = false` (same as before, but the UI now separates the automatic +1 visually).

**Files changed:**

| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Added `WideNoBallEntryDialog` composable and `buildWideNoBallEvent` helper; updated call site to route Wide/NoBall taps to the new dialog |
| `README.md` | Updated |

**What did NOT change:**
- Bye / Leg Bye behaviour — untouched.
- `ExtrasEntryDialog` and `buildExtrasEvent` — untouched.
- Wicket details logic — reused as-is.
- Reducer, scorecard, timeline, innings flow, camera / streaming — untouched.

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

### 2026-03-12 – Scoring UX Fix: Bye and Leg Bye dialogs

- Removed invalid scoring options from Bye and Leg Bye dialogs.
- Each dialog now only shows valid run options (1–6) in a compact 3-column grid.
- Bye dialog title: **Bye Runs**; buttons: B+1 … B+6.
- Leg Bye dialog title: **Leg Bye Runs**; buttons: LB+1 … LB+6.
- Wide, No Ball, Extra, and other scoring types no longer appear inside these dialogs.
- Improved scoring accuracy and simplified UI.
- New `ByeLegByeEntryDialog` composable and `buildByeLegByeEvent` helper added to `ScoringScreen.kt`.

---

### 2026-03-11 – Feature: Maiden Overs (corrected rule)

- Bowling stats now track maiden overs correctly.
- **Maiden defined as:** over with zero runs conceded by the bowler.
- Byes and leg-byes are **ignored** for bowler runs — they do not break a maiden.
- Wides and no-balls **do** count against the bowler.
- Maiden counts are derived from event history, so undo/edit/delete remain correct.
- `MaidenOverCalculator.kt` updated: uses `extras.wides + extras.noBalls` (not `extras.total`) for runs conceded, and exposes a `calculateBowlerMaidens(events, bowlerId)` convenience function.

---

### 2026-03-07 – Maiden Overs Tracking

**Maiden-over rule used:**
An over is a maiden when the bowler concedes **zero runs** during that over.
Runs conceded by the bowler: runs off the bat, wides, no-balls.
Runs **not** counted against the bowler: byes, leg-byes.
A wicket with no other runs does **not** break a maiden.
This rule is documented in `MaidenOverCalculator.kt`.

**Model updates:**
- `BallEvent` (`domain/BallEvent.kt`) gained a nullable `bowler: Player?` field (default `null`) so each delivery carries its bowler reference.  The field is backward-compatible — all existing `BallEvent` construction sites are unaffected because the field has a default value.
- `BowlingEntry` (`data/entity/BowlingEntry.kt`) already contained a `maidens: Int` field (previously always 0); it is now populated by the replay-based calculator.

**Replay-based calculation (`MaidenOverCalculator.kt`):**
A new pure-function object `MaidenOverCalculator.compute(events)` was created in `domain/`.  It walks the ordered event list, groups deliveries into completed overs (6 `countsAsBall == true` deliveries each), sums the total team runs for each completed over, and credits a maiden to the bowler of any over with 0 runs.  Because the function is stateless and re-runs over the full event log, it is automatically correct after undo, ball edits, and ball deletes — no mutable counters are involved.

**ViewModel integration (`MatchViewModel.kt`):**
- `addBallEvent` now stamps `bowler = currentBowler` onto every event before appending it to the log.
- `updateConsoleAfterEvent` calls `MaidenOverCalculator.compute` after each delivery and writes the fresh maiden counts into `allBowlingEntries` and `currentBowlerEntry`.
- `undo()` calls the new `refreshMaidensFromEvents` helper to keep maiden counts accurate when a ball is rolled back.
- `replaceBallEvent` and `deleteBallEvent` also call `refreshMaidensFromEvents` after modifying the current-innings log.
- `rebuildFirstInningsSnapshot` now also recomputes maiden counts for `firstInningsBowlingEntries` using the first-innings event log, so the scorecard remains correct when first-innings balls are edited.

**Scorecard bowling table (`ScorecardScreen.kt`):**
The bowling table columns were updated from `O R W Econ` to `O M R W Econ`.
`BowlingTableHeader` and `BowlingTableRow` both now include the maiden column.

**Current-bowler summary card (`ScoringScreen.kt`):**
`BowlerRow` now displays figures in the compact format `overs.balls-maidens-runs-wickets`
(e.g. `3.0-1-12-2`), consistent with traditional cricket bowling-figures notation.

**Files created:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/domain/MaidenOverCalculator.kt` | Created |

**Files modified:**
| File | Change |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/domain/BallEvent.kt` | Added `bowler: Player?` field |
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/MatchViewModel.kt` | Bowler stamping, maiden derivation, refresh helpers |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScorecardScreen.kt` | Added M column to bowling table |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | Updated BowlerRow display format |
| `README.md` | This entry |

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

### 2026-03-10 – Run Out Dismissal Now Supports Two Fielders

**Feature:** Run Out dismissal now supports one or two fielders. Scorecards render as `"run out (Fielder)"` or `"run out (Fielder / Fielder)"` to match real cricket scoring conventions.

**Files modified:**
| File | Change |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/data/entity/DismissalDetail.kt` | Replaced `fielder: Player?` with `fielders: List<Player>`; updated `toScorecardString()` for Run Out to join up to two fielder names with ` / ` |
| `app/src/main/java/com/example/scorebroadcaster/data/local/BallEventEntity.kt` | Added `fielder2Name` column; updated `toDomain()` and `toEntity()` mapping helpers |
| `app/src/main/java/com/example/scorebroadcaster/data/local/ScoredDatabase.kt` | Bumped schema version to 7 |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoringScreen.kt` | `WicketDetailsDialog` now shows a second optional fielder selector for Run Out; `buildExtrasEvent` updated to use `fielders` list |
| `app/src/main/java/com/example/scorebroadcaster/ui/EditBallDialog.kt` | Updated to use `fielders` list in `buildEvent()` and `buildExtrasEventForEdit()` |
| `README.md` | Added this log entry |

**What was added/refactored:**

- **`DismissalDetail.fielders`** — replaces the single `fielder: Player?` with `fielders: List<Player>`. Rules: Bowled/LBW → empty list; Caught/Stumped → one player; Run Out → one or two players.
- **`toScorecardString()` update** — Run Out now renders as `"run out (Smith)"` or `"run out (Smith / Khan)"` using `joinToString(" / ")`.
- **`WicketDetailsDialog` update** — When dismissal type is Run Out, the dialog shows a required "Fielder 1" selector and an optional "+ Add second fielder" button. Selecting a second fielder shows a "Fielder 2" selector (excluding the already-chosen Fielder 1). The second fielder can be removed with a "Remove second fielder" button.
- **DB schema v7** — New `fielder2Name` column in `ball_events` stores the optional second fielder's name for Run Out dismissals.

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

### 2026-03-07 – Exclude Selected Saved Team From Opposite Team Dropdown

**Feature:** Saved-team dropdown options are now mutually exclusive between Team A and Team B.

**Behaviour:**
- Once a saved team is selected for Team A, it no longer appears in Team B's dropdown list, and vice versa.
- If the user clears a saved-team selection by typing freely, that team immediately becomes available again in the other side's dropdown.
- If the user changes Team A's selection from one saved team to another, the old team becomes available in Team B's list and the new one is excluded.
- A newly created saved team that is auto-selected for one side is immediately excluded from the other side's dropdown.
- When all other saved teams are excluded (e.g. only one saved team exists and it is selected on the opposite side), a disabled info row "No other saved teams available" is shown at the top of the dropdown.
- Free typing is completely unaffected — users can still type any custom team name manually.
- Existing same-team-name validation and disabled Next button logic are unchanged.

**Files modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/CreateMatchScreen.kt` | Added `teamASelectedSaved`/`teamBSelectedSaved` state; added `excludedTeam` parameter to `TeamSelectorField`; filter available teams by exclusion; show "No other saved teams available" info row |
| `README.md` | Added this log entry |

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

### 2026-03-09 – UI Improvement: TV-Style Broadcast Score Overlay

**Feature:** Redesigned the live scoreboard overlay to match a professional TV cricket broadcast lower-third graphic.

**Files created/modified:**
| File | Action |
|------|--------|
| `app/src/main/java/com/example/scorebroadcaster/ui/BroadcastOverlayMapper.kt` | Created |
| `app/src/main/java/com/example/scorebroadcaster/ui/ScoreboardOverlay.kt` | Rewritten |
| `app/src/main/java/com/example/scorebroadcaster/ui/CameraPreviewScreen.kt` | Updated |
| `app/src/main/java/com/example/scorebroadcaster/streaming/ScoreboardOverlayRenderer.kt` | Rewritten |
| `app/src/main/java/com/example/scorebroadcaster/viewmodel/LiveStreamViewModel.kt` | Updated |
| `app/src/main/java/com/example/scorebroadcaster/ui/StreamPreviewScreen.kt` | Updated |
| `README.md` | Updated |

**Explanation:**

The scoreboard overlay was replaced with a compact landscape-optimised lower-third in the style of live cricket broadcasts on television.

**`BroadcastOverlayMapper`** (`ui/BroadcastOverlayMapper.kt`) — a new shared mapper object that converts a `MatchState` + `ScoringConsoleState` pair into a `BroadcastOverlayModel` (UI-only data class). The mapper returns `null` during `InningsPhase.SETUP` to hide the overlay before play starts, and computes a context line: run rate (`"RUN RATE 6.21"`) for the first innings, a chase line (`"Need 23 from 15"`) for the second innings using `matchOvers` to compute remaining balls, a target announcement (`"Target: 201"`) at the innings break, and a result line at `MATCH_COMPLETE`. This logic is shared by both the Compose overlay and the Canvas stream renderer.

**Batter section** (left column): Displays the striker and non-striker with name, runs, and balls in the format `57 (60)`. A small amber dot sits next to the striker's name. Names are ellipsised when too long to preserve the fixed layout.

**Center score block** (middle column): Shows the match title (`LIO v FAL`), the current score in a large amber font (`177-2`), the over count (`28.5 overs`), and a small blue innings badge (`1st` / `2nd`).

**Bowler section** (right column): Shows the bowler's name and figures (`1-18`) on one line and a row of small colour-coded ball circles below — red fill for wickets, blue fill for boundaries, outline only for dot balls, and grey fill for scored runs. `Wd` and `NB` deliveries are abbreviated to `W+` / `N+` to fit the circle.

**Context strip** (full-width thin bar below the main row): Shows the run rate (`RUN RATE 6.21`) during the first innings and the chase requirement (`Need 23 from 15`) during the second innings. Hidden when no data is available (e.g. at the innings break the strip shows the target instead).

**`ScoreboardOverlay` composable** signature extended with `console: ScoringConsoleState` and `matchOvers: Int?` optional parameters; the old single-parameter form still compiles without changes. `CameraPreviewScreen` now collects `matchViewModel.consoleState` and `matchViewModel.activeMatch` (for the over count) and forwards both to the overlay.

**`ScoreboardOverlayRenderer`** (Canvas / RTMP path) rewritten to draw the same three-column layout at 1280 × 190 px using Android Paint. Sections adapt when batter or bowler data is absent. The `render()` signature adds optional `console` and `matchOvers` parameters; callers that pass only `MatchState` continue to work.

**`LiveStreamViewModel.startStreaming`** now accepts an optional `consoleStateFlow` and `matchOvers` and combines the two flows with `kotlinx.coroutines.flow.combine` before debouncing, so both state streams feed the overlay renderer. `StreamPreviewScreen` was updated to pass these values.

#### Overlay Redesign Log – Layout Compression

- **Overlay height reduced**: Compose overlay compressed to ≤ ~110 dp (was uncapped and grew with content); Canvas renderer default reduced from 1280 × 190 px to 1280 × 130 px, keeping the bar within 12–16 % of a 720-line stream frame.
- **Layout compressed to two rows**: The previous three-column bar + full-width context strip was replaced with a two-row structure. Row 1 carries the main scoreboard (batters left, match title/score/overs centre, bowler right). Row 2 carries secondary info (ball-by-ball circles left, run rate or chase info centre, innings indicator right). This eliminates the extra height consumed by the standalone context strip.
- **Less screen coverage for better camera visibility**: Stacked inner section cards (each with their own background and padding) were removed in favour of a single rounded-corner container, vertical padding reduced to 6 dp (top/bottom) and 12 dp (sides), inter-row gap set to 4 dp, and typography scaled down (score 20 sp, player names 13 sp, secondary labels 10–11 sp). Ball indicator circles reduced to 11 dp with no text labels. Together these changes leave the majority of the camera preview unobscured.

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

