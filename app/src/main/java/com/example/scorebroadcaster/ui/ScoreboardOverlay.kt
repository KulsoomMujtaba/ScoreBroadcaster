package com.example.scorebroadcaster.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scorebroadcaster.data.InningsPhase
import com.example.scorebroadcaster.data.MatchState
import com.example.scorebroadcaster.data.ScoringConsoleState
import com.example.scorebroadcaster.data.entity.BattingEntry
import com.example.scorebroadcaster.data.entity.BowlingEntry
import com.example.scorebroadcaster.data.entity.Player

// ─── Overlay colour palette ───────────────────────────────────────────────────

private val OverlayBackground = Color(0xCC1F3A5F)   // deep blue, slight transparency
private val PrimaryText = Color.White
private val SecondaryText = Color(0xFFCCCCCC)
private val AccentColor = Color(0xFFF2C94C)          // warm gold – accents
private val WicketColor = Color(0xFFFF4444)
private val BoundaryFourColor = Color(0xFFF2C94C)    // gold highlight for boundary 4
private val BoundarySixColor = Color(0xFFFFAA00)     // stronger gold highlight for six
private val DotBorderColor = Color(0xFF888888)
private val StrikerDotColor = Color(0xFFF2C94C)      // gold accent indicator next to striker name
private val WideBallColor = Color(0xFFF5A623)        // lighter gold/amber for wide and no-ball
private val NormalBallColor = Color(0xFF2A3A4A)      // dark blue-neutral for regular runs

// ─── Public composable ────────────────────────────────────────────────────────

/**
 * TV-style broadcast lower-third scoreboard overlay.
 *
 * Renders a single slim horizontal strip anchored to the bottom of the screen:
 *  - **Left block**: striker and non-striker (name + runs/balls, two compact lines)
 *  - **Center capsule**: match short title + innings badge, score (prominent), overs, run rate / chase info
 *  - **Right block**: bowler name + figures, then a row of current-over ball circles
 *
 * The overlay renders nothing during [InningsPhase.SETUP].
 *
 * @param state         Live [MatchState] from the scoring engine.
 * @param console       Live [ScoringConsoleState] with player and innings data.
 * @param matchOvers    Total overs for the match (from [com.example.scorebroadcaster.data.entity.Match.overs]),
 *                      used to compute balls remaining in the second innings.
 */
@Composable
fun ScoreboardOverlay(
    state: MatchState,
    console: ScoringConsoleState = ScoringConsoleState(),
    matchOvers: Int? = null,
    modifier: Modifier = Modifier
) {
    val model = remember(state, console, matchOvers) {
        BroadcastOverlayMapper.map(state, console, matchOvers)
    } ?: return

    val showBatters = model.striker != null || model.nonStriker != null
    val showBowler = model.bowler != null

    // Single unified lower-third strip – left / center / right columns
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            .background(OverlayBackground)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ── Left: batters ──────────────────────────────────────────────────────
        if (showBatters) {
            BattersSection(
                striker = model.striker,
                nonStriker = model.nonStriker,
                modifier = Modifier.weight(1f)
            )
        }

        // ── Center: score capsule ──────────────────────────────────────────────
        CenterScoreSection(
            model = model,
            modifier = Modifier.weight(if (showBatters || showBowler) 1.2f else 1f)
        )

        // ── Right: bowler + current-over balls ────────────────────────────────
        if (showBowler) {
            BowlerSection(
                bowler = checkNotNull(model.bowler),
                currentOverBalls = model.currentOverBalls,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─── Batters section ──────────────────────────────────────────────────────────

@Composable
private fun BattersSection(
    striker: BatterOverlayInfo?,
    nonStriker: BatterOverlayInfo?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        striker?.let { BatterRow(batter = it) }
        nonStriker?.let { BatterRow(batter = it) }
    }
}

@Composable
private fun BatterRow(batter: BatterOverlayInfo) {
    val nameColor = if (batter.isStriker) PrimaryText else SecondaryText
    val weight = if (batter.isStriker) FontWeight.Bold else FontWeight.Normal
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Striker indicator dot
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (batter.isStriker) StrikerDotColor else Color.Transparent)
        )

        Text(
            text = batter.name,
            color = nameColor,
            fontSize = 11.sp,
            fontWeight = weight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "${batter.runs}(${batter.balls})",
            color = nameColor,
            fontSize = 10.sp,
            fontWeight = weight
        )
    }
}

// ─── Center score section ─────────────────────────────────────────────────────

