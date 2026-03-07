package com.example.scorebroadcaster.data.entity

/**
 * Represents a batting partnership between two players.
 *
 * @param strikerName    Name of the striker when this partnership began.
 * @param nonStrikerName Name of the non-striker when this partnership began.
 * @param runs           Total runs scored by the team while this pair was at the crease.
 * @param balls          Total legal deliveries faced during this partnership.
 * @param startScore     Team score at the start of this partnership.
 * @param endScore       Team score at the end of this partnership (0 while still current).
 * @param isCurrent      True while this partnership is still in progress.
 */
data class Partnership(
    val strikerName: String,
    val nonStrikerName: String,
    val runs: Int,
    val balls: Int,
    val startScore: Int,
    val endScore: Int,
    val isCurrent: Boolean
)
