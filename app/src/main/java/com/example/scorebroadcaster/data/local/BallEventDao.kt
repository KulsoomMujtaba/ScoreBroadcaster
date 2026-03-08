package com.example.scorebroadcaster.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface BallEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BallEventEntity)

    @Update
    suspend fun update(entity: BallEventEntity)

    @Delete
    suspend fun delete(entity: BallEventEntity)

    @Query("SELECT * FROM ball_events")
    suspend fun getAll(): List<BallEventEntity>

    @Query("SELECT * FROM ball_events WHERE id = :id")
    suspend fun getById(id: String): BallEventEntity?

    @Query("SELECT * FROM ball_events WHERE matchId = :matchId ORDER BY innings, eventIndex")
    suspend fun getByMatchId(matchId: String): List<BallEventEntity>
}
