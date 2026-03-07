package com.example.scorebroadcaster.data.entity

/**
 * Controls who can see a match once publish/share features are introduced.
 *
 * Today all matches are treated as [PRIVATE] (local, scorer-only).
 * [PUBLISHED] and [UNLISTED] are reserved for when backend publishing is implemented.
 *
 * Architecture note – one-scorer / many-viewers model:
 * - The scorer (owner) always has full edit access regardless of visibility.
 * - Viewers will only be able to open matches that are [PUBLISHED] or [UNLISTED].
 * - [PRIVATE] matches are never visible to viewers; only the owning device sees them.
 */
enum class MatchVisibility(val label: String) {
    /** Visible only to the scorer on this device. Default for all new matches. */
    PRIVATE("Private"),

    /** Publicly listed and visible to all viewers once backend sync is live. */
    PUBLISHED("Published"),

    /**
     * Accessible via direct link / share-code but not listed publicly.
     * Useful for sharing a match with a specific audience without full publication.
     */
    UNLISTED("Unlisted")
}
