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

private val OverlayBackground = Color(0xCC000000)
private val SectionBackground = Color(0xBB000000)
private val PrimaryText = Color.White
private val SecondaryText = Color(0xFFCCCCCC)
private val AccentColor = Color(0xFFFFCC00)      // amber/gold – score headline
private val WicketColor = Color(0xFFFF4444)
private val BoundaryColor = Color(0xFF44AAFF)
private val DotBorderColor = Color(0xFF888888)
private val StrikerDotColor = Color(0xFFFFCC00)  // amber indicator next to striker name

// ─── Public composable ────────────────────────────────────────────────────────

/**
 * TV-style broadcast lower-third scoreboard overlay.
 *
 * Renders a compact three-column bar:
 *  - **Left**: current batters (striker and non-striker) with runs and balls
 *  - **Center**: match title, score, overs, innings badge
 *  - **Right**: current bowler figures and current-over ball-by-ball indicators
 *
 * A thin context strip below shows run rate (first innings) or chase target (second innings).
 *
 * Sections are hidden automatically when their data is unavailable (e.g. between innings).
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(OverlayBackground)
    ) {
        // ── Main three-column row ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showBatters) {
                BattersSection(
                    striker = model.striker,
                    nonStriker = model.nonStriker,
                    modifier = Modifier.weight(1f)
                )
            }

            CenterScoreSection(
                model = model,
                modifier = Modifier.weight(if (showBatters || showBowler) 0.85f else 1f)
            )

            if (showBowler) {
                BowlerSection(
                    bowler = model.bowler!!,
                    currentOverBalls = model.currentOverBalls,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Context strip (run rate / chase) ──────────────────────────────────
        if (model.contextLine != null) {
            Text(
                text = model.contextLine,
                color = AccentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xAA000000))
                    .padding(horizontal = 12.dp, vertical = 3.dp)
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
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(SectionBackground)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        striker?.let { BatterRow(batter = it) }
        nonStriker?.let { BatterRow(batter = it) }
    }
}

@Composable
private fun BatterRow(batter: BatterOverlayInfo) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Striker indicator dot
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (batter.isStriker) StrikerDotColor else Color.Transparent)
        )

        Text(
            text = batter.name,
            color = PrimaryText,
            fontSize = 12.sp,
            fontWeight = if (batter.isStriker) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "${batter.runs} (${batter.balls})",
            color = if (batter.isStriker) PrimaryText else SecondaryText,
            fontSize = 12.sp,
            fontWeight = if (batter.isStriker) FontWeight.Bold else FontWeight.Normal
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
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(SectionBackground)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
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
            color = AccentColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = model.overs,
            color = SecondaryText,
            fontSize = 10.sp
        )
        // Innings badge pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF005599))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(
                text = model.inningsBadge,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
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
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(SectionBackground)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = bowler.name,
                color = PrimaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${bowler.wickets}-${bowler.runs}",
                color = SecondaryText,
                fontSize = 12.sp
            )
        }
        if (currentOverBalls.isNotEmpty()) {
            OverBallIndicators(balls = currentOverBalls)
        }
    }
}

// ─── Over ball indicators ─────────────────────────────────────────────────────

@Composable
private fun OverBallIndicators(balls: List<String>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        balls.forEach { ball -> BallIndicator(label = ball) }
    }
}

@Composable
private fun BallIndicator(label: String) {
    val isWicket = label == "W"
    val isBoundary = label == "4" || label == "6"
    val isDot = label == "0" || label == "."
    val isWide = label == "Wd"
    val isNoBall = label == "NB"

    val bgColor = when {
        isWicket -> WicketColor
        isBoundary -> BoundaryColor
        isDot -> Color.Transparent
        isWide || isNoBall -> Color(0xFF886600)
        else -> Color(0xFF444444)
    }
    val textColor = when {
        isDot -> DotBorderColor
        else -> Color.White
    }
    val displayText = when {
        label == "Wd" -> "W+"
        label == "NB" -> "N+"
        label.startsWith("LB") -> "L"
        label.startsWith("B") && !isWicket && !isBoundary -> "B"
        else -> label
    }
    val borderModifier = if (isDot) {
        Modifier.border(1.dp, DotBorderColor, CircleShape)
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .size(16.dp)
            .background(bgColor, CircleShape)
            .then(borderModifier),
        contentAlignment = Alignment.Center
    ) {
        if (!isDot) {
            Text(
                text = displayText,
                color = textColor,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold
            )
        }
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
