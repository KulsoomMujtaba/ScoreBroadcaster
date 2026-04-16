package com.devhub.scored.features.scoring.data

import com.devhub.scored.features.players.data.Player
 /*
 * @param batter        The player who was dismissed.
 * @param dismissalType How the batter was dismissed.
 * @param fielders      Fielders involved in the dismissal.
 *                      Empty for Bowled/LBW; one player for Caught/Stumped;
 *                      one or two players for Run Out.
 * @param bowler        The bowler at the time of dismissal (may be null for run outs from earlier).
 */
data class DismissalDetail(
    val batter: Player,
    val dismissalType: DismissalType,
    val fielders: List<Player> = emptyList(),
    val bowler: Player? = null
) {
    /**
     * Whether this dismissal is credited to the bowler.
     * Run Out and Obstructing the Field dismissals do NOT credit the bowler; all others do.
     */
    val bowlerCredited: Boolean get() = dismissalType != DismissalType.RUN_OUT &&
            dismissalType != DismissalType.OBSTRUCTING_FIELD

    /**
     * Human-readable scorecard string following cricket convention, e.g.:
     * - "b Smith"
     * - "c Jones b Smith"
     * - "lbw b Smith"
     * - "st Brown b Smith"
     * - "run out (Jones)"
     * - "run out (Jones / Khan)"
     */
    fun toScorecardString(): String = when (dismissalType) {
        DismissalType.BOWLED ->
            "b ${bowler?.name ?: "?"}"
        DismissalType.CAUGHT -> {
            val fielder = fielders.firstOrNull()
            when {
                fielder != null && bowler != null -> "c ${fielder.name} b ${bowler.name}"
                fielder != null -> "c ${fielder.name}"
                bowler != null -> "c & b ${bowler.name}"
                else -> "caught"
            }
        }
        DismissalType.LBW ->
            "lbw b ${bowler?.name ?: "?"}"
        DismissalType.STUMPED ->
            "st ${fielders.firstOrNull()?.name ?: "?"} b ${bowler?.name ?: "?"}"
        DismissalType.RUN_OUT ->
            if (fielders.isNotEmpty()) {
                val fielderText = fielders.joinToString(" / ") { it.name }
                "run out ($fielderText)"
            } else "run out"
        DismissalType.HIT_WICKET ->
            "hit wicket b ${bowler?.name ?: "?"}"
        DismissalType.OBSTRUCTING_FIELD ->
            "obstructing field"
        DismissalType.OTHER ->
            "out"
    }
}
