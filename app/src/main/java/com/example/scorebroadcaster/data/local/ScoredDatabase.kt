package com.example.scorebroadcaster.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room database for the Scored app.
 *
 * Contains local persistence tables for player profiles, saved teams,
 * matches, and ball-by-ball events.
 *
 * **Architecture note:** The existing in-memory repositories
 * ([com.example.scorebroadcaster.repository.MatchRepository],
 * [com.example.scorebroadcaster.repository.SavedTeamRepository],
 * [com.example.scorebroadcaster.repository.SavedPlayerRepository])
 * remain the active data sources for Phase 9.  The Room database is
 * introduced alongside them and will replace them in a future phase.
 *
 * Use [getInstance] to obtain the singleton database instance.
 */
@Database(
    entities = [
        PlayerProfileEntity::class,
        SavedTeamEntity::class,
        SavedTeamPlayerCrossRef::class,
        MatchEntity::class,
        BallEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ScoredDatabase : RoomDatabase() {

    abstract fun playerProfileDao(): PlayerProfileDao
    abstract fun savedTeamDao(): SavedTeamDao
    abstract fun matchDao(): MatchDao
    abstract fun ballEventDao(): BallEventDao

    companion object {
        @Volatile
        private var instance: ScoredDatabase? = null

        fun getInstance(context: Context): ScoredDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScoredDatabase::class.java,
                    "scored.db"
                ).build().also { instance = it }
            }
    }
}
