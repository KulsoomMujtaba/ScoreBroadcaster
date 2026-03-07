package com.example.scorebroadcaster.ui

/**
 * Pure formatting helpers for the cricket scorecard.
 * All functions are stateless and free of composable or Android dependencies.
 */
object ScorecardFormatter {

    /**
     * Returns the strike rate as a formatted string (e.g. "133.3").
     * Returns "-" when [balls] is zero.
     */
    fun formatStrikeRate(runs: Int, balls: Int): String {
        if (balls == 0) return "-"
        return "%.1f".format(runs * 100.0 / balls)
    }

    /**
     * Returns the bowling economy rate (runs per over) as a formatted string (e.g. "6.50").
     * Returns "-" when no legal deliveries have been bowled.
     */
    fun formatEconomy(runs: Int, overs: Int, balls: Int): String {
        val totalBalls = overs * 6 + balls
        if (totalBalls == 0) return "-"
        return "%.2f".format(runs * 6.0 / totalBalls)
    }

    /**
     * Returns an overs string in cricket notation (e.g. "10.3" meaning 10 complete overs
     * plus 3 balls of the next over).
     */
    fun formatOvers(overs: Int, balls: Int): String = "$overs.$balls"
}
