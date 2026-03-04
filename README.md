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
