package com.example.scorebroadcaster.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a single ball delivery event stored locally.
 *
 * Mirrors [com.example.scorebroadcaster.domain.BallEvent] with flat columns
 * so each delivery can be queried and replayed independently.
 * The [matchId] references [MatchEntity.localId].
 *
 * Relations and compound queries will be implemented in a future phase.
 */
@Entity(tableName = "ball_events")
data class BallEventEntity(
    @PrimaryKey val id: String,
    val matchId: String,
    val innings: Int,
    val eventIndex: Int,
    val runsOffBat: Int,
    val wides: Int,
    val noBalls: Int,
    val byes: Int,
    val legByes: Int,
    val wicket: Boolean,
    val dismissalType: String?,
    val batterId: String?,
    val bowlerId: String?,
    val fielderId: String?,
    val countsAsBall: Boolean
)
