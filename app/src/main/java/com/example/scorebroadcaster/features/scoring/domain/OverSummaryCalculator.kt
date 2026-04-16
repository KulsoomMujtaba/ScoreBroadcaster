package com.example.scorebroadcaster.features.scoring.domain
import com.example.scorebroadcaster.features.scoring.data.DismissalType

/**
 * Derives [OverSummary] objects from an ordered [BallEvent] log.
 *
 * This calculator is a thin wrapper around [BallTimelineFormatter.groupByOver] that ensures
 * over summaries are always derived from the event log rather than stored incrementally.
 * This guarantees correctness after undo, ball edits, and ball deletions.
 *
 * ## Ball label format
 * Each delivery is represented as a compact label string:
 *  - `"0"`      — dot ball
 *  - `"1"` … `"6"` — runs off bat
 *  - `"W"`      — wicket (bowled / caught / lbw / stumped)
 *  - `"1W"`     — wicket with runs (e.g. run-out with 1 run)
 *  - `"Wd"`     — wide
 *  - `"Nb"`     — no-ball
 *  - `"B1"`     — 1 bye (or `"B2"`, etc.)
 *  - `"Lb1"`    — 1 leg-bye (or `"Lb2"`, etc.)
 *
 * ## Maiden rule
 * An over is a maiden when [BallEvent.runsOffBat] + [ExtrasBreakdown.wides] +
 * [ExtrasBreakdown.noBalls] == 0 across all deliveries in a completed over.
 * Byes and leg-byes are excluded because they are not charged to the bowler.
 */
object OverSummaryCalculator {

    /**
     * Derives a list of [OverSummary] objects from an ordered [BallEvent] log.
     *
     * Delegates grouping logic to [BallTimelineFormatter.groupByOver] so over boundaries,
     * bowler attribution, run totals, and maiden detection are all consistent with the
     * live scoring engine.
     *
     * @param events The complete, ordered delivery log for one innings.
     * @return A list of [OverSummary] objects, one per over (including the in-progress over).
     */
    fun deriveOverSummaries(events: List<BallEvent>): List<OverSummary> =
        BallTimelineFormatter.groupByOver(events)

    /**
     * Returns a compact cricket-notation label for a single [BallEvent], suitable for
     * use in an over summary display.
     *
     * Labels are intentionally shorter than [BallTimelineFormatter.formatBall] output:
     * - Dot balls are shown as `"0"` rather than `"."`.
     * - Extras use capitalised abbreviations: `"Wd"`, `"Nb"`, `"B1"`, `"Lb1"`.
     * - Run-outs with runs are combined: `"1W"` for 1 run + wicket.
     */
    fun ballLabel(event: BallEvent): String {
        if (event.isPenalty) return "P${event.runsOffBat}"

        val isWide   = event.extras.wides   > 0
        val isNoBall = event.extras.noBalls > 0
        val isBye    = event.extras.byes    > 0
        val isLegBye = event.extras.legByes > 0

        return when {
            isWide   -> "Wd"
            isNoBall -> "Nb"
            isBye    -> "B${event.extras.byes}"
            isLegBye -> "Lb${event.extras.legByes}"
            event.wicket -> {
                val isRunOut = event.dismissalDetail?.dismissalType == DismissalType.RUN_OUT
                if (isRunOut && event.runsOffBat > 0) "${event.runsOffBat}W" else "W"
            }
            event.runsOffBat == 0 -> "0"
            else -> "${event.runsOffBat}"
        }
    }
}
