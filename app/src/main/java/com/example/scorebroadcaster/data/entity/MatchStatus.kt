package com.example.scorebroadcaster.data.entity

enum class MatchStatus(val label: String) {
    NOT_STARTED("Not Started"),
    IN_PROGRESS("Live"),
    INNINGS_BREAK("Innings Break"),
    COMPLETED("Completed")
}
