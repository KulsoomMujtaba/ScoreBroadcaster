package com.example.scorebroadcaster.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<BallEventEntity>)

    @Query("DELETE FROM ball_events WHERE matchLocalId = :matchId")
    suspend fun deleteForMatch(matchId: String)

    @Query("DELETE FROM ball_events WHERE matchLocalId = :matchId AND inningsNumber = :inningsNumber")
    suspend fun deleteForInnings(matchId: String, inningsNumber: Int)
}
