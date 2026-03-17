package com.example.scorebroadcaster.features.players.data
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT * FROM player_profiles ORDER BY displayName")
    fun observeAll(): Flow<List<PlayerProfileEntity>>
}
