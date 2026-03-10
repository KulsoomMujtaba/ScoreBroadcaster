package com.example.scorebroadcaster.streaming

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import com.example.scorebroadcaster.data.MatchState
import com.example.scorebroadcaster.data.ScoringConsoleState
import com.example.scorebroadcaster.ui.BroadcastOverlayMapper
import com.example.scorebroadcaster.ui.BroadcastOverlayModel
import com.example.scorebroadcaster.ui.ballDisplayLabel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "ScoreboardOverlayRenderer"

// ── Named constants for layout dimensions ─────────────────────────────────────
private const val STRIKER_DOT_OFFSET_X = 8f        // px from section left edge to dot centre
private const val STRIKER_DOT_VERTICAL_CENTER = 0.5f  // fraction of row height
private const val STRIKER_DOT_RADIUS = 4f           // px

// Landscape defaults (portrait values are scaled down in render)
private const val BALL_INDICATOR_RADIUS = 5.5f      // px (reduced from 7f for slimmer over row)
private const val BALL_INDICATOR_SPACING = 12f      // px between ball centre points (reduced from 16f)

/** Two-space gap used to separate inline text segments within a single drawn line. */
private const val INLINE_GAP = "  "

/**
 * Renders a [MatchState] + [ScoringConsoleState] to a [Bitmap] using Android Canvas/Paint,
 * matching the TV-style broadcast lower-third layout of the Compose [ScoreboardOverlay].
 *
 * Layout (single slim strip):
 * - **Left** (~36 % width): striker/non-striker name + runs/balls (two lines)
 * - **Centre** (~28 % width): match title + innings badge, score (large), overs, run rate / chase
 * - **Right** (~36 % width): bowler name + figures, then a compact row of current-over ball circles
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

    // Text paints – sizes reduced to match compact 90 px strip
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD0D0D0")   // light grey – team names
        textSize = 13f
    }
    private val inningsBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFCC00")
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val oversPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF2C94C")   // warm gold – overs highlight
        textSize = 13f
    }
    private val contextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCCCCCC")
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val contextValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF2C94C")   // warm gold – run rate value
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val batterNameBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val batterNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCCCCCC")
        textSize = 15f
    }
    private val batterStatsBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val batterStatsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCCCCCC")
        textSize = 13f
    }
    private val strikerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF2C94C")   // warm gold accent
        style = Paint.Style.FILL
    }
    private val bowlerNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val bowlerFiguresPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCCCCCC")
        textSize = 13f
    }

    // Ball indicator paints – all outline/stroke style (broadcast-style)
    private val ballWicketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFF4444")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val ballBoundaryFourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF2C94C")   // warm gold for boundary 4
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val ballBoundarySixPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFAA00")   // stronger gold for six
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val ballWidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF5A623")   // lighter gold/amber for wide and no-ball
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val ballNormalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF888888")   // light grey for dots and normal runs
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    /** Text paint for ball labels – color set per-ball in drawBallIndicator. */
    private val ballTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 9f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
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

        // Detect orientation from stream dimensions
        val isPortrait = streamWidth < streamHeight

        // Apply orientation-responsive font sizes (portrait is 85% of landscape)
        val fontScale = if (isPortrait) 0.85f else 1f
        titlePaint.textSize = 13f * fontScale
        inningsBadgePaint.textSize = 13f * fontScale
        scorePaint.textSize = 18f * fontScale
        oversPaint.textSize = 13f * fontScale
        contextPaint.textSize = 13f * fontScale
        contextValuePaint.textSize = 13f * fontScale
        batterNameBoldPaint.textSize = 15f * fontScale
        batterNamePaint.textSize = 15f * fontScale
        batterStatsBoldPaint.textSize = 13f * fontScale
        batterStatsPaint.textSize = 13f * fontScale
        bowlerNamePaint.textSize = 15f * fontScale
        bowlerFiguresPaint.textSize = 13f * fontScale

        val ballRadius = if (isPortrait) BALL_INDICATOR_RADIUS * 0.85f else BALL_INDICATOR_RADIUS
        val ballSpacing = if (isPortrait) BALL_INDICATOR_SPACING * 0.85f else BALL_INDICATOR_SPACING

        // Single background rect for the full strip
        canvas.drawRect(0f, 0f, fw, fh, bgPaint)

        // ── Single-row: Left batters | Centre score | Right bowler+balls ──────
        val showBatters = model.striker != null || model.nonStriker != null
        val showBowler = model.bowler != null

        // Portrait uses narrower side sections so the center stays readable
        val sideWidthFraction = if (isPortrait) 0.32f else 0.36f
        val pad = if (isPortrait) 8f else 10f

        when {
            showBatters && showBowler -> {
                val leftW = fw * sideWidthFraction
                val rightW = fw * sideWidthFraction
                val centreX = leftW
                val centreW = fw - leftW - rightW
                drawBattersSection(model, pad, fh, leftW - pad * 2)
                drawCentreSection(model, centreX, fh, centreW, pad)
                drawBowlerSection(model, centreX + centreW + pad, fh, rightW - pad * 2, ballRadius, ballSpacing)
            }
            showBatters -> {
                val leftW = fw * 0.50f
                val centreW = fw - leftW
                drawBattersSection(model, pad, fh, leftW - pad * 2)
                drawCentreSection(model, leftW, fh, centreW, pad)
            }
            showBowler -> {
                val rightW = fw * 0.50f
                val centreW = fw - rightW
                drawCentreSection(model, 0f, fh, centreW, pad)
                drawBowlerSection(model, centreW + pad, fh, rightW - pad * 2, ballRadius, ballSpacing)
            }
            else -> {
                drawCentreSection(model, 0f, fh, fw, pad)
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
        val lineH = (totalH - 8f) / 2f
        listOf(model.striker, model.nonStriker).forEachIndexed { i, batter ->
            batter ?: return@forEachIndexed
            val rowTop = 4f + i * lineH
            val baselineY = rowTop + lineH * 0.70f
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
        pad: Float
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

        // ── Line 1: matchTitle  score  overs – all on one baseline ────────────
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

        // ── Line 2: context line (run rate / chase info) ───────────────────────
        if (model.contextLine != null) {
            val line2Y = totalH * 0.82f
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

    private fun drawBowlerSection(
        model: BroadcastOverlayModel,
        left: Float,
        totalH: Float,
        width: Float,
        ballRadius: Float = BALL_INDICATOR_RADIUS,
        ballSpacing: Float = BALL_INDICATOR_SPACING
    ) {
        val bowler = model.bowler ?: return

        // ── Line 1: name  W-R  (overs) on one baseline ────────────────────────
        val nameMaxWidth = width * 0.48f
        val nameText = truncateText(bowler.name, bowlerNamePaint, nameMaxWidth)
        val figuresText = "$INLINE_GAP${bowler.wickets}-${bowler.runs}"
        val oversDisplayText = if (bowler.oversText.isNotEmpty()) "$INLINE_GAP(${bowler.oversText})" else ""

        val lineY = totalH * 0.42f
        var x = left + 6f
        bowlerNamePaint.textAlign = Paint.Align.LEFT
        canvas.drawText(nameText, x, lineY, bowlerNamePaint)
        x += bowlerNamePaint.measureText(nameText)
        bowlerFiguresPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(figuresText, x, lineY, bowlerFiguresPaint)
        if (oversDisplayText.isNotEmpty()) {
            x += bowlerFiguresPaint.measureText(figuresText)
            canvas.drawText(oversDisplayText, x, lineY, bowlerFiguresPaint)
        }

        // ── Ball indicators – compact row at ~78 % height ─────────────────────
        if (model.currentOverBalls.isNotEmpty()) {
            val by = totalH * 0.80f
            val rightEdge = left + width - 6f
            var bx = rightEdge - ballRadius
            model.currentOverBalls.reversed().forEach { ball ->
                drawBallIndicator(ball, bx, by, ballRadius)
                bx -= ballSpacing
            }
        }
    }

    private fun drawBallIndicator(label: String, cx: Float, cy: Float, r: Float) {
        val isWicket = label == "W"
        val isBoundarySix = label == "6"
        val isBoundaryFour = label == "4"
        val isWide = label == "Wd"
        val isNoBall = label == "NB"

        // Draw outlined circle (broadcast style – no fill)
        val borderPaint = when {
            isWicket -> ballWicketPaint
            isBoundaryFour -> ballBoundaryFourPaint
            isBoundarySix -> ballBoundarySixPaint
            isWide || isNoBall -> ballWidePaint
            else -> ballNormalPaint  // light grey for dots and normal runs
        }
        canvas.drawCircle(cx, cy, r, borderPaint)

        // Draw label text centred inside the circle with matching text color
        val textColor = when {
            isWicket -> Color.parseColor("#FFFF4444")
            isBoundaryFour -> Color.parseColor("#FFF2C94C")
            isWide || isNoBall -> Color.parseColor("#FFF5A623")
            else -> Color.WHITE  // white for dot, normal runs, six
        }
        val displayLabel = ballDisplayLabel(label)
        ballTextPaint.color = textColor
        val textY = cy - (ballTextPaint.ascent() + ballTextPaint.descent()) / 2f
        canvas.drawText(displayLabel, cx, textY, ballTextPaint)
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
