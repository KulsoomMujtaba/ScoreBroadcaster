package com.example.scorebroadcaster.data.entity

/**
 * Represents a single entry in the Fall of Wickets list.
 *
 * @param wicketNumber The sequential wicket number (1–10).
 * @param batterName   The name of the dismissed batter.
 * @param teamScore    The team's total runs at the time the wicket fell.
 * @param overs        The over and ball at which the wicket fell, in "X.Y" format.
 * @param dismissal    Human-readable dismissal description (e.g. "c Jones b Smith").
 */
data class FallOfWicket(
    val wicketNumber: Int,
    val batterName: String,
    val teamScore: Int,
    val overs: String,
    val dismissal: String
)
