package com.example.scorebroadcaster.data.entity

import java.util.UUID

/**
 * A match-level player snapshot.
 *
 * When a player is selected from a saved [PlayerProfile] the profile's [displayName] is
 * copied here and [sourceProfileId] records the origin profile id.  Because this is an
 * independent copy, edits to the profile later never affect existing match records.
 *
 * Players created by typing a name manually have [sourceProfileId] == null.
 */
data class Player(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Id of the [PlayerProfile] this snapshot was created from, or null for ad-hoc names. */
    val sourceProfileId: String? = null
)
