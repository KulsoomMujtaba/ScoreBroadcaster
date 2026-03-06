package com.example.scorebroadcaster.data.entity

enum class MatchFormat(val label: String, val defaultOvers: Int) {
    T20("T20 (20 overs)", 20),
    T10("T10 (10 overs)", 10),
    ODI("ODI (50 overs)", 50),
    TAPE_BALL("Tape-ball (6 overs)", 6),
    CUSTOM("Custom", 0)
}
