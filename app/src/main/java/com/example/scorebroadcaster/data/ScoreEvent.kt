package com.example.scorebroadcaster.data

sealed class ScoreEvent {
    data class Run(val runs: Int) : ScoreEvent()
    data object Wicket : ScoreEvent()
    data class Wide(val runs: Int) : ScoreEvent()
    data class NoBall(val runs: Int) : ScoreEvent()
    data class Bye(val runs: Int) : ScoreEvent()
    data class LegBye(val runs: Int) : ScoreEvent()
}
