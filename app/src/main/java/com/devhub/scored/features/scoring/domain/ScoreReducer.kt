package com.devhub.scored.features.scoring.domain
import android.util.Log
import com.devhub.scored.features.scoring.data.MatchState

private const val TAG = "ScoreReducer"

/**
 * Pure functional scoring reducer.
 *
 * Folds a list of [BallEvent]s into a [MatchState]. Because this is a pure function with no
 * side-effects, replaying the full event log always produces the canonical match state — which
 * makes undo trivial (drop the last event and re-reduce).
 *
 * @param events   Ordered list of ball events for the current innings.
 * @param maxOvers Maximum overs allowed for this innings (e.g. 10 for T10, 20 for T20).
 *                 Pass 0 (default) to disable the limit — used by the viewer and for
 *                 first-innings snapshot calculations where enforcement is not needed.
 */
fun reduce(events: List<BallEvent>, maxOvers: Int = 0): MatchState =
    events.fold(MatchState()) { state, event -> applyEvent(state, event, maxOvers) }

private fun applyEvent(state: MatchState, event: BallEvent, maxOvers: Int): MatchState {
    // Penalty runs are credited directly to the team total with no delivery mechanics.
    if (event.isPenalty) {
        Log.d(TAG, "Penalty runs added: ${event.runsOffBat}")
        return state.copy(
            runs = state.runs + event.runsOffBat,
            penaltyRuns = state.penaltyRuns + event.runsOffBat
        )
    }

    val (overs, balls) = if (event.countsAsBall) {
        incrementBall(state.overs, state.balls)
    } else {
        Pair(state.overs, state.balls)
    }

    val newTotalBalls = overs * 6 + balls
    // isInningsOver is true when a limit is configured (maxOvers > 0) and the total
    // legal deliveries for this innings have reached that limit.  Using `maxOvers > 0`
    // as the guard avoids any arithmetic with sentinel values for the "no limit" case.
    val isInningsOver = maxOvers > 0 && newTotalBalls >= maxOvers * 6

    if (isInningsOver && !state.isInningsOver) {
        Log.d(TAG, "Overs limit reached. Total balls: $newTotalBalls / Max balls: ${maxOvers * 6}")
    }

    val totalRuns = event.runsOffBat + event.extras.total

    val isExtra = event.extras.total > 0
    if (isExtra) {
        val extraType = when {
            event.extras.wides   > 0 -> "WIDE"
            event.extras.noBalls > 0 -> "NO_BALL"
            event.extras.byes    > 0 -> "BYE"
            else                     -> "LEG_BYE"
        }
        val extraRuns = when {
            event.extras.wides   > 0 -> event.extras.wides - 1
            event.extras.noBalls > 0 -> event.runsOffBat
            event.extras.byes    > 0 -> event.extras.byes
            else                     -> event.extras.legByes
        }
        Log.d(TAG, "Extra event: type=$extraType, runs=$extraRuns")
        Log.d(TAG, "Total runs updated to ${state.runs + totalRuns}")
    }

    return state.copy(
        runs = state.runs + totalRuns,
        wickets = if (event.wicket) state.wickets + 1 else state.wickets,
        overs = overs,
        balls = balls,
        extras = state.extras + event.extras.total,
        wides = state.wides + event.extras.wides,
        noBalls = state.noBalls + event.extras.noBalls,
        byes = state.byes + event.extras.byes,
        legByes = state.legByes + event.extras.legByes,
        lastBalls = updateLastBalls(state.lastBalls, buildBallLabel(event)),
        isInningsOver = isInningsOver
    )
}

/** Generates a short human-readable label for the over-summary strip. */
private fun buildBallLabel(event: BallEvent): String = when {
    event.wicket -> "W"
    event.extras.wides > 0 -> {
        val extra = event.extras.wides - 1
        if (extra > 0) "Wd+$extra" else "Wd"
    }
    event.extras.noBalls > 0 -> {
        if (event.runsOffBat > 0) "Nb+${event.runsOffBat}" else "Nb"
    }
    event.extras.byes > 0 -> "B${event.extras.byes}"
    event.extras.legByes > 0 -> "Lb${event.extras.legByes}"
    else -> event.runsOffBat.toString()
}

private fun incrementBall(overs: Int, balls: Int): Pair<Int, Int> =
    if (balls + 1 >= 6) Pair(overs + 1, 0) else Pair(overs, balls + 1)

private fun updateLastBalls(lastBalls: List<String>, newBall: String): List<String> =
    (lastBalls + newBall).takeLast(6)
