package com.example.scorebroadcaster.data

import com.example.scorebroadcaster.data.entity.DismissalDetail

sealed class ScoreEvent {
    data class Run(val runs: Int) : ScoreEvent()
    data class Wicket(val dismissal: DismissalDetail) : ScoreEvent()
    data class Wide(val runs: Int) : ScoreEvent()
    data class NoBall(val runs: Int) : ScoreEvent()
    data class Bye(val runs: Int) : ScoreEvent()
    data class LegBye(val runs: Int) : ScoreEvent()
}
