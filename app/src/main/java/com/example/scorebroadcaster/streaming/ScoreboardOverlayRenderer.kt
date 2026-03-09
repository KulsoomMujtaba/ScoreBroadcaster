package com.example.scorebroadcaster.streaming

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
private const val STRIKER_DOT_OFFSET_X = 10f       // px from section left edge to dot centre
private const val STRIKER_DOT_VERTICAL_CENTER = 0.5f  // fraction of row height
private const val STRIKER_DOT_RADIUS = 5f          // px

private const val BALL_INDICATOR_RADIUS = 10f       // px
private const val BALL_INDICATOR_SPACING = 26f      // px between ball centre points

/**
 * Renders a [MatchState] + [ScoringConsoleState] to a [Bitmap] using Android Canvas/Paint,
 * matching the TV-style broadcast lower-third layout of the Compose [ScoreboardOverlay].
 *
 * Layout (three columns + optional context strip):
 * - Left (~35 %): striker and non-striker names, runs, and balls
 * - Centre (~30 %): match title, score (large), overs, innings badge
 * - Right (~35 %): bowler figures and current-over ball circles
 * - Bottom strip: run rate (first innings) or chase target (second innings)
 *
 * A single [Bitmap] buffer ([streamWidth] × [overlayHeight], ARGB_8888) is allocated once
 * and reused across renders to minimise GC pressure. The buffer is erased to transparent
 * before each draw. Concurrent access is guarded by a [Mutex] so [render] is safe to call
 * from a background coroutine.
 *
 * @param streamWidth   Width of the video stream in pixels (default 1280).
 * @param overlayHeight Height of the overlay strip in pixels (default 190).
 */
