package com.example.scorebroadcaster.data.entity

import java.util.UUID

data class Match(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val teamA: Team,
    val teamB: Team,
    val format: MatchFormat,
    val overs: Int,
    val tossWinner: Team,
    val tossDecision: TossDecision,
    val battingFirst: Team,
    val bowlingFirst: Team,
    val innings: List<Innings> = emptyList(),
    val status: MatchStatus = MatchStatus.NOT_STARTED,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Human-readable title, falling back to "Team A vs Team B". */
    val displayTitle: String
        get() = title.ifBlank { "${teamA.name} vs ${teamB.name}" }

    /** Short toss result sentence shown in summaries. */
    val tossResultText: String
        get() = "${tossWinner.name} won the toss and chose to ${tossDecision.label.lowercase()}"
}
