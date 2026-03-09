package com.example.scorebroadcaster.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BallEventDao {

    @Query("""
        SELECT * FROM ball_events
        WHERE matchLocalId = :matchId AND inningsNumber = :inningsNumber
        ORDER BY sequenceNumber ASC
    """)
    suspend fun getEventsForInnings(matchId: String, inningsNumber: Int): List<BallEventEntity>

    @Query("""
        SELECT * FROM ball_events
        WHERE matchLocalId = :matchId
        ORDER BY inningsNumber ASC, sequenceNumber ASC
    """)
    suspend fun getAllEventsForMatch(matchId: String): List<BallEventEntity>

    /**
     * Observe all ball events for a match as a reactive [Flow].
     *
     * Emits a new list whenever events are inserted, updated, or deleted for the given match.
     * Events are ordered by innings then by sequence so replaying them always produces the
     * correct match state.
     */
    @Query("""
        SELECT * FROM ball_events
        WHERE matchLocalId = :matchLocalId
        ORDER BY inningsNumber ASC, sequenceNumber ASC
    """)
    fun observeForMatch(matchLocalId: String): Flow<List<BallEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<BallEventEntity>)

    @Query("DELETE FROM ball_events WHERE matchLocalId = :matchId")
    suspend fun deleteForMatch(matchId: String)

    @Query("DELETE FROM ball_events WHERE matchLocalId = :matchId AND inningsNumber = :inningsNumber")
    suspend fun deleteForInnings(matchId: String, inningsNumber: Int)
}
