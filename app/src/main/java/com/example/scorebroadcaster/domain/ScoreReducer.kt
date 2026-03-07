package com.example.scorebroadcaster.domain

import com.example.scorebroadcaster.data.MatchState
import com.example.scorebroadcaster.data.ScoreEvent

fun reduce(events: List<ScoreEvent>): MatchState =
    events.fold(MatchState()) { state, event -> applyEvent(state, event) }

private fun applyEvent(state: MatchState, event: ScoreEvent): MatchState = when (event) {
    is ScoreEvent.Run -> {
        val (overs, balls) = incrementBall(state.overs, state.balls)
        state.copy(
            runs = state.runs + event.runs,
            overs = overs,
            balls = balls,
            lastBalls = updateLastBalls(state.lastBalls, event.runs.toString())
        )
    }
    is ScoreEvent.Wicket -> {
        val (overs, balls) = incrementBall(state.overs, state.balls)
        state.copy(
            wickets = state.wickets + 1,
            overs = overs,
            balls = balls,
            lastBalls = updateLastBalls(state.lastBalls, "W")
        )
    }
    is ScoreEvent.Wide -> {
        // Wides do not count as legal deliveries
        state.copy(
            runs = state.runs + event.runs + 1,
            extras = state.extras + event.runs + 1,
            wides = state.wides + event.runs + 1,
            lastBalls = updateLastBalls(state.lastBalls, "Wd")
        )
    }
    is ScoreEvent.NoBall -> {
        // No-balls do not count as legal deliveries; batter's runs (if any) added normally
        state.copy(
            runs = state.runs + event.runs + 1,
            extras = state.extras + 1,
            noBalls = state.noBalls + 1,
            lastBalls = updateLastBalls(state.lastBalls, "NB")
        )
    }
    is ScoreEvent.Bye -> {
        val (overs, balls) = incrementBall(state.overs, state.balls)
        state.copy(
            runs = state.runs + event.runs,
            extras = state.extras + event.runs,
            byes = state.byes + event.runs,
            overs = overs,
            balls = balls,
            lastBalls = updateLastBalls(state.lastBalls, "B${event.runs}")
        )
    }
    is ScoreEvent.LegBye -> {
        val (overs, balls) = incrementBall(state.overs, state.balls)
        state.copy(
            runs = state.runs + event.runs,
            extras = state.extras + event.runs,
            legByes = state.legByes + event.runs,
            overs = overs,
            balls = balls,
            lastBalls = updateLastBalls(state.lastBalls, "LB${event.runs}")
        )
    }
}

private fun incrementBall(overs: Int, balls: Int): Pair<Int, Int> =
    if (balls + 1 >= 6) Pair(overs + 1, 0) else Pair(overs, balls + 1)

private fun updateLastBalls(lastBalls: List<String>, newBall: String): List<String> =
    (lastBalls + newBall).takeLast(6)
