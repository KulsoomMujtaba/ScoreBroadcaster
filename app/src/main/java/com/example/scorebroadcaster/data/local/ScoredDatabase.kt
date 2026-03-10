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
 * - v3: Updated `matches` schema — `title` is now nullable; added `remoteId` and `publishedAt`;
 *       MatchRepository is now Room-backed (Phase 9.3)
 * - v4: Replaced `ball_events` schema with full delivery model — renamed columns to `matchLocalId`,
 *       `inningsNumber`, `sequenceNumber`; added dismissal fields, bowler fields, `createdAt`;
 *       added `toDomain()` / `toEntity()` mapping helpers (Phase 9.4)
 * - v5: Finalised `matches` schema — renamed `overs` to `oversLimit`; added `tossWinner` and
 * - v6: Added `eventStrikerName`, `eventStrikerSourceProfileId`, `eventNonStrikerName`,
 *       `eventNonStrikerSourceProfileId` columns to `ball_events` so batting state can be
 *       restored after an app restart without re-opening the innings-setup dialog.
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
    version = 6,
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
