package com.example.scorebroadcaster.domain

import com.example.scorebroadcaster.data.MatchState

/**
 * Pure functional scoring reducer.
 *
 * Folds a list of [BallEvent]s into a [MatchState]. Because this is a pure function with no
 * side-effects, replaying the full event log always produces the canonical match state — which
 * makes undo trivial (drop the last event and re-reduce).
 */
fun reduce(events: List<BallEvent>): MatchState =
    events.fold(MatchState()) { state, event -> applyEvent(state, event) }

private fun applyEvent(state: MatchState, event: BallEvent): MatchState {
    val (overs, balls) = if (event.countsAsBall) {
        incrementBall(state.overs, state.balls)
    } else {
        Pair(state.overs, state.balls)
    }

    val totalRuns = event.runsOffBat + event.extras.total

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
        lastBalls = updateLastBalls(state.lastBalls, buildBallLabel(event))
    )
}

/** Generates a short human-readable label for the over-summary strip. */
private fun buildBallLabel(event: BallEvent): String = when {
    event.wicket -> "W"
    event.extras.wides > 0 -> "Wd"
    event.extras.noBalls > 0 -> "NB"
    event.extras.byes > 0 -> "B${event.extras.byes}"
    event.extras.legByes > 0 -> "LB${event.extras.legByes}"
    else -> event.runsOffBat.toString()
}

private fun incrementBall(overs: Int, balls: Int): Pair<Int, Int> =
    if (balls + 1 >= 6) Pair(overs + 1, 0) else Pair(overs, balls + 1)

private fun updateLastBalls(lastBalls: List<String>, newBall: String): List<String> =
    (lastBalls + newBall).takeLast(6)
