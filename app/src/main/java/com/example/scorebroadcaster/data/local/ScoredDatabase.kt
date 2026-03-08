package com.example.scorebroadcaster.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database for the Scored app.
 *
 * Contains local persistence tables for player profiles, saved teams,
 * matches, and ball-by-ball events.
 *
 * **Version history:**
 * - v1: Initial schema (player_profiles, saved_teams, saved_team_player_cross_ref, matches, ball_events)
 * - v2: Added `players` JSON column to `saved_teams`; SavedTeamRepository is now Room-backed
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
    version = 2,
    exportSchema = false
)
@TypeConverters(PlayerListTypeConverter::class)
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
                )
                    // Development-phase: destructive migration acceptable while schema evolves.
                    // Replace with explicit Migration objects before a production release.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