class ScoreboardOverlayRenderer(
    private val streamWidth: Int = 1280,
    private val overlayHeight: Int = 190
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
    private val sectionBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0xBB, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val contextBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0xAA, 0, 0, 0)
        style = Paint.Style.FILL
    }

    // Text paints
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCCCCCC")
        textSize = 22f
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFCC00")
        textSize = 52f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val oversPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCCCCCC")
        textSize = 20f
    }
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF005599")
        style = Paint.Style.FILL
    }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val batterNameBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val batterNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCCCCCC")
        textSize = 24f
    }
    private val batterStatsBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val batterStatsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCCCCCC")
        textSize = 22f
    }
    private val strikerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFCC00")
        style = Paint.Style.FILL
    }
    private val bowlerNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val bowlerFiguresPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCCCCCC")
        textSize = 22f
    }
    private val contextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFCC00")
        textSize = 22f
        typeface = Typeface.DEFAULT_BOLD
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
        strokeWidth = 2f
    }
    private val ballTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f
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

        val contextStripH = if (model.contextLine != null) 36f else 0f
        val mainH = overlayHeight - contextStripH
        val fw = streamWidth.toFloat()

        // Full background
        canvas.drawRect(0f, 0f, fw, overlayHeight.toFloat(), bgPaint)

        // ── Column boundaries ──────────────────────────────────────────────────
        val showBatters = model.striker != null || model.nonStriker != null
        val showBowler = model.bowler != null

        val pad = 12f
        val sectionCorner = 8f

        when {
            showBatters && showBowler -> {
                val leftW = fw * 0.36f
                val rightW = fw * 0.36f
                val centreX = leftW
                val centreW = fw - leftW - rightW
                drawBattersSection(model, pad, mainH, leftW - pad * 2, sectionCorner)
                drawCentreSection(model, centreX + pad, mainH, centreW - pad * 2, sectionCorner)
                drawBowlerSection(model, centreX + centreW + pad, mainH, rightW - pad * 2, sectionCorner)
            }
            showBatters -> {
                val leftW = fw * 0.50f
                val centreW = fw - leftW
                drawBattersSection(model, pad, mainH, leftW - pad * 2, sectionCorner)
                drawCentreSection(model, leftW + pad, mainH, centreW - pad * 2, sectionCorner)
            }
            showBowler -> {
                val rightW = fw * 0.50f
                val centreW = fw - rightW
                drawCentreSection(model, pad, mainH, centreW - pad * 2, sectionCorner)
                drawBowlerSection(model, centreW + pad, mainH, rightW - pad * 2, sectionCorner)
            }
            else -> {
                drawCentreSection(model, pad, mainH, fw - pad * 2, sectionCorner)
            }
        }

        // ── Context strip ──────────────────────────────────────────────────────
        if (model.contextLine != null) {
            canvas.drawRect(0f, mainH, fw, overlayHeight.toFloat(), contextBgPaint)
            val cy = mainH + contextStripH * 0.72f
            canvas.drawText(model.contextLine, pad + 4f, cy, contextPaint)
        }

        bitmap
    }

    // ── Section drawing helpers ────────────────────────────────────────────────

    private fun drawBattersSection(
        model: BroadcastOverlayModel,
        left: Float,
        mainH: Float,
        width: Float,
        corner: Float
    ) {
        canvas.drawRoundRect(
            RectF(left, 4f, left + width, mainH - 4f),
            corner, corner, sectionBgPaint
        )

        val nameMaxWidth = width * 0.60f
        val lineH = (mainH - 8f) / 2f
        listOf(model.striker, model.nonStriker).forEachIndexed { i, batter ->
            batter ?: return@forEachIndexed
            val rowTop = 4f + i * lineH
            val baselineY = rowTop + lineH * 0.65f
            val xText = left + 22f

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
            val statText = "${batter.runs} (${batter.balls})"
            val statPaint = if (batter.isStriker) batterStatsBoldPaint else batterStatsPaint
            val statX = left + width - statPaint.measureText(statText) - 8f
            canvas.drawText(statText, statX, baselineY, statPaint)
        }
    }

    private fun drawCentreSection(
        model: BroadcastOverlayModel,
        left: Float,
        mainH: Float,
        width: Float,
        corner: Float
    ) {
        canvas.drawRoundRect(
            RectF(left, 4f, left + width, mainH - 4f),
            corner, corner, sectionBgPaint
        )
        val cx = left + width / 2f

        // Match title
        titlePaint.textAlign = Paint.Align.CENTER
        canvas.drawText(model.matchTitle, cx, mainH * 0.22f, titlePaint)

        // Score (large)
        scorePaint.textAlign = Paint.Align.CENTER
        canvas.drawText(model.score, cx, mainH * 0.58f, scorePaint)

        // Overs
        oversPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(model.overs, cx, mainH * 0.76f, oversPaint)

        // Innings badge
        badgeTextPaint.textAlign = Paint.Align.CENTER
        val badgeText = model.inningsBadge
        val badgeW = badgeTextPaint.measureText(badgeText) + 16f
        val badgeH = badgeTextPaint.textSize + 8f
        val badgeLeft = cx - badgeW / 2f
        val badgeTop = mainH * 0.83f
        canvas.drawRoundRect(
            RectF(badgeLeft, badgeTop, badgeLeft + badgeW, badgeTop + badgeH),
            4f, 4f, badgeBgPaint
        )
        canvas.drawText(badgeText, cx, badgeTop + badgeH * 0.72f, badgeTextPaint)
    }

    private fun drawBowlerSection(
        model: BroadcastOverlayModel,
        left: Float,
        mainH: Float,
        width: Float,
        corner: Float
    ) {
        val bowler = model.bowler ?: return
        canvas.drawRoundRect(
            RectF(left, 4f, left + width, mainH - 4f),
            corner, corner, sectionBgPaint
        )

        val nameMaxWidth = width * 0.60f
        val nameText = truncateText(bowler.name, bowlerNamePaint, nameMaxWidth)
        val figuresText = "${bowler.wickets}-${bowler.runs}"

        val firstLineY = mainH * 0.40f
        canvas.drawText(nameText, left + 8f, firstLineY, bowlerNamePaint)
        val figX = left + width - bowlerFiguresPaint.measureText(figuresText) - 8f
        canvas.drawText(figuresText, figX, firstLineY, bowlerFiguresPaint)

        // Ball indicators
        if (model.currentOverBalls.isNotEmpty()) {
            val totalBallsW = model.currentOverBalls.size * BALL_INDICATOR_SPACING
            var bx = left + (width - totalBallsW) / 2f + BALL_INDICATOR_RADIUS
            val by = mainH * 0.72f
            model.currentOverBalls.forEach { ball ->
                drawBallIndicator(ball, bx, by, BALL_INDICATOR_RADIUS)
                bx += BALL_INDICATOR_SPACING
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
        if (!isDot) {
            ballTextPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(ballDisplayLabel(label), cx, cy + ballTextPaint.textSize * 0.35f, ballTextPaint)
        }
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
