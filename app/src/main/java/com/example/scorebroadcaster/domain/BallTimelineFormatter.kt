package com.example.scorebroadcaster.domain

import com.example.scorebroadcaster.data.entity.DismissalType

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
 * @param balls        All deliveries in this over, in order (including wides and no-balls).
 */
data class OverSummary(
    val overNumber: Int,
    val balls: List<IndexedBall>
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
        val isWide   = event.extras.wides   > 0
        val isNoBall = event.extras.noBalls > 0
        val isBye    = event.extras.byes    > 0
        val isLegBye = event.extras.legByes > 0

        return when {
            isWide -> {
                // Wide penalty is 1; anything beyond is additional runs
                val extra = event.extras.wides - 1
                val runsStr = if (extra > 0) "+$extra" else ""
                val wicketStr = if (event.wicket) "+W" else ""
                "wd$runsStr$wicketStr"
            }

            isNoBall -> {
                // Runs off bat credited separately from penalty
                val runsSuffix = when {
                    event.wicket                -> "+W"   // run-out on a no-ball
                    event.runsOffBat > 0        -> "+${event.runsOffBat}"
                    else                        -> ""
                }
                "nb$runsSuffix"
            }

            isBye   -> "b${event.extras.byes}"
            isLegBye -> "lb${event.extras.legByes}"

            event.wicket -> {
                val type = event.dismissalDetail?.dismissalType
                if (type == DismissalType.RUN_OUT) "W (run out)" else "W"
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
                    overs.add(OverSummary(currentOverNumber, currentBalls.toList()))
                    currentOverNumber++
                    legalBallsInOver = 0
                    currentBalls.clear()
                }
            }
        }

        // Add the in-progress (incomplete) over, if any deliveries remain
        if (currentBalls.isNotEmpty()) {
            overs.add(OverSummary(currentOverNumber, currentBalls.toList()))
        }

        return overs
    }
}
