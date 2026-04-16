package com.devhub.scored.features.streaming.data
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import com.devhub.scored.features.scoring.data.MatchState
import com.devhub.scored.features.scoring.data.ScoringConsoleState
import com.devhub.scored.features.streaming.ui.BroadcastOverlayMapper
import com.devhub.scored.features.streaming.ui.BroadcastOverlayModel
import com.devhub.scored.features.streaming.ui.ballDisplayLabel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "ScoreboardOverlayRenderer"

// ── Named constants for layout dimensions ─────────────────────────────────────
private const val STRIKER_DOT_OFFSET_X = 8f        // px from section left edge to dot centre
private const val STRIKER_DOT_VERTICAL_CENTER = 0.5f  // fraction of row height
private const val STRIKER_DOT_RADIUS = 4f           // px

// Landscape layout constants
private const val LS_TITLE_TEXT_SIZE = 13f
private const val LS_SCORE_TEXT_SIZE = 18f
private const val LS_BATTER_NAME_TEXT_SIZE = 15f
private const val LS_BATTER_STATS_TEXT_SIZE = 13f
private const val LS_BOWLER_NAME_TEXT_SIZE = 15f
private const val LS_BOWLER_FIGURES_TEXT_SIZE = 13f
private const val LS_BALL_TEXT_SIZE = 9f
private const val LS_SIDE_WIDTH_FRACTION = 0.36f
private const val LS_PAD = 10f
private const val LS_BALL_LABEL_GAP = 4f           // px gap between consecutive ball label tokens
private const val LS_BOWLER_NAME_WIDTH_FRACTION = 0.45f  // fraction of section width for bowler name

// Portrait layout constants – intentionally tuned for narrow width, not simply scaled down
private const val PT_TITLE_TEXT_SIZE = 10f
private const val PT_SCORE_TEXT_SIZE = 14f
private const val PT_BATTER_NAME_TEXT_SIZE = 12f
private const val PT_BATTER_STATS_TEXT_SIZE = 10f
private const val PT_BOWLER_NAME_TEXT_SIZE = 12f
private const val PT_BOWLER_FIGURES_TEXT_SIZE = 10f
private const val PT_BALL_TEXT_SIZE = 8f
private const val PT_SIDE_WIDTH_FRACTION = 0.30f
private const val PT_PAD = 6f
private const val PT_BALL_LABEL_GAP = 3f           // px gap between consecutive ball label tokens
private const val PT_BOWLER_NAME_WIDTH_FRACTION = 0.45f  // fraction of section width for bowler name

/** Two-space gap used to separate inline text segments within a single drawn line. */
private const val INLINE_GAP = "  "

/**
 * Renders a [MatchState] + [ScoringConsoleState] to a [Bitmap] using Android Canvas/Paint,
 * matching the TV-style broadcast lower-third layout of the Compose [ScoreboardOverlay].
 *
 * Layout (single slim strip):
 * - **Left** (~36 % width): striker/non-striker name + runs/balls (two lines)
 * - **Centre** (~28 % width): match title + innings badge, score (large), overs, run rate / chase
 * - **Right** (~36 % width): bowler name + figures, then a compact row of current-over ball labels
 *
 * A single [Bitmap] buffer ([streamWidth] × [overlayHeight], ARGB_8888) is allocated once
 * and reused across renders to minimise GC pressure. The buffer is erased to transparent
 * before each draw. Concurrent access is guarded by a [Mutex] so [render] is safe to call
 * from a background coroutine.
 *
 * @param streamWidth   Width of the video stream in pixels (default 1280).
 * @param overlayHeight Height of the overlay strip in pixels (default 90).
 * @param streamHeight  Full height of the video stream in pixels (default 720); used to detect
 *                      portrait vs landscape orientation for responsive layout adjustments.
 */