@Composable
private fun CenterScoreSection(
    model: BroadcastOverlayModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Line 1: matchTitle  score  overs – all on one row
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = model.matchTitle,
                color = SecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = model.score,
                color = PrimaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = model.overs,
                color = SecondaryText,
                fontSize = 10.sp
            )
        }
        // Line 2: run rate / chase info (always rendered to hold height)
        Text(
            text = model.contextLine ?: "",
            color = SecondaryText,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─── Bowler section ───────────────────────────────────────────────────────────

@Composable
private fun BowlerSection(
    bowler: BowlerOverlayInfo,
    currentOverBalls: List<String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // One line: name  W-R  (overs)
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = bowler.name,
                color = PrimaryText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = "${bowler.wickets}-${bowler.runs}",
                color = SecondaryText,
                fontSize = 10.sp
            )
            if (bowler.oversText.isNotEmpty()) {
                Text(
                    text = "(${bowler.oversText})",
                    color = SecondaryText,
                    fontSize = 10.sp
                )
            }
        }
        // Current-over ball circles – compact row
        if (currentOverBalls.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                currentOverBalls.forEach { ballLabel -> BallIndicator(label = ballLabel) }
            }
        }
    }
}

// ─── Ball indicator ───────────────────────────────────────────────────────────

@Composable
private fun BallIndicator(label: String) {
    val displayLabel = ballDisplayLabel(label)
    val isWicket = label == "W"
    val isBoundarySix = label == "6"
    val isBoundaryFour = label == "4"
    val isDot = label == "0" || label == "."
    val isWide = label == "Wd"
    val isNoBall = label == "NB"

    val bgColor = when {
        isWicket -> WicketColor
        isBoundarySix -> BoundarySixColor
        isBoundaryFour -> BoundaryFourColor
        isDot -> Color.Transparent
        isWide || isNoBall -> WideBallColor
        else -> NormalBallColor
    }
    val textColor = if (isDot) DotBorderColor else Color.White
    val borderModifier = if (isDot) {
        Modifier.border(1.dp, DotBorderColor, CircleShape)
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(bgColor)
            .then(borderModifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayLabel,
            color = textColor,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF228B22, widthDp = 640)
@Composable
private fun ScoreboardOverlayFirstInningsPreview() {
    Box {
        ScoreboardOverlay(
            state = MatchState(
                teamAName = "Lions",
                teamBName = "Falcons",
                runs = 177,
                wickets = 2,
                overs = 28,
                balls = 5,
                lastBalls = listOf("0", "W", "2", "0", "0", "1")
            ),
            console = ScoringConsoleState(
                inningsNumber = 1,
                phase = InningsPhase.FIRST_INNINGS,
                battingTeamName = "Lions",
                bowlingTeamName = "Falcons",
                strikerEntry = BattingEntry(player = Player(name = "Smith"), runs = 57, balls = 60),
                nonStrikerEntry = BattingEntry(player = Player(name = "Jones"), runs = 40, balls = 49),
                currentBowlerEntry = BowlingEntry(player = Player(name = "Patel"), wickets = 1, runs = 18)
            ),
            matchOvers = 50
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF228B22, widthDp = 640)
@Composable
private fun ScoreboardOverlaySecondInningsPreview() {
    Box {
        ScoreboardOverlay(
            state = MatchState(
                teamAName = "Falcons",
                teamBName = "Lions",
                runs = 155,
                wickets = 4,
                overs = 34,
                balls = 2,
                lastBalls = listOf("1", "4", "0", "0", "2")
            ),
            console = ScoringConsoleState(
                inningsNumber = 2,
                phase = InningsPhase.SECOND_INNINGS,
                battingTeamName = "Falcons",
                bowlingTeamName = "Lions",
                strikerEntry = BattingEntry(player = Player(name = "Ahmed"), runs = 63, balls = 71),
                nonStrikerEntry = BattingEntry(player = Player(name = "Kumar"), runs = 29, balls = 35),
                currentBowlerEntry = BowlingEntry(player = Player(name = "White"), wickets = 2, runs = 31),
                target = 178
            ),
            matchOvers = 50
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF228B22, widthDp = 640)
@Composable
private fun ScoreboardOverlayInningsBreakPreview() {
    Box {
        ScoreboardOverlay(
            state = MatchState(
                teamAName = "Lions",
                teamBName = "Falcons",
                runs = 200,
                wickets = 8,
                overs = 50,
                balls = 0
            ),
            console = ScoringConsoleState(
                inningsNumber = 1,
                phase = InningsPhase.INNINGS_BREAK,
                battingTeamName = "Lions",
                bowlingTeamName = "Falcons",
                target = 201
            )
        )
    }
}
