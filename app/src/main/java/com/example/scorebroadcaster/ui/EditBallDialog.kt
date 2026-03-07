package com.example.scorebroadcaster.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.scorebroadcaster.data.entity.DismissalDetail
import com.example.scorebroadcaster.data.entity.DismissalType
import com.example.scorebroadcaster.data.entity.ExtrasBreakdown
import com.example.scorebroadcaster.data.entity.Player
import com.example.scorebroadcaster.domain.BallEvent
import com.example.scorebroadcaster.domain.IndexedBall

// ---------------------------------------------------------------------------
// Delivery mode used internally within the edit dialog
// ---------------------------------------------------------------------------

private enum class DeliveryMode { NORMAL, EXTRA }

// ---------------------------------------------------------------------------
// EditBallDialog
// ---------------------------------------------------------------------------

/**
 * Dialog that lets the scorer correct any previously recorded delivery.
 *
 * The dialog is pre-populated with the current values of [ball] so the scorer only
 * needs to change what is wrong. Pressing **Save** calls [onConfirm] with the
 * replacement [BallEvent]. Pressing **Delete** (with a confirmation step) calls
 * [onDelete]. Pressing **Cancel** or tapping outside calls [onDismiss].
 *
 * @param ball             The delivery being edited (carries its global index and display label).
 * @param battingPlayers   All players in the batting team — used for "who got out?" selection.
 * @param bowlingPlayers   All players in the bowling team — used for fielder / bowler selection.
 * @param onConfirm        Called with the replacement [BallEvent] when the scorer taps Save.
 * @param onDelete         Called (after confirmation) when the scorer taps Delete this ball.
 * @param onDismiss        Called when the dialog is dismissed without saving.
 */
