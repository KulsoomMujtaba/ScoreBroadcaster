package com.example.scorebroadcaster.features.match.data

import com.example.scorebroadcaster.features.scoring.data.Innings
enum class MatchStatus(val label: String) {
    NOT_STARTED("Not Started"),
    IN_PROGRESS("Live"),
    INNINGS_BREAK("Innings Break"),
    COMPLETED("Completed")
}
