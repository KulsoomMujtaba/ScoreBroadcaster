package com.example.scorebroadcaster.data.entity

import java.util.UUID

/**
 * Describes how a [PlayerProfile] was created.
 *
 * PRIVATE – a profile created locally by the current user (default today).
 * APP_USER – a profile linked to a registered Scored app account (future).
 *
 * The enum makes it easy to add new source types (e.g. IMPORTED) later without
 * touching the rest of the data model.
 */
enum class PlayerSourceType {
    PRIVATE,
    APP_USER
}

/**
 * A reusable player profile owned by the current user.
 *
 * This is the *template* stored in [com.example.scorebroadcaster.repository.SavedPlayerRepository].
 * When a player is added to a match a [Player] snapshot is created from this profile so that
 * later edits to the profile never corrupt existing match records.
 *
 * Fields are intentionally MVP-minimal but include nullable placeholders so the model is
 * ready for future extension without a schema migration.
 */
data class PlayerProfile(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val playerSourceType: PlayerSourceType = PlayerSourceType.PRIVATE,
    /** Null for PRIVATE players; will hold the linked account id for APP_USER players. */
    val linkedUserId: String? = null,
    // ---- optional metadata placeholders (future) ----
    val avatarUrl: String? = null,
    val role: String? = null,
    val battingStyle: String? = null,
    val bowlingStyle: String? = null
)

/**
 * Create a match-level [Player] snapshot from this profile.
 *
 * The snapshot captures [displayName] at the moment of selection.
 * Subsequent edits to the profile will not affect the returned [Player] or any
 * [com.example.scorebroadcaster.data.entity.BattingEntry] /
 * [com.example.scorebroadcaster.data.entity.BowlingEntry] that already references it.
 */
fun PlayerProfile.toMatchPlayer(): Player = Player(
    name = displayName,
    sourceProfileId = id
)
