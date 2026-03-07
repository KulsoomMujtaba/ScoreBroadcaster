package com.example.scorebroadcaster.data.entity

/**
 * Breakdown of extras conceded on a single delivery.
 *
 * Only one of [wides], [noBalls], [byes], or [legByes] will be non-zero for any given ball,
 * as a delivery can only be one type of extra. However, the data class is flexible enough to
 * represent combined outcomes such as a run-out on a no-ball.
 *
 * @param wides    Wide penalty runs (including the 1-run penalty itself).
 * @param noBalls  No-ball penalty runs (the 1-run penalty only; batter runs are in [BallEvent.runsOffBat]).
 * @param byes     Bye runs scored (not credited to the batter).
 * @param legByes  Leg-bye runs scored (not credited to the batter).
 */
data class ExtrasBreakdown(
    val wides: Int = 0,
    val noBalls: Int = 0,
    val byes: Int = 0,
    val legByes: Int = 0
) {
    /** Total extras from this delivery. */
    val total: Int get() = wides + noBalls + byes + legByes

    companion object {
        val NONE = ExtrasBreakdown()
    }
}
