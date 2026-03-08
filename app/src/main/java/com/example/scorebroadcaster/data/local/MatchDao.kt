package com.example.scorebroadcaster.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MatchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MatchEntity)

    @Update
    suspend fun update(entity: MatchEntity)

    @Delete
    suspend fun delete(entity: MatchEntity)

    @Query("SELECT * FROM matches")
    suspend fun getAll(): List<MatchEntity>

    @Query("SELECT * FROM matches WHERE localId = :localId")
    suspend fun getById(localId: String): MatchEntity?
}