@Composable
internal fun EditBallDialog(
    ball: IndexedBall,
    battingPlayers: List<Player>,
    bowlingPlayers: List<Player>,
    onConfirm: (BallEvent) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val event = ball.event

    // ------------------------------------------------------------------
    // Derive initial state from the existing BallEvent
    // ------------------------------------------------------------------

    val initialMode = if (event.extras.total > 0) DeliveryMode.EXTRA else DeliveryMode.NORMAL

    val initialExtraType: ExtraType = when {
        event.extras.wides   > 0 -> ExtraType.WIDE
        event.extras.noBalls > 0 -> ExtraType.NO_BALL
        event.extras.byes    > 0 -> ExtraType.BYE
        event.extras.legByes > 0 -> ExtraType.LEG_BYE
        else                     -> ExtraType.WIDE
    }

    // For extras, reconstruct the "total user-entered runs":
    //   Wide   → total wides (including 1-run penalty)
    //   NoBall → 1-run penalty + runs off bat
    //   Bye/LB → the bye/lb count
    val initialRuns: Int = when {
        event.extras.wides   > 0 -> event.extras.wides
        event.extras.noBalls > 0 -> 1 + event.runsOffBat
        event.extras.byes    > 0 -> event.extras.byes
        event.extras.legByes > 0 -> event.extras.legByes
        else                     -> event.runsOffBat
    }

    val commonPreset = listOf(0, 1, 2, 3, 4, 6)
    val initialUseCustom  = initialRuns !in commonPreset && initialMode == DeliveryMode.NORMAL
    val initialCustomText = if (initialUseCustom) initialRuns.toString() else ""

    // ------------------------------------------------------------------
    // Compose state
    // ------------------------------------------------------------------

    var deliveryMode  by remember { mutableStateOf(initialMode) }
    var extraType     by remember { mutableStateOf(initialExtraType) }

    // Normal delivery runs
    var selectedRuns   by remember { mutableStateOf(if (!initialUseCustom) initialRuns else 0) }
    var useCustomRuns  by remember { mutableStateOf(initialUseCustom) }
    var customRunsText by remember { mutableStateOf(initialCustomText) }

    // Extras runs
    val extraPreset = listOf(1, 2, 3, 4)
    val initialExtraUseCustom  = initialRuns !in extraPreset && initialMode == DeliveryMode.EXTRA
    var extraSelectedRuns  by remember {
        mutableStateOf(if (!initialExtraUseCustom && initialMode == DeliveryMode.EXTRA) initialRuns else 1)
    }
    var extraUseCustomRuns by remember { mutableStateOf(initialExtraUseCustom) }
    var extraCustomText    by remember {
        mutableStateOf(if (initialExtraUseCustom) initialRuns.toString() else "")
    }

    // Wicket
    var hasWicket        by remember { mutableStateOf(event.wicket) }
    var batterOut        by remember { mutableStateOf(event.dismissalDetail?.batter ?: battingPlayers.firstOrNull()) }
    var dismissalType    by remember { mutableStateOf(event.dismissalDetail?.dismissalType ?: DismissalType.BOWLED) }
    var selectedFielder  by remember { mutableStateOf(event.dismissalDetail?.fielder) }
    var selectedBowler   by remember { mutableStateOf(event.dismissalDetail?.bowler ?: bowlingPlayers.firstOrNull()) }

    // Delete confirmation
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // ------------------------------------------------------------------
    // Helper: build the edited BallEvent
    // ------------------------------------------------------------------

    fun buildEvent(): BallEvent {
        val dismissal: DismissalDetail? = if (hasWicket && batterOut != null) {
            val requiresFielder = dismissalType in listOf(
                DismissalType.CAUGHT, DismissalType.STUMPED, DismissalType.RUN_OUT
            )
            val effectiveBowler = if (dismissalType == DismissalType.RUN_OUT) null else selectedBowler
            DismissalDetail(
                batter      = batterOut!!,
                dismissalType = dismissalType,
                fielder     = if (requiresFielder) selectedFielder else null,
                bowler      = effectiveBowler
            )
        } else null

        return when (deliveryMode) {
            DeliveryMode.NORMAL -> {
                val runs = if (useCustomRuns) {
                    customRunsText.toIntOrNull()?.coerceAtLeast(0) ?: selectedRuns
                } else selectedRuns
                BallEvent(
                    runsOffBat      = runs,
                    extras          = ExtrasBreakdown.NONE,
                    wicket          = hasWicket,
                    dismissalDetail = dismissal,
                    countsAsBall    = true
                )
            }
            DeliveryMode.EXTRA -> {
                val runs = if (extraUseCustomRuns) {
                    extraCustomText.toIntOrNull()?.coerceAtLeast(1) ?: extraSelectedRuns
                } else extraSelectedRuns
                buildExtrasEventForEdit(extraType, runs, hasWicket, batterOut, selectedFielder)
            }
        }
    }

    // ------------------------------------------------------------------
    // Delete-confirmation dialog or main edit dialog
    // ------------------------------------------------------------------

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title  = { Text("Delete this ball?") },
            text   = {
                Text(
                    "Over ${ball.overNumber}, Ball ${ball.ballInOver} " +
                    "(\"${ball.display}\") will be removed and the innings " +
                    "score will be recalculated from scratch."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    } else {

        val isValid = !hasWicket || batterOut != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text       = "Edit Delivery",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text  = "Over ${ball.overNumber}, Ball ${ball.ballInOver}  ·  was: \"${ball.display}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {

                // ---- Delivery type: Normal vs Extra ----
                Text("Delivery type", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = deliveryMode == DeliveryMode.NORMAL,
                        onClick  = { deliveryMode = DeliveryMode.NORMAL; hasWicket = false },
                        label    = { Text("Normal") }
                    )
                    FilterChip(
                        selected = deliveryMode == DeliveryMode.EXTRA,
                        onClick  = { deliveryMode = DeliveryMode.EXTRA; hasWicket = false },
                        label    = { Text("Extra") }
                    )
                }

                HorizontalDivider()

                when (deliveryMode) {

                    // ---- Normal delivery ----
                    DeliveryMode.NORMAL -> {
                        Text("Runs off bat", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            commonPreset.forEach { runs ->
                                FilterChip(
                                    selected = !useCustomRuns && selectedRuns == runs,
                                    onClick  = {
                                        selectedRuns  = runs
                                        useCustomRuns = false
                                    },
                                    label = { Text(if (runs == 0) "·" else "$runs") }
                                )
                            }
                            FilterChip(
                                selected = useCustomRuns,
                                onClick  = { useCustomRuns = true },
                                label    = { Text("Other") }
                            )
                        }
                        if (useCustomRuns) {
                            OutlinedTextField(
                                value         = customRunsText,
                                onValueChange = { customRunsText = it.filter { c -> c.isDigit() } },
                                label         = { Text("Enter runs") },
                                singleLine    = true,
                                modifier      = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // ---- Extra delivery ----
                    DeliveryMode.EXTRA -> {
                        Text("Extra type", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ExtraType.entries.forEach { type ->
                                FilterChip(
                                    selected = extraType == type,
                                    onClick  = { extraType = type },
                                    label    = { Text(type.label) }
                                )
                            }
                        }

                        HorizontalDivider()

                        Text("Runs on delivery", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            extraPreset.forEach { runs ->
                                FilterChip(
                                    selected = !extraUseCustomRuns && extraSelectedRuns == runs,
                                    onClick  = {
                                        extraSelectedRuns  = runs
                                        extraUseCustomRuns = false
                                    },
                                    label = { Text("$runs") }
                                )
                            }
                            FilterChip(
                                selected = extraUseCustomRuns,
                                onClick  = { extraUseCustomRuns = true },
                                label    = { Text("5+") }
                            )
                        }
                        if (extraUseCustomRuns) {
                            OutlinedTextField(
                                value         = extraCustomText,
                                onValueChange = { extraCustomText = it.filter { c -> c.isDigit() } },
                                label         = { Text("Enter runs") },
                                singleLine    = true,
                                modifier      = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                HorizontalDivider()

                // ---- Wicket toggle ----
                Row(
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked         = hasWicket,
                        onCheckedChange = { checked ->
                            hasWicket = checked
                            if (!checked) {
                                batterOut       = battingPlayers.firstOrNull()
                                selectedFielder = null
                            }
                        }
                    )
                    val wicketLabel = if (deliveryMode == DeliveryMode.EXTRA)
                        "Wicket on this ball (Run Out only)"
                    else
                        "Wicket on this ball"
                    Text(wicketLabel, style = MaterialTheme.typography.bodyMedium)
                }

                // ---- Wicket detail section ----
                if (hasWicket) {
                    HorizontalDivider()

                    // Who got out?
                    Text("Who got out?", style = MaterialTheme.typography.labelMedium)
                    if (battingPlayers.isEmpty()) {
                        Text(
                            "No batting team players registered.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            battingPlayers.forEach { player ->
                                FilterChip(
                                    selected = batterOut?.id == player.id,
                                    onClick  = { batterOut = player },
                                    label    = { Text(player.name) }
                                )
                            }
                        }
                    }

                    // Dismissal type (only for normal deliveries)
                    if (deliveryMode == DeliveryMode.NORMAL) {
                        HorizontalDivider()
                        Text("How?", style = MaterialTheme.typography.labelMedium)
                        val types = DismissalType.entries
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                types.take(3).forEach { type ->
                                    FilterChip(
                                        selected = dismissalType == type,
                                        onClick  = {
                                            dismissalType   = type
                                            selectedFielder = null
                                        },
                                        label = { Text(type.label) }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                types.drop(3).forEach { type ->
                                    FilterChip(
                                        selected = dismissalType == type,
                                        onClick  = {
                                            dismissalType   = type
                                            selectedFielder = null
                                        },
                                        label = { Text(type.label) }
                                    )
                                }
                            }
                        }

                        // Fielder / bowler selectors
                        val requiresFielder = dismissalType in listOf(
                            DismissalType.CAUGHT, DismissalType.STUMPED, DismissalType.RUN_OUT
                        )
                        if (requiresFielder) {
                            HorizontalDivider()
                            val fielderLabel = when (dismissalType) {
                                DismissalType.CAUGHT  -> "Catcher"
                                DismissalType.STUMPED -> "Wicketkeeper"
                                else                  -> "Fielder (optional)"
                            }
                            Text(fielderLabel, style = MaterialTheme.typography.labelMedium)
                            if (bowlingPlayers.isEmpty()) {
                                Text(
                                    "No fielding team players registered.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    bowlingPlayers.forEach { player ->
                                        FilterChip(
                                            selected = selectedFielder?.id == player.id,
                                            onClick  = {
                                                selectedFielder =
                                                    if (selectedFielder?.id == player.id) null else player
                                            },
                                            label = { Text(player.name) }
                                        )
                                    }
                                }
                            }
                        }

                        // Bowler for credited dismissals
                        if (dismissalType != DismissalType.RUN_OUT) {
                            HorizontalDivider()
                            Text("Bowler", style = MaterialTheme.typography.labelMedium)
                            if (bowlingPlayers.isEmpty()) {
                                Text(
                                    "No bowling team players registered.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    bowlingPlayers.forEach { player ->
                                        FilterChip(
                                            selected = selectedBowler?.id == player.id,
                                            onClick  = { selectedBowler = player },
                                            label    = { Text(player.name) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // For extras, who was run out (fielder optional)
                    if (deliveryMode == DeliveryMode.EXTRA) {
                        HorizontalDivider()
                        Text("Fielder (optional)", style = MaterialTheme.typography.labelMedium)
                        if (bowlingPlayers.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                bowlingPlayers.forEach { player ->
                                    FilterChip(
                                        selected = selectedFielder?.id == player.id,
                                        onClick  = {
                                            selectedFielder =
                                                if (selectedFielder?.id == player.id) null else player
                                        },
                                        label = { Text(player.name) }
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // ---- Delete ----
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete this ball")
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { onConfirm(buildEvent()) },
                enabled  = isValid
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
    } // end else (main edit dialog)
}

// ---------------------------------------------------------------------------
// Private helper: build BallEvent for an extras correction
// ---------------------------------------------------------------------------

/**
 * Re-uses the same run-mapping logic as [buildExtrasEvent] in ScoringScreen
 * but is private to this file and used only within the edit dialog.
 */
private fun buildExtrasEventForEdit(
    type: ExtraType,
    runs: Int,
    hasWicket: Boolean,
    batterOut: Player?,
    fielder: Player?
): BallEvent {
    val dismissal: DismissalDetail? = if (hasWicket && batterOut != null) {
        DismissalDetail(
            batter        = batterOut,
            dismissalType = DismissalType.RUN_OUT,
            fielder       = fielder,
            bowler        = null
        )
    } else null

    return when (type) {
        ExtraType.WIDE -> BallEvent(
            extras          = ExtrasBreakdown(wides = runs),
            wicket          = hasWicket,
            dismissalDetail = dismissal,
            countsAsBall    = false
        )
        ExtraType.NO_BALL -> BallEvent(
            runsOffBat      = maxOf(0, runs - 1),
            extras          = ExtrasBreakdown(noBalls = 1),
            wicket          = hasWicket,
            dismissalDetail = dismissal,
            countsAsBall    = false
        )
        ExtraType.BYE -> BallEvent(
            extras          = ExtrasBreakdown(byes = runs),
            wicket          = hasWicket,
            dismissalDetail = dismissal,
            countsAsBall    = true
        )
        ExtraType.LEG_BYE -> BallEvent(
            extras          = ExtrasBreakdown(legByes = runs),
            wicket          = hasWicket,
            dismissalDetail = dismissal,
            countsAsBall    = true
        )
    }
}
