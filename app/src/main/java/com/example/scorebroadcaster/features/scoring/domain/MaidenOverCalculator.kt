package com.example.scorebroadcaster.features.scoring.domain
/**
 * Derives maiden-over counts for each bowler from an ordered list of [BallEvent]s.
 *
 * ## Maiden-over definition
 * An over is a maiden when the bowler concedes **zero runs** during that over.
 * Runs conceded by the bowler include:
 *  - runs off the bat ([BallEvent.runsOffBat])
 *  - wides ([ExtrasBreakdown.wides] — the penalty counts against the bowler)
 *  - no-balls ([ExtrasBreakdown.noBalls] — the penalty counts against the bowler)
 *
 * Runs that do **not** count against the bowler:
 *  - byes ([ExtrasBreakdown.byes]) — charged to the wicket-keeper, not the bowler
 *  - leg-byes ([ExtrasBreakdown.legByes]) — also not the bowler's responsibility
 *
 * A wicket with no other runs is still a maiden.
 *
 * ## Grouping into overs
 * An over boundary is reached after exactly 6 deliveries for which [BallEvent.countsAsBall] is
 * `true`.  Wides and no-balls do not count as legal balls and therefore do not end the over, but
 * they are still included in the run total charged to the bowler.
 *
 * ## Bowler attribution
 * Each [BallEvent] carries an optional [BallEvent.bowler] reference (stamped by the ViewModel
 * when the event is recorded).  The maiden is credited to the first bowler found within the over's
 * deliveries.  Events recorded before bowler-stamping was introduced (where `bowler == null`) are
 * ignored for maiden purposes.
 *
 * ## Correctness after undo / edit / delete
 * Because this function is pure — it takes the full event list and returns a fresh map — calling
 * it again after any modification to the log always yields the correct result.  No mutable
 * counters are kept anywhere.
 */
object MaidenOverCalculator {

    /**
     * Compute the number of maiden overs bowled by each bowler.
     *
     * @param events The complete, ordered delivery log for one innings.
     * @return A map from bowler player-id to maiden-over count.  Bowlers with no maidens
     *         are not present in the map (their count is implicitly 0).
     */
    fun compute(events: List<BallEvent>): Map<String, Int> {
        val maidensPerBowler = mutableMapOf<String, Int>()

        // Deliveries belonging to the current (possibly incomplete) over.
        val overBuffer = mutableListOf<BallEvent>()
        var legalBallsInOver = 0

        for (event in events) {
            overBuffer.add(event)
            if (event.countsAsBall) legalBallsInOver++

            if (legalBallsInOver == 6) {
                // Over is now complete — evaluate runs conceded by the bowler.
                // Byes and leg-byes are excluded: they are not charged to the bowler.
                val runsConceded = overBuffer.sumOf {
                    it.runsOffBat + it.extras.wides + it.extras.noBalls
                }
                if (runsConceded == 0) {
                    // Maiden: credit the bowler of this over.
                    val bowler = overBuffer.firstNotNullOfOrNull { it.bowler }
                    if (bowler != null) {
                        maidensPerBowler[bowler.id] = (maidensPerBowler[bowler.id] ?: 0) + 1
                    }
                }
                overBuffer.clear()
                legalBallsInOver = 0
            }
        }

        // The incomplete over at the tail is ignored — it has not been completed yet.

        return maidensPerBowler
    }

    /**
     * Convenience function that returns the maiden-over count for a single bowler.
     *
     * @param events   The complete, ordered delivery log for one innings.
     * @param bowlerId The [com.example.scorebroadcaster.data.entity.Player.id] of the bowler.
     * @return The number of maiden overs bowled by [bowlerId], or 0 if none.
     */
    fun calculateBowlerMaidens(events: List<BallEvent>, bowlerId: String): Int =
        compute(events)[bowlerId] ?: 0
}
