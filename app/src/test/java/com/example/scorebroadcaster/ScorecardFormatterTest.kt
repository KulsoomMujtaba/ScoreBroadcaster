package com.example.scorebroadcaster

import com.example.scorebroadcaster.ui.ScorecardFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class ScorecardFormatterTest {

    // -------------------------------------------------------------------------
    // formatOvers
    // -------------------------------------------------------------------------

    @Test
    fun formatOvers_zeroBalls_showsOnlyOvers() {
        assertEquals("0", ScorecardFormatter.formatOvers(0, 0))
        assertEquals("4", ScorecardFormatter.formatOvers(4, 0))
        assertEquals("10", ScorecardFormatter.formatOvers(10, 0))
    }

    @Test
    fun formatOvers_partialOver_showsOversAndBalls() {
        assertEquals("0.1", ScorecardFormatter.formatOvers(0, 1))
        assertEquals("2.3", ScorecardFormatter.formatOvers(2, 3))
        assertEquals("10.5", ScorecardFormatter.formatOvers(10, 5))
    }

    // -------------------------------------------------------------------------
    // formatRunRate
    // -------------------------------------------------------------------------

    @Test
    fun formatRunRate_zeroBalls_returnsDash() {
        assertEquals("-", ScorecardFormatter.formatRunRate(0, 0, 0))
        assertEquals("-", ScorecardFormatter.formatRunRate(50, 0, 0))
    }

    @Test
    fun formatRunRate_typicalFirstInnings() {
        // 84 runs from 60 balls (10 overs) → 8.40
        assertEquals("8.40", ScorecardFormatter.formatRunRate(84, 10, 0))
    }

    @Test
    fun formatRunRate_partialOver() {
        // 50 runs from 33 balls (5 overs + 3 balls) → 50*6/33 ≈ 9.09
        assertEquals("9.09", ScorecardFormatter.formatRunRate(50, 5, 3))
    }

    @Test
    fun formatRunRate_zeroRuns() {
        // 0 runs from 6 balls → 0.00
        assertEquals("0.00", ScorecardFormatter.formatRunRate(0, 1, 0))
    }

    // -------------------------------------------------------------------------
    // formatRequiredRunRate
    // -------------------------------------------------------------------------

    @Test
    fun formatRequiredRunRate_zeroBallsRemaining_returnsDash() {
        assertEquals("-", ScorecardFormatter.formatRequiredRunRate(50, 0))
    }

    @Test
    fun formatRequiredRunRate_zeroRunsNeeded_returnsDash() {
        assertEquals("-", ScorecardFormatter.formatRequiredRunRate(0, 30))
    }

    @Test
    fun formatRequiredRunRate_negativeRunsNeeded_returnsDash() {
        // Target already exceeded
        assertEquals("-", ScorecardFormatter.formatRequiredRunRate(-5, 30))
    }

    @Test
    fun formatRequiredRunRate_typicalChase() {
        // Need 58 runs from 36 balls (6 overs) → 58*6/36 ≈ 9.67
        assertEquals("9.67", ScorecardFormatter.formatRequiredRunRate(58, 36))
    }

    @Test
    fun formatRequiredRunRate_partialOvers() {
        // Need 20 runs from 13 balls → 20*6/13 ≈ 9.23
        assertEquals("9.23", ScorecardFormatter.formatRequiredRunRate(20, 13))
    }
}
