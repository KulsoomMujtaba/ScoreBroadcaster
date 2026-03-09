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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "ScoreboardOverlayRenderer"

// ── Named constants for layout dimensions ─────────────────────────────────────
private const val STRIKER_DOT_OFFSET_X = 8f        // px from section left edge to dot centre
private const val STRIKER_DOT_VERTICAL_CENTER = 0.5f  // fraction of row height
private const val STRIKER_DOT_RADIUS = 4f           // px

private const val BALL_INDICATOR_RADIUS = 7f        // px
private const val BALL_INDICATOR_SPACING = 16f      // px between ball centre points

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
 */
class ScoreboardOverlayRenderer(
    private val streamWidth: Int = 1280,
    private val overlayHeight: Int = 90
) {

    /** Single reused bitmap buffer; erased to transparent before each render. */
    private val bitmap: Bitmap =
        Bitmap.createBitmap(streamWidth, overlayHeight, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val mutex = Mutex()

    // ── Paint objects (allocated once) ────────────────────────────────────────

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0xCC, 0, 0, 0)
        style = Paint.Style.FILL
    }

    // Text paints – sizes reduced to match compact 90 px strip
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCCCCCC")
        textSize = 13f
    }
    private val inningsBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFCC00")
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFCC00")
        textSize = 26f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val oversPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCCCCCC")
        textSize = 12f
    }
    private val contextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFCC00")
        textSize = 12f
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
        color = Color.parseColor("#FFFFCC00")
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

    // Ball indicator paints
    private val ballWicketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFF4444")
        style = Paint.Style.FILL
    }
    private val ballBoundaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF44AAFF")
        style = Paint.Style.FILL
    }
    private val ballRunsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF444444")
        style = Paint.Style.FILL
    }
    private val ballDotBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF888888")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
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

        // Single background rect for the full strip
        canvas.drawRect(0f, 0f, fw, fh, bgPaint)

        // ── Single-row: Left batters | Centre score | Right bowler+balls ──────
        val showBatters = model.striker != null || model.nonStriker != null
        val showBowler = model.bowler != null

        val pad = 10f

        when {
            showBatters && showBowler -> {
                val leftW = fw * 0.36f
                val rightW = fw * 0.36f
                val centreX = leftW
                val centreW = fw - leftW - rightW
                drawBattersSection(model, pad, fh, leftW - pad * 2)
                drawCentreSection(model, centreX + pad, fh, centreW - pad * 2)
                drawBowlerSection(model, centreX + centreW + pad, fh, rightW - pad * 2)
            }
            showBatters -> {
                val leftW = fw * 0.50f
                val centreW = fw - leftW
                drawBattersSection(model, pad, fh, leftW - pad * 2)
                drawCentreSection(model, leftW + pad, fh, centreW - pad * 2)
            }
            showBowler -> {
                val rightW = fw * 0.50f
                val centreW = fw - rightW
                drawCentreSection(model, pad, fh, centreW - pad * 2)
                drawBowlerSection(model, centreW + pad, fh, rightW - pad * 2)
            }
            else -> {
                drawCentreSection(model, pad, fh, fw - pad * 2)
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
        left: Float,
        totalH: Float,
        width: Float
    ) {
        val cx = left + width / 2f

        // Title + innings badge side by side, centred
        titlePaint.textAlign = Paint.Align.CENTER
        inningsBadgePaint.textAlign = Paint.Align.CENTER
        val titleText = model.matchTitle
        val badgeText = " ${model.inningsBadge}"
        val titleW = titlePaint.measureText(titleText)
        val badgeW = inningsBadgePaint.measureText(badgeText)
        val combinedLeft = cx - (titleW + badgeW) / 2f
        canvas.drawText(titleText, combinedLeft, totalH * 0.20f, titlePaint.apply { textAlign = Paint.Align.LEFT })
        canvas.drawText(badgeText, combinedLeft + titleW, totalH * 0.20f, inningsBadgePaint.apply { textAlign = Paint.Align.LEFT })

        // Score (large, centred)
        scorePaint.textAlign = Paint.Align.CENTER
        canvas.drawText(model.score, cx, totalH * 0.58f, scorePaint)

        // Overs
        oversPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(model.overs, cx, totalH * 0.78f, oversPaint)

        // Context line (run rate / chase info)
        if (model.contextLine != null) {
            contextPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(model.contextLine, cx, totalH * 0.95f, contextPaint)
        }
    }

    private fun drawBowlerSection(
        model: BroadcastOverlayModel,
        left: Float,
        totalH: Float,
        width: Float
    ) {
        val bowler = model.bowler ?: return

        val nameMaxWidth = width * 0.58f
        val nameText = truncateText(bowler.name, bowlerNamePaint, nameMaxWidth)
        val figuresText = "${bowler.wickets}-${bowler.runs}"

        // Name at ~20 % height, figures at ~45 % height
        val nameY = totalH * 0.28f
        val figuresY = totalH * 0.52f
        bowlerNamePaint.textAlign = Paint.Align.LEFT
        canvas.drawText(nameText, left + 6f, nameY, bowlerNamePaint)

        bowlerFiguresPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(figuresText, left + 6f, figuresY, bowlerFiguresPaint)

        // Ball indicators – compact row at ~78 % height, right-aligned within section
        if (model.currentOverBalls.isNotEmpty()) {
            val by = totalH * 0.80f
            val rightEdge = left + width - 6f
            // Draw right-to-left so last ball is on the right
            var bx = rightEdge - BALL_INDICATOR_RADIUS
            model.currentOverBalls.reversed().forEach { ball ->
                drawBallIndicator(ball, bx, by, BALL_INDICATOR_RADIUS)
                bx -= BALL_INDICATOR_SPACING
            }
        }
    }

    private fun drawBallIndicator(label: String, cx: Float, cy: Float, r: Float) {
        val isWicket = label == "W"
        val isBoundary = label == "4" || label == "6"
        val isDot = label == "0" || label == "."

        when {
            isWicket -> canvas.drawCircle(cx, cy, r, ballWicketPaint)
            isBoundary -> canvas.drawCircle(cx, cy, r, ballBoundaryPaint)
            isDot -> canvas.drawCircle(cx, cy, r, ballDotBorderPaint)
            else -> canvas.drawCircle(cx, cy, r, ballRunsPaint)
        }
        // No text labels in ball indicators
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
