package com.example.scorebroadcaster.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface PlayerProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PlayerProfileEntity)

    @Update
    suspend fun update(entity: PlayerProfileEntity)

    @Delete
    suspend fun delete(entity: PlayerProfileEntity)

    @Query("SELECT * FROM player_profiles")
    suspend fun getAll(): List<PlayerProfileEntity>

    @Query("SELECT * FROM player_profiles WHERE id = :id")
    suspend fun getById(id: String): PlayerProfileEntity?
}