class ScoreboardOverlayRenderer(
    private val streamWidth: Int = 1280,
    private val overlayHeight: Int = 90,
    private val streamHeight: Int = 720
) {

    /** Single reused bitmap buffer; erased to transparent before each render. */
    private val bitmap: Bitmap =
        Bitmap.createBitmap(streamWidth, overlayHeight, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val mutex = Mutex()

    // ── Paint objects (allocated once) ────────────────────────────────────────

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0xCC, 0x1F, 0x3A, 0x5F)
        style = Paint.Style.FILL
    }

    // Darker contrasting background for the centre score capsule
    private val centerPanelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0xDD, 0x0D, 0x21, 0x37)
        style = Paint.Style.FILL
    }

    // Text paints – sizes are set per-render based on orientation
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD0D0D0")   // light grey – team names
        textSize = LS_TITLE_TEXT_SIZE
    }
    private val inningsBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFCC00")
        textSize = LS_TITLE_TEXT_SIZE
        typeface = Typeface.DEFAULT_BOLD
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = LS_SCORE_TEXT_SIZE
        typeface = Typeface.DEFAULT_BOLD
    }
    private val oversPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF2C94C")   // warm gold – overs highlight
        textSize = LS_TITLE_TEXT_SIZE
    }
    private val contextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCCCCCC")
        textSize = LS_TITLE_TEXT_SIZE
        typeface = Typeface.DEFAULT_BOLD
    }
    private val contextValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF2C94C")   // warm gold – run rate value
        textSize = LS_TITLE_TEXT_SIZE
        typeface = Typeface.DEFAULT_BOLD
    }
    private val batterNameBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = LS_BATTER_NAME_TEXT_SIZE
        typeface = Typeface.DEFAULT_BOLD
    }
    private val batterNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCCCCCC")
        textSize = LS_BATTER_NAME_TEXT_SIZE
    }
    private val batterStatsBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = LS_BATTER_STATS_TEXT_SIZE
        typeface = Typeface.DEFAULT_BOLD
    }
    private val batterStatsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCCCCCC")
        textSize = LS_BATTER_STATS_TEXT_SIZE
    }
    private val strikerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF2C94C")   // warm gold accent
        style = Paint.Style.FILL
    }
    private val bowlerNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = LS_BOWLER_NAME_TEXT_SIZE
        typeface = Typeface.DEFAULT_BOLD
    }
    private val bowlerFiguresPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCCCCCC")
        textSize = LS_BOWLER_FIGURES_TEXT_SIZE
    }

    /**
     * Text paint for ball labels – no circle drawn, color set per-ball in [drawBallLabel].
     * Uses LEFT alignment so each token can be positioned by its left edge.
     */
    private val ballTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = LS_BALL_TEXT_SIZE
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Renders [state] and [console] onto the reused [Bitmap] and returns it.
     *
     * Safe to call from a background coroutine; uses a [Mutex] to prevent concurrent writes.
     */
    suspend fun render(
        state: MatchState,
        console: ScoringConsoleState = ScoringConsoleState(),
        matchOvers: Int? = null
    ): Bitmap = mutex.withLock {
        Log.d(TAG, "Overlay render: phase=${console.phase} score=${state.runs}/${state.wickets}")
        bitmap.eraseColor(Color.TRANSPARENT)

        val model = BroadcastOverlayMapper.map(state, console, matchOvers)
        if (model == null) return@withLock bitmap

        val fw = streamWidth.toFloat()
        val fh = overlayHeight.toFloat()

        // Detect orientation from stream dimensions (portrait = taller than wide)
        val isPortrait = streamWidth < streamHeight

        // Apply orientation-specific font sizes using separate portrait/landscape constants
        if (isPortrait) {
            titlePaint.textSize = PT_TITLE_TEXT_SIZE
            inningsBadgePaint.textSize = PT_TITLE_TEXT_SIZE
            scorePaint.textSize = PT_SCORE_TEXT_SIZE
            oversPaint.textSize = PT_TITLE_TEXT_SIZE
            contextPaint.textSize = PT_TITLE_TEXT_SIZE
            contextValuePaint.textSize = PT_TITLE_TEXT_SIZE
            batterNameBoldPaint.textSize = PT_BATTER_NAME_TEXT_SIZE
            batterNamePaint.textSize = PT_BATTER_NAME_TEXT_SIZE
            batterStatsBoldPaint.textSize = PT_BATTER_STATS_TEXT_SIZE
            batterStatsPaint.textSize = PT_BATTER_STATS_TEXT_SIZE
            bowlerNamePaint.textSize = PT_BOWLER_NAME_TEXT_SIZE
            bowlerFiguresPaint.textSize = PT_BOWLER_FIGURES_TEXT_SIZE
            ballTextPaint.textSize = PT_BALL_TEXT_SIZE
        } else {
            titlePaint.textSize = LS_TITLE_TEXT_SIZE
            inningsBadgePaint.textSize = LS_TITLE_TEXT_SIZE
            scorePaint.textSize = LS_SCORE_TEXT_SIZE
            oversPaint.textSize = LS_TITLE_TEXT_SIZE
            contextPaint.textSize = LS_TITLE_TEXT_SIZE
            contextValuePaint.textSize = LS_TITLE_TEXT_SIZE
            batterNameBoldPaint.textSize = LS_BATTER_NAME_TEXT_SIZE
            batterNamePaint.textSize = LS_BATTER_NAME_TEXT_SIZE
            batterStatsBoldPaint.textSize = LS_BATTER_STATS_TEXT_SIZE
            batterStatsPaint.textSize = LS_BATTER_STATS_TEXT_SIZE
            bowlerNamePaint.textSize = LS_BOWLER_NAME_TEXT_SIZE
            bowlerFiguresPaint.textSize = LS_BOWLER_FIGURES_TEXT_SIZE
            ballTextPaint.textSize = LS_BALL_TEXT_SIZE
        }

        val ballLabelGap = if (isPortrait) PT_BALL_LABEL_GAP else LS_BALL_LABEL_GAP
        val bowlerNameWidthFraction = if (isPortrait) PT_BOWLER_NAME_WIDTH_FRACTION else LS_BOWLER_NAME_WIDTH_FRACTION

        // Single background rect for the full strip
        canvas.drawRect(0f, 0f, fw, fh, bgPaint)

        // ── Single-row: Left batters | Centre score | Right bowler+balls ──────
        val showBatters = model.striker != null || model.nonStriker != null
        val showBowler = model.bowler != null

        // Portrait uses narrower side sections so the center stays readable
        val sideWidthFraction = if (isPortrait) PT_SIDE_WIDTH_FRACTION else LS_SIDE_WIDTH_FRACTION
        val pad = if (isPortrait) PT_PAD else LS_PAD

        when {
            showBatters && showBowler -> {
                val leftW = fw * sideWidthFraction
                val rightW = fw * sideWidthFraction
                val centreX = leftW
                val centreW = fw - leftW - rightW
                drawBattersSection(model, pad, fh, leftW - pad * 2)
                drawCentreSection(model, centreX, fh, centreW, pad, isPortrait)
                drawBowlerSection(model, centreX + centreW, fh, rightW, pad, ballLabelGap, bowlerNameWidthFraction)
            }
            showBatters -> {
                val leftW = fw * 0.50f
                val centreW = fw - leftW
                drawBattersSection(model, pad, fh, leftW - pad * 2)
                drawCentreSection(model, leftW, fh, centreW, pad, isPortrait)
            }
            showBowler -> {
                val rightW = fw * 0.50f
                val centreW = fw - rightW
                drawCentreSection(model, 0f, fh, centreW, pad, isPortrait)
                drawBowlerSection(model, centreW, fh, rightW, pad, ballLabelGap, bowlerNameWidthFraction)
            }
            else -> {
                drawCentreSection(model, 0f, fh, fw, pad, isPortrait)
            }
        }

        bitmap
    }

    // ── Section drawing helpers ────────────────────────────────────────────────

    private fun drawBattersSection(
        model: BroadcastOverlayModel,
        left: Float,
        totalH: Float,
        width: Float
    ) {
        val nameMaxWidth = width * 0.58f
        val lineH = (totalH - 4f) / 2f
        listOf(model.striker, model.nonStriker).forEachIndexed { i, batter ->
            batter ?: return@forEachIndexed
            val rowTop = 2f + i * lineH
            val baselineY = rowTop + lineH * 0.72f
            val xText = left + 18f

            // Striker dot
            if (batter.isStriker) {
                canvas.drawCircle(
                    left + STRIKER_DOT_OFFSET_X,
                    rowTop + lineH * STRIKER_DOT_VERTICAL_CENTER,
                    STRIKER_DOT_RADIUS,
                    strikerDotPaint
                )
            }

            // Name (truncate to fit)
            val namePaint = if (batter.isStriker) batterNameBoldPaint else batterNamePaint
            val nameText = truncateText(batter.name, namePaint, nameMaxWidth)
            canvas.drawText(nameText, xText, baselineY, namePaint)

            // Stats right-aligned within section
            val statText = "${batter.runs}(${batter.balls})"
            val statPaint = if (batter.isStriker) batterStatsBoldPaint else batterStatsPaint
            val statX = left + width - statPaint.measureText(statText) - 6f
            canvas.drawText(statText, statX, baselineY, statPaint)
        }
    }

    private fun drawCentreSection(
        model: BroadcastOverlayModel,
        sectionLeft: Float,
        totalH: Float,
        sectionWidth: Float,
        pad: Float,
        isPortrait: Boolean
    ) {
        // Draw the distinct darker rounded capsule behind the center score area
        val panelLeft = sectionLeft + pad / 2f
        val panelRight = sectionLeft + sectionWidth - pad / 2f
        val panelCorner = 6f
        canvas.drawRoundRect(
            panelLeft, 2f,
            panelRight, totalH - 2f,
            panelCorner, panelCorner,
            centerPanelPaint
        )

        val left = sectionLeft + pad
        val width = sectionWidth - pad * 2
        val cx = left + width / 2f

        if (isPortrait) {
            // ── Portrait mode: two-line layout ────────────────────────────────
            // Line 1: team names (matchTitle), centered, smaller text
            titlePaint.textAlign = Paint.Align.CENTER
            val titleText = truncateText(model.matchTitle, titlePaint, width)
            val line1Y = totalH * 0.38f
            canvas.drawText(titleText, cx, line1Y, titlePaint)

            // Line 2: score • overs, centered, larger/bolder (primary info)
            val oversShort = model.overs.removeSuffix(" overs")
            val scoreLine = "${model.score} \u2022 $oversShort"
            scorePaint.textAlign = Paint.Align.CENTER
            val line2Y = totalH * 0.80f
            canvas.drawText(scoreLine, cx, line2Y, scorePaint)
        } else {
            // ── Landscape mode: existing layout unchanged ──────────────────────
            // Line 1: matchTitle  score  overs – all on one baseline
            val titleText = model.matchTitle
            val scoreText = "$INLINE_GAP${model.score}"
            val oversText = "$INLINE_GAP${model.overs}"

            val titleW = titlePaint.measureText(titleText)
            val scoreW = scorePaint.measureText(scoreText)
            val oversW = oversPaint.measureText(oversText)
            val line1TotalW = titleW + scoreW + oversW
            var x = cx - line1TotalW / 2f
            val line1Y = totalH * 0.42f

            titlePaint.textAlign = Paint.Align.LEFT
            canvas.drawText(titleText, x, line1Y, titlePaint)
            x += titleW
            scorePaint.textAlign = Paint.Align.LEFT
            canvas.drawText(scoreText, x, line1Y, scorePaint)
            x += scoreW
            oversPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(oversText, x, line1Y, oversPaint)

            // Line 2: context line (run rate / chase info)
            if (model.contextLine != null) {
                val line2Y = totalH * 0.78f
                if (model.contextLine.startsWith("RUN RATE ")) {
                    val rrValue = model.contextLine.removePrefix("RUN RATE ")
                    val labelText = "RR"
                    val valueText = " $rrValue"
                    val labelW = contextPaint.measureText(labelText)
                    val valueW = contextValuePaint.measureText(valueText)
                    val totalW = labelW + valueW
                    contextPaint.textAlign = Paint.Align.LEFT
                    contextValuePaint.textAlign = Paint.Align.LEFT
                    val startX = cx - totalW / 2f
                    canvas.drawText(labelText, startX, line2Y, contextPaint)
                    canvas.drawText(valueText, startX + labelW, line2Y, contextValuePaint)
                } else {
                    contextPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(model.contextLine, cx, line2Y, contextPaint)
                }
            }
        }
    }

    private fun drawBowlerSection(
        model: BroadcastOverlayModel,
        sectionLeft: Float,
        totalH: Float,
        sectionWidth: Float,
        pad: Float,
        ballLabelGap: Float = LS_BALL_LABEL_GAP,
        bowlerNameWidthFraction: Float = LS_BOWLER_NAME_WIDTH_FRACTION
    ) {
        val bowler = model.bowler ?: return
        val left = sectionLeft + pad

        // ── Line 1: name  W-R  (overs) on one baseline ────────────────────────
        val nameMaxWidth = sectionWidth * bowlerNameWidthFraction
        val nameText = truncateText(bowler.name, bowlerNamePaint, nameMaxWidth)
        val figuresText = "$INLINE_GAP${bowler.wickets}-${bowler.runs}"
        val oversDisplayText = if (bowler.oversText.isNotEmpty()) "$INLINE_GAP(${bowler.oversText})" else ""

        val lineY = totalH * 0.42f
        var x = left
        bowlerNamePaint.textAlign = Paint.Align.LEFT
        canvas.drawText(nameText, x, lineY, bowlerNamePaint)
        x += bowlerNamePaint.measureText(nameText)
        bowlerFiguresPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(figuresText, x, lineY, bowlerFiguresPaint)
        if (oversDisplayText.isNotEmpty()) {
            x += bowlerFiguresPaint.measureText(figuresText)
            canvas.drawText(oversDisplayText, x, lineY, bowlerFiguresPaint)
        }

        // ── Ball labels – compact left-to-right row at ~75 % height ──────────
        // Labels start from the left of the right section (aligned with bowler name)
        if (model.currentOverBalls.isNotEmpty()) {
            val by = totalH * 0.76f
            var bx = left
            model.currentOverBalls.forEach { ball ->
                val labelW = drawBallLabel(ball, bx, by)
                bx += labelW + ballLabelGap
            }
        }
    }

    /**
     * Draws a single ball outcome as a plain text token at ([x], [y]) using left-alignment.
     * No circle or border is drawn – just the colored label text.
     * Returns the measured width of the drawn label so the caller can advance [x].
     */
    private fun drawBallLabel(label: String, x: Float, y: Float): Float {
        val isWicket = label == "W"
        val isBoundarySix = label == "6"
        val isBoundaryFour = label == "4"
        val isWide = label == "Wd"
        val isNoBall = label == "NB"

        ballTextPaint.color = when {
            isWicket -> Color.parseColor("#FFFF4444")
            isBoundaryFour -> Color.parseColor("#FFF2C94C")
            isBoundarySix -> Color.parseColor("#FFFFAA00")
            isWide || isNoBall -> Color.parseColor("#FFF5A623")
            else -> Color.WHITE
        }
        val displayLabel = ballDisplayLabel(label)
        val textY = y - (ballTextPaint.ascent() + ballTextPaint.descent()) / 2f
        canvas.drawText(displayLabel, x, textY, ballTextPaint)
        return ballTextPaint.measureText(displayLabel)
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && paint.measureText("$truncated…") > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return "$truncated…"
    }
}
