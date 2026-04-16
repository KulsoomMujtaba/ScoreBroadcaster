package com.devhub.scored.features.scoring.ui
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
     * Returns an overs string in cricket notation.
     * Complete overs are shown as a plain number (e.g. "4"); partial overs include
     * the ball count after a decimal (e.g. "2.3").
     */
    fun formatOvers(overs: Int, balls: Int): String = if (balls == 0) "$overs" else "$overs.$balls"

    /**
     * Returns the run rate (runs per over) as a formatted string rounded to 2 decimal
     * places (e.g. "8.42"). Returns "-" when no legal deliveries have been bowled.
     *
     * Uses the same over-fraction logic as [formatEconomy]: one over = 6 balls.
     */
    fun formatRunRate(runs: Int, overs: Int, balls: Int): String {
        val totalBalls = overs * 6 + balls
        if (totalBalls == 0) return "-"
        return "%.2f".format(runs * 6.0 / totalBalls)
    }

    /**
     * Returns the required run rate (runs per over needed to win) as a formatted string
     * rounded to 2 decimal places (e.g. "9.75"). Returns "-" when no balls remain or the
     * target is already reached.
     *
     * @param runsNeeded       Runs still required to reach/exceed the target.
     * @param ballsRemaining   Legal-delivery balls left in the innings.
     */
    fun formatRequiredRunRate(runsNeeded: Int, ballsRemaining: Int): String {
        if (ballsRemaining <= 0 || runsNeeded <= 0) return "-"
        return "%.2f".format(runsNeeded * 6.0 / ballsRemaining)
    }
}
