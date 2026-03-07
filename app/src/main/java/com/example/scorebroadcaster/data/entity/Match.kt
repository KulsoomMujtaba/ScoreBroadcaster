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
    val createdAt: Long = System.currentTimeMillis(),

    // ── Publishing / ownership metadata (future-ready) ─────────────────────────
    // All fields below are nullable / defaulted so existing code requires no changes.

    /**
     * Local device identifier (same as [id] today).
     * Kept separate to make the local vs. remote distinction explicit when
     * backend sync is introduced.
     */
    val localId: String = id,

    /**
     * Identifier assigned by the remote backend after the match is published.
     * Null until backend sync is implemented.
     */
    val remoteId: String? = null,

    /**
     * The user-account id of the scorer who owns/created this match.
     * Null until authentication is implemented.
     */
    val ownerUserId: String? = null,

    /**
     * Controls who can see this match once publish/share features are live.
     * Defaults to [MatchVisibility.PRIVATE] so all matches remain local/scorer-only today.
     */
    val visibility: MatchVisibility = MatchVisibility.PRIVATE,

    /**
     * Epoch-millisecond timestamp of when the match was first made accessible to viewers
     * (i.e. visibility changed to [MatchVisibility.PUBLISHED] or [MatchVisibility.UNLISTED]).
     * Null while the match remains [MatchVisibility.PRIVATE] and has never been shared.
     */
    val publishedAt: Long? = null,

    /**
     * Short human-readable slug or share code for public links (e.g. "abc123").
     * Null until the match is published and the backend assigns one.
     */
    val shareCode: String? = null
) {
    /** Human-readable title, falling back to "Team A vs Team B". */
    val displayTitle: String
        get() = title.ifBlank { "${teamA.name} vs ${teamB.name}" }

    /** Short toss result sentence shown in summaries. */
    val tossResultText: String
        get() = "${tossWinner.name} won the toss and chose to ${tossDecision.label.lowercase()}"

    /**
     * True if this match is publicly listed and visible to viewers.
     * Use [publishedAt] != null to check whether the match has ever been made accessible,
     * which also covers [MatchVisibility.UNLISTED] (shared via link).
     */
    val isPubliclyListed: Boolean
        get() = visibility == MatchVisibility.PUBLISHED && publishedAt != null
}
