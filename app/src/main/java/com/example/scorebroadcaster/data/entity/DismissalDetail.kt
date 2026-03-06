package com.example.scorebroadcaster.data.entity

/**
 * Full details about a wicket dismissal.
 *
 * @param batter     The player who was dismissed.
 * @param dismissalType  How the batter was dismissed.
 * @param fielder    Optional fielder involved (catcher, wicketkeeper, or run-out fielder).
 * @param bowler     The bowler at the time of dismissal (may be null for run outs from earlier).
 */
data class DismissalDetail(
    val batter: Player,
    val dismissalType: DismissalType,
    val fielder: Player? = null,
    val bowler: Player? = null
) {
    /**
     * Whether this dismissal is credited to the bowler.
     * Run Out dismissals do NOT credit the bowler; all others do.
     */
    val bowlerCredited: Boolean get() = dismissalType != DismissalType.RUN_OUT

    /**
     * Human-readable scorecard string following cricket convention, e.g.:
     * - "b Smith"
     * - "c Jones b Smith"
     * - "lbw b Smith"
     * - "st Brown b Smith"
     * - "run out (Jones)"
     */
    fun toScorecardString(): String = when (dismissalType) {
        DismissalType.BOWLED ->
            "b ${bowler?.name ?: "?"}"
        DismissalType.CAUGHT -> when {
            fielder != null && bowler != null -> "c ${fielder.name} b ${bowler.name}"
            fielder != null -> "c ${fielder.name}"
            bowler != null -> "c & b ${bowler.name}"
            else -> "caught"
        }
        DismissalType.LBW ->
            "lbw b ${bowler?.name ?: "?"}"
        DismissalType.STUMPED ->
            "st ${fielder?.name ?: "?"} b ${bowler?.name ?: "?"}"
        DismissalType.RUN_OUT ->
            if (fielder != null) "run out (${fielder.name})" else "run out"
        DismissalType.OTHER ->
            "out"
    }
}
