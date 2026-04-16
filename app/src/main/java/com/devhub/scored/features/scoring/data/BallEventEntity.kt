package com.devhub.scored.features.scoring.data
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.devhub.scored.features.players.data.Player
import com.devhub.scored.features.scoring.domain.BallEvent

/**
 * Room entity for a single ball delivery event stored locally.
 *
 * Stores a [BallEvent] in a flattened, Room-safe format so each delivery can be
 * replayed independently to reconstruct full match state after an app restart.
 *
 * [matchLocalId] references [MatchEntity.localId].
 * [inningsNumber] is 1 for first-innings events, 2 for second-innings events.
 * [sequenceNumber] preserves event order within an innings (0-based).
 *
 * [ExtrasBreakdown] is stored as four separate Int columns.
 * [DismissalDetail] is flattened into nullable primitive/string fields.
 * The delivery's [BallEvent.bowler] is stored as [eventBowlerName] /
 * [eventBowlerSourceProfileId] so maiden-over calculations can be restored.
 */
@Entity(tableName = "ball_events")
data class BallEventEntity(
    @PrimaryKey val id: String,
    val matchLocalId: String,
    val inningsNumber: Int,
    val sequenceNumber: Int,

    // Runs and extras
    val runsOffBat: Int,
    val wides: Int,
    val noBalls: Int,
    val byes: Int,
    val legByes: Int,

    // Delivery flags
    val wicket: Boolean,
    val countsAsBall: Boolean,

    // DismissalDetail — all nullable; only set when wicket == true
    val dismissedBatterName: String?,
    val dismissalType: String?,
    val fielderName: String?,
    val fielder2Name: String?,
    val bowlerName: String?,
    /** True when the dismissal caused the striker to be replaced; null when not a wicket. */
    val dismissalReplacingStriker: Boolean?,

    // The bowler who delivered this ball (for maiden-over calculation)
    val eventBowlerName: String?,
    val eventBowlerSourceProfileId: String?,

    // The striker and non-striker at the time of this delivery (for app-restart restore)
    val eventStrikerName: String?,
    val eventStrikerSourceProfileId: String?,
    val eventNonStrikerName: String?,
    val eventNonStrikerSourceProfileId: String?,

    /** True when this event represents penalty runs (not a delivery). */
    val isPenalty: Boolean = false,

    val createdAt: Long
)

// ---------------------------------------------------------------------------
// Mapping helpers
// ---------------------------------------------------------------------------

/**
 * Convert a [BallEventEntity] back to the domain [BallEvent].
 *
 * Players are reconstructed from stored name strings.
 * [DismissalDetail] is rebuilt when [BallEventEntity.wicket] is true and a
 * [BallEventEntity.dismissalType] is present.
 */
fun BallEventEntity.toDomain(): BallEvent {
    val extras = ExtrasBreakdown(
        wides = wides,
        noBalls = noBalls,
        byes = byes,
        legByes = legByes
    )

    val dismissal: DismissalDetail? = if (wicket && dismissalType != null) {
        val type = runCatching { DismissalType.valueOf(dismissalType ?: "") }.getOrNull()
        if (type != null && dismissedBatterName != null) {
            val fieldersList = listOfNotNull(
                fielderName?.let { Player(name = it) },
                fielder2Name?.let { Player(name = it) }
            )
            DismissalDetail(
                batter = Player(name = dismissedBatterName),
                dismissalType = type,
                fielders = fieldersList,
                bowler = bowlerName?.let { Player(name = it) }
            )
        } else null
    } else null

    val bowler: Player? = eventBowlerName?.let {
        Player(name = it, sourceProfileId = eventBowlerSourceProfileId)
    }

    val striker: Player? = eventStrikerName?.let {
        Player(name = it, sourceProfileId = eventStrikerSourceProfileId)
    }

    val nonStriker: Player? = eventNonStrikerName?.let {
        Player(name = it, sourceProfileId = eventNonStrikerSourceProfileId)
    }

    return BallEvent(
        runsOffBat = runsOffBat,
        extras = extras,
        wicket = wicket,
        dismissalDetail = dismissal,
        countsAsBall = countsAsBall,
        bowler = bowler,
        striker = striker,
        nonStriker = nonStriker,
        isPenalty = isPenalty
    )
}

/**
 * Convert a domain [BallEvent] to a [BallEventEntity] ready for Room persistence.
 *
 * @param matchLocalId   The local ID of the match this event belongs to.
 * @param inningsNumber  1 for the first innings, 2 for the second innings.
 * @param sequenceNumber 0-based position of this event within the innings.
 */
fun BallEvent.toEntity(
    matchLocalId: String,
    inningsNumber: Int,
    sequenceNumber: Int
): BallEventEntity = BallEventEntity(
    id = "${matchLocalId}_${inningsNumber}_${sequenceNumber}",
    matchLocalId = matchLocalId,
    inningsNumber = inningsNumber,
    sequenceNumber = sequenceNumber,
    runsOffBat = runsOffBat,
    wides = extras.wides,
    noBalls = extras.noBalls,
    byes = extras.byes,
    legByes = extras.legByes,
    wicket = wicket,
    countsAsBall = countsAsBall,
    dismissedBatterName = dismissalDetail?.batter?.name,
    dismissalType = dismissalDetail?.dismissalType?.name,
    fielderName = dismissalDetail?.fielders?.getOrNull(0)?.name,
    fielder2Name = dismissalDetail?.fielders?.getOrNull(1)?.name,
    bowlerName = dismissalDetail?.bowler?.name,
    dismissalReplacingStriker = null,
    eventBowlerName = bowler?.name,
    eventBowlerSourceProfileId = bowler?.sourceProfileId,
    eventStrikerName = striker?.name,
    eventStrikerSourceProfileId = striker?.sourceProfileId,
    eventNonStrikerName = nonStriker?.name,
    eventNonStrikerSourceProfileId = nonStriker?.sourceProfileId,
    isPenalty = isPenalty,
    createdAt = System.currentTimeMillis()
)
