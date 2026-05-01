package com.devhub.scored.features.players.data
import java.util.UUID

/**
 * Indicates whether a [Player] was created on-the-fly for a single match (QUICK)
 * or is backed by a persisted [PlayerProfile] owned by the current user (SAVED).
 */
enum class PlayerType {
    /** Created ad-hoc for one match; not persisted remotely. */
    QUICK,
    /** Backed by a [PlayerProfile] stored in the local DB and optionally synced to the cloud. */
    SAVED
}

/**
 * Internal field used to track the origin of a [Player] entry.
 *
 * LOCAL  – player was created on this device by the current user (default today).
 * FUTURE_GLOBAL – reserved for cross-account / global player support (not yet implemented).
 */
enum class PlayerOrigin {
    LOCAL,
    // Reserved for future global player support
    FUTURE_GLOBAL
}

/**
 * A match-level player snapshot.
 *
 * When a player is selected from a saved [PlayerProfile] the profile's [displayName] is
 * copied here and [sourceProfileId] records the origin profile id.  Because this is an
 * independent copy, edits to the profile later never affect existing match records.
 *
 * Players created by typing a name manually have [sourceProfileId] == null and
 * [type] == [PlayerType.QUICK].
 *
 * Both player types behave identically during scoring — [type] is informational only
 * and the match engine never branches on it.
 */
data class Player(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Id of the [PlayerProfile] this snapshot was created from, or null for quick players. */
    val sourceProfileId: String? = null,
    /** User id that owns this player entry when [type] is [PlayerType.SAVED]; otherwise null. */
    val userId: String? = null,
    // Reserved for future global player support
    /** Internal origin marker — LOCAL for all players created today; FUTURE_GLOBAL is reserved. */
    val sourceType: PlayerOrigin = PlayerOrigin.LOCAL,
    /** External user id for future global player linking; not used in the current UI. */
    val externalUserId: String? = null
) {
    /**
     * Derived from [sourceProfileId]: SAVED when backed by a profile, QUICK otherwise.
     * Always consistent with [sourceProfileId] — no manual synchronisation needed.
     */
    val type: PlayerType
        get() = if (sourceProfileId != null) PlayerType.SAVED else PlayerType.QUICK
}
