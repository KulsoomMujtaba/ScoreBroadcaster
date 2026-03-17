package com.example.scorebroadcaster.features.scoring.domain
import com.example.scorebroadcaster.features.scoring.data.DismissalDetail
import com.example.scorebroadcaster.features.scoring.data.ExtrasBreakdown
import com.example.scorebroadcaster.features.players.data.Player

/**
 * Represents a single delivery (ball) in a cricket innings.
 *
 * A [BallEvent] can model every possible real-world delivery outcome including:
 * - Dot ball, runs off bat, boundaries
 * - Wide (with or without additional runs)
 * - No-ball (with or without runs off bat)
 * - Byes and leg-byes (with any number of runs)
 * - Wickets (bowled, caught, lbw, stumped, run-out)
 * - Combined outcomes such as run-out on a no-ball or wide
 *
 * @param runsOffBat      Runs credited to the batter's bat (0 for extras, wides, etc.).
 * @param extras          Breakdown of any extras conceded on this delivery.
 * @param wicket          True if a dismissal occurred on this delivery.
 * @param dismissalDetail Full details of the dismissal (null when [wicket] is false).
 * @param countsAsBall    True when this delivery counts as a legal ball (increments the over).
 *                        Wides and no-balls are false; all other deliveries are true.
 * @param bowler          The bowler who delivered this ball.  Null for events recorded before
 *                        bowler-stamping was introduced (backward-compatible default).
 *                        Used by [com.example.scorebroadcaster.domain.MaidenOverCalculator] to
 *                        derive maiden-over counts without storing mutable counters.
 * @param striker         The batter on strike when this ball was bowled.  Null for events
 *                        recorded before striker-stamping was introduced (backward-compatible).
 *                        Persisted so the live batting state can be restored after an app
 *                        restart without re-opening the innings-setup dialog.
 * @param nonStriker      The batter at the non-striker's end.  Same persistence rationale as
 *                        [striker].
 */
data class BallEvent(
    val runsOffBat: Int = 0,
    val extras: ExtrasBreakdown = ExtrasBreakdown.NONE,
    val wicket: Boolean = false,
    val dismissalDetail: DismissalDetail? = null,
    val countsAsBall: Boolean = true,
    val bowler: Player? = null,
    val striker: Player? = null,
    val nonStriker: Player? = null
)
