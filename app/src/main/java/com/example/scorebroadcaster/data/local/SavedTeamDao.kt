package com.example.scorebroadcaster.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SavedTeamDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SavedTeamEntity)

    @Update
    suspend fun update(entity: SavedTeamEntity)

    @Delete
    suspend fun delete(entity: SavedTeamEntity)

    @Query("SELECT * FROM saved_teams")
    suspend fun getAll(): List<SavedTeamEntity>

    @Query("SELECT * FROM saved_teams WHERE id = :id")
    suspend fun getById(id: String): SavedTeamEntity?
}
