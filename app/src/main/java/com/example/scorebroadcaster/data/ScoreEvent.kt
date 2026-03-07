package com.example.scorebroadcaster.data

import com.example.scorebroadcaster.data.entity.DismissalDetail
import com.example.scorebroadcaster.data.entity.ExtrasBreakdown
import com.example.scorebroadcaster.domain.BallEvent

sealed class ScoreEvent {
    data class Run(val runs: Int) : ScoreEvent()
    data class Wicket(val dismissal: DismissalDetail) : ScoreEvent()
    data class Wide(val runs: Int) : ScoreEvent()
    data class NoBall(val runs: Int) : ScoreEvent()
    data class Bye(val runs: Int) : ScoreEvent()
    data class LegBye(val runs: Int) : ScoreEvent()
}

/**
 * Converts a legacy [ScoreEvent] to the new [BallEvent] domain model.
 *
 * This mapping keeps the UI layer unchanged while the scoring engine
 * operates entirely on [BallEvent] internally.
 */
fun ScoreEvent.toBallEvent(): BallEvent = when (this) {
    is ScoreEvent.Run -> BallEvent(
        runsOffBat = runs,
        countsAsBall = true
    )
    is ScoreEvent.Wicket -> BallEvent(
        wicket = true,
        dismissalDetail = dismissal,
        countsAsBall = true
    )
    is ScoreEvent.Wide -> BallEvent(
        // Wide penalty is 1 run; any extra runs from overthrows etc. go in wides total
        extras = ExtrasBreakdown(wides = runs + 1),
        countsAsBall = false
    )
    is ScoreEvent.NoBall -> BallEvent(
        runsOffBat = runs,
        // NoBall penalty is always 1 extra run; batter runs tracked in runsOffBat
        extras = ExtrasBreakdown(noBalls = 1),
        countsAsBall = false
    )
    is ScoreEvent.Bye -> BallEvent(
        extras = ExtrasBreakdown(byes = runs),
        countsAsBall = true
    )
    is ScoreEvent.LegBye -> BallEvent(
        extras = ExtrasBreakdown(legByes = runs),
        countsAsBall = true
    )
}
