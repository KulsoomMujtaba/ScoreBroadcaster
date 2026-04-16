package com.devhub.scored.features.scoring.data

import kotlinx.serialization.Serializable

@Serializable
enum class EventType {
    BALL,

    RUN,
    EXTRA,
    WICKET,

    STRIKE_CHANGE,
    OVER_COMPLETE,

    PLAYER_IN,
    BOWLER_CHANGE,

    INNINGS_END,
    MATCH_END,
    MATCH_STATUS_CHANGE,

    UNDO_TO_INDEX,

    SCORE_CORRECTION,

    PENALTY_RUNS
}
