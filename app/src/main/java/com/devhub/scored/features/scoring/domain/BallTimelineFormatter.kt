package com.devhub.scored.features.scoring.domain
import com.devhub.scored.features.scoring.data.DismissalType

/**
 * Represents a single delivery in the ball timeline with stable identification.
 *
 * @param globalIndex   Position of this delivery in the innings event log (0-based).
 * @param overNumber    1-based over number this delivery belongs to.
 * @param ballInOver    1-based position within the over (extras can push this beyond 6).
 * @param display       Short cricket-notation string (e.g. ".", "4", "W", "wd+2", "nb+run out").
 * @param event         The underlying [BallEvent] (useful for future edit-ball support).
 */
data class IndexedBall(
    val globalIndex: Int,
    val overNumber: Int,
    val ballInOver: Int,
    val display: String,
    val event: BallEvent
)

/**
 * A completed (or in-progress) over with its constituent deliveries.
 *
 * @param overNumber   1-based over number.
 * @param bowlerName   Name of the bowler who bowled this over (empty string if unknown).
 * @param balls        All deliveries in this over, in order (including wides and no-balls).
 * @param runsInOver   Total runs scored in this over (runs off bat + wides + no-balls;
 *                     byes and leg-byes excluded from bowler's figures).
 * @param isMaiden     True when the bowler conceded zero runs in this over (a maiden over).
 *                     Only meaningful for completed overs (6 legal deliveries).
 */
data class OverSummary(
    val overNumber: Int,
    val bowlerName: String = "",
    val balls: List<IndexedBall>,
    val runsInOver: Int = 0,
    val isMaiden: Boolean = false
)

/**
 * Pure formatting helpers for the ball timeline / over history feature.
 *
 * All functions are stateless and free of Composable or Android dependencies,
 * so they can be unit-tested without a device or emulator.
 */
object BallTimelineFormatter {

    // -------------------------------------------------------------------------
    // Ball display string
    // -------------------------------------------------------------------------

    /**
     * Returns a compact cricket-notation string for a single [BallEvent].
     *
     * Examples:
     * - `.`         — dot ball
     * - `1` / `4` / `6` — runs off bat
     * - `W`         — wicket (bowled / caught / lbw / stumped)
     * - `W (run out)` — run-out dismissal
     * - `wd`        — wide (no additional runs)
     * - `wd+2`      — wide with 2 extra runs
     * - `nb`        — no-ball (no runs off bat)
     * - `nb+4`      — no-ball, 4 runs off bat
     * - `nb+W`      — no-ball, batter run out
     * - `b2`        — 2 byes
     * - `lb3`       — 3 leg-byes
     */
    fun formatBall(event: BallEvent): String {
        if (event.isPenalty) return "Penalty +${event.runsOffBat}"

        val isWide   = event.extras.wides   > 0
        val isNoBall = event.extras.noBalls > 0
        val isBye    = event.extras.byes    > 0
        val isLegBye = event.extras.legByes > 0

        return when {
            isWide -> {
                // Wide penalty is 1; anything beyond is additional runs taken by batsmen
                val extra = event.extras.wides - 1
                val runsStr = if (extra > 0) " + $extra" else ""
                val wicketStr = if (event.wicket) "+W" else ""
                "Wd$runsStr$wicketStr"
            }

            isNoBall -> {
                // Runs off bat credited separately from the 1-run penalty
                val runsSuffix = when {
                    event.wicket             -> "+W"   // run-out on a no-ball
                    event.runsOffBat > 0     -> " + ${event.runsOffBat}"
                    else                     -> ""
                }
                "Nb$runsSuffix"
            }

            isBye    -> "B ${event.extras.byes}"
            isLegBye -> "Lb ${event.extras.legByes}"

            event.wicket -> {
                val type = event.dismissalDetail?.dismissalType
                when (type) {
                    DismissalType.RUN_OUT -> "W (run out)"
                    DismissalType.HIT_WICKET -> "Hit Wicket"
                    DismissalType.OBSTRUCTING_FIELD -> "Obstructing the Field"
                    else -> "W"
                }
            }

            else -> if (event.runsOffBat == 0) "." else "${event.runsOffBat}"
        }
    }

    // -------------------------------------------------------------------------
    // Group deliveries by over
    // -------------------------------------------------------------------------

    /**
     * Groups an ordered list of [BallEvent]s into [OverSummary] objects.
     *
     * An over is complete when 6 legal deliveries ([BallEvent.countsAsBall] == true) have
     * been recorded. Wides and no-balls are included in the over they occur in but do not
     * advance the ball counter — matching real cricket rules.
     *
     * Calling this on an empty list returns an empty list.
     */
    fun groupByOver(events: List<BallEvent>): List<OverSummary> {
        if (events.isEmpty()) return emptyList()

        val overs = mutableListOf<OverSummary>()
        var currentOverNumber = 1
        var legalBallsInOver = 0
        val currentBalls = mutableListOf<IndexedBall>()

        events.forEachIndexed { globalIndex, event ->
            val display = formatBall(event)
            currentBalls.add(
                IndexedBall(
                    globalIndex  = globalIndex,
                    overNumber   = currentOverNumber,
                    ballInOver   = currentBalls.size + 1,
                    display      = display,
                    event        = event
                )
            )

            if (event.countsAsBall) {
                legalBallsInOver++
                if (legalBallsInOver >= 6) {
                    overs.add(buildOverSummary(currentOverNumber, currentBalls.toList()))
                    currentOverNumber++
                    legalBallsInOver = 0
                    currentBalls.clear()
                }
            }
        }

        // Add the in-progress (incomplete) over, if any deliveries remain
        if (currentBalls.isNotEmpty()) {
            overs.add(buildOverSummary(currentOverNumber, currentBalls.toList()))
        }

        return overs
    }

    /**
     * Returns the deliveries belonging to the current (latest, possibly incomplete) over.
     *
     * This is a convenience wrapper around [groupByOver] that extracts only the in-progress
     * over's [IndexedBall] list.  If the most recent over is already complete (exactly 6 legal
     * balls), a new over has effectively started with no deliveries yet, so an empty list is
     * returned — matching the "reset automatically when a new over starts" requirement.
     *
     * Returns an empty list when [events] is empty.
     */
    fun getCurrentOverBalls(events: List<BallEvent>): List<IndexedBall> {
        val lastOver = groupByOver(events).lastOrNull() ?: return emptyList()
        // A completed over means the next over is starting fresh; return empty.
        return if (lastOver.balls.count { it.event.countsAsBall } >= 6) emptyList()
        else lastOver.balls
    }

    /** Constructs an [OverSummary] from a list of [IndexedBall]s, computing derived fields. */
    private fun buildOverSummary(overNumber: Int, balls: List<IndexedBall>): OverSummary {
        val bowlerName = balls.firstNotNullOfOrNull { it.event.bowler?.name } ?: ""
        val runsInOver = balls.sumOf { b ->
            b.event.runsOffBat + b.event.extras.wides + b.event.extras.noBalls
        }
        val isComplete = balls.count { it.event.countsAsBall } >= 6
        val isMaiden = isComplete && runsInOver == 0
        return OverSummary(
            overNumber = overNumber,
            bowlerName = bowlerName,
            balls      = balls,
            runsInOver = runsInOver,
            isMaiden   = isMaiden
        )
    }
}
