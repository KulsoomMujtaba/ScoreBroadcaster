package com.example.scorebroadcaster

import com.example.scorebroadcaster.data.ScoreEvent
import com.example.scorebroadcaster.domain.reduce
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreReducerTest {

    // --- Run events ---

    @Test
    fun `run adds runs and increments ball count`() {
        val state = reduce(listOf(ScoreEvent.Run(4)))
        assertEquals(4, state.runs)
        assertEquals(1, state.balls)
        assertEquals(0, state.overs)
    }

    @Test
    fun `six legal balls increments over and resets balls`() {
        val events = List(6) { ScoreEvent.Run(1) }
        val state = reduce(events)
        assertEquals(1, state.overs)
        assertEquals(0, state.balls)
    }

    @Test
    fun `seven legal balls gives one over and one ball`() {
        val events = List(7) { ScoreEvent.Run(1) }
        val state = reduce(events)
        assertEquals(1, state.overs)
        assertEquals(1, state.balls)
    }

    // --- Wicket events ---

    @Test
    fun `wicket increments wickets and ball count`() {
        val state = reduce(listOf(ScoreEvent.Wicket))
        assertEquals(1, state.wickets)
        assertEquals(1, state.balls)
        assertEquals(0, state.runs)
    }

    // --- Wide events ---

    @Test
    fun `wide adds one run as extra and does NOT count as legal ball`() {
        val state = reduce(listOf(ScoreEvent.Wide(0)))
        assertEquals(1, state.runs)
        assertEquals(1, state.extras)
        assertEquals(0, state.balls) // not a legal delivery
    }

    @Test
    fun `wide with extra runs adds correct total`() {
        val state = reduce(listOf(ScoreEvent.Wide(3)))
        assertEquals(4, state.runs)   // 3 + 1 penalty
        assertEquals(4, state.extras)
        assertEquals(0, state.balls)
    }

    @Test
    fun `six wides do not complete an over`() {
        val events = List(6) { ScoreEvent.Wide(0) }
        val state = reduce(events)
        assertEquals(0, state.overs)
        assertEquals(0, state.balls)
    }

    // --- NoBall events ---

    @Test
    fun `no-ball adds one extra and does NOT count as legal ball`() {
        val state = reduce(listOf(ScoreEvent.NoBall(0)))
        assertEquals(1, state.runs)
        assertEquals(1, state.extras)
        assertEquals(0, state.balls)
    }

    @Test
    fun `no-ball with batter runs: batter runs added to total, only 1 extra`() {
        val state = reduce(listOf(ScoreEvent.NoBall(4)))
        assertEquals(5, state.runs)   // 4 batter + 1 penalty
        assertEquals(1, state.extras) // only the penalty is an extra
        assertEquals(0, state.balls)
    }

    // --- Bye events ---

    @Test
    fun `bye adds runs as extras and counts as legal ball`() {
        val state = reduce(listOf(ScoreEvent.Bye(4)))
        assertEquals(4, state.runs)
        assertEquals(4, state.extras)
        assertEquals(1, state.balls)
    }

    // --- LegBye events ---

    @Test
    fun `leg-bye adds runs as extras and counts as legal ball`() {
        val state = reduce(listOf(ScoreEvent.LegBye(2)))
        assertEquals(2, state.runs)
        assertEquals(2, state.extras)
        assertEquals(1, state.balls)
    }

    // --- Mixed over completion ---

    @Test
    fun `wides and no-balls mixed with legal balls – over counts only legal deliveries`() {
        // 3 wides + 3 no-balls + 6 legal runs = 1 over completed
        val events: List<ScoreEvent> =
            List(3) { ScoreEvent.Wide(0) } +
            List(3) { ScoreEvent.NoBall(0) } +
            List(6) { ScoreEvent.Run(1) }
        val state = reduce(events)
        assertEquals(1, state.overs)
        assertEquals(0, state.balls)
    }

    // --- lastBalls ---

    @Test
    fun `lastBalls tracks last 6 deliveries including illegals`() {
        val events: List<ScoreEvent> = listOf(
            ScoreEvent.Run(1),
            ScoreEvent.Wide(0),
            ScoreEvent.NoBall(0),
            ScoreEvent.Run(4),
            ScoreEvent.Wicket,
            ScoreEvent.Bye(1),
            ScoreEvent.LegBye(2)
        )
        val state = reduce(events)
        // Only last 6 entries are retained
        assertEquals(6, state.lastBalls.size)
        assertEquals(listOf("Wd", "NB", "4", "W", "B1", "LB2"), state.lastBalls)
    }

    // --- Undo ---

    @Test
    fun `undo removes last event and recomputes state`() {
        val events = mutableListOf<ScoreEvent>(
            ScoreEvent.Run(4),
            ScoreEvent.Run(6)
        )
        val stateAfterTwo = reduce(events)
        assertEquals(10, stateAfterTwo.runs)

        val stateAfterUndo = reduce(events.dropLast(1))
        assertEquals(4, stateAfterUndo.runs)
        assertEquals(1, stateAfterUndo.balls)
    }

    @Test
    fun `undo on empty event list returns default MatchState`() {
        val state = reduce(emptyList())
        assertEquals(0, state.runs)
        assertEquals(0, state.wickets)
        assertEquals(0, state.overs)
        assertEquals(0, state.balls)
    }

    // --- Initial state ---

    @Test
    fun `initial state has correct defaults`() {
        val state = reduce(emptyList())
        assertEquals("Team A", state.teamAName)
        assertEquals("Team B", state.teamBName)
        assertEquals(0, state.runs)
        assertEquals(0, state.wickets)
        assertEquals(0, state.overs)
        assertEquals(0, state.balls)
        assertEquals(0, state.extras)
        assertEquals(emptyList<String>(), state.lastBalls)
    }
}
