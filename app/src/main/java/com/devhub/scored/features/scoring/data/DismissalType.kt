package com.devhub.scored.features.scoring.data
enum class DismissalType(val label: String) {
    BOWLED("Bowled"),
    CAUGHT("Caught"),
    LBW("LBW"),
    RUN_OUT("Run Out"),
    STUMPED("Stumped"),
    HIT_WICKET("Hit Wicket"),
    OBSTRUCTING_FIELD("Obstructing the Field"),
    OTHER("Other")
}
