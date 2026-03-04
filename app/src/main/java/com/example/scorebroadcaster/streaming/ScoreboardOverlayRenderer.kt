package com.example.scorebroadcaster.streaming

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import com.example.scorebroadcaster.data.MatchState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "ScoreboardOverlayRenderer"

/**
 * Renders a [MatchState] to a [Bitmap] using Android Canvas/Paint.
 *
 * A single [Bitmap] buffer ([streamWidth] × [overlayHeight], ARGB_8888) is allocated once
 * and reused across renders to minimise GC pressure. The buffer is erased to transparent
 * before each draw. Concurrent access is guarded by a [Mutex] so [render] is safe to call
 * from a background coroutine.
 *
 * The returned bitmap is intended to be passed to [ImageObjectFilterRender.setImage] via
 * [RtmpLiveStreamer.updateOverlayBitmap] so the overlay is burned into the RTMP stream.
 *
 * Layout (bottom bar, similar to [com.example.scorebroadcaster.ui.ScoreboardOverlay]):
 * - Semi-transparent dark (~80 % opaque) background
 * - Optional top row: colour-coded last-ball delivery outcomes
 * - Main row: team title on the left; runs/wickets + overs on the right
 *
 * @param streamWidth  Width of the video stream in pixels (default 1280).
 * @param overlayHeight Height of the overlay strip in pixels (default 140).
 */
class ScoreboardOverlayRenderer(
    private val streamWidth: Int = 1280,
    private val overlayHeight: Int = 140
) {

    /** Single reused bitmap buffer; erased to transparent before each render. */
    private val bitmap: Bitmap =
        Bitmap.createBitmap(streamWidth, overlayHeight, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val mutex = Mutex()

    // ---- Paint objects (allocated once) ----------------------------------------

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0xCC, 0, 0, 0) // ~80 % opaque black, matching ScoreboardOverlay
        style = Paint.Style.FILL
    }

    private val primaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFDDDDDD")
        textSize = 24f
    }

    private val wicketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFF4444")
        textSize = 26f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val boundaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF44AAFF")
        textSize = 26f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val defaultBallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFDDDDDD")
        textSize = 26f
        typeface = Typeface.DEFAULT_BOLD
    }

    // ---- Public API ------------------------------------------------------------

    /**
     * Renders [state] onto the reused [Bitmap] and returns it.
     *
     * Safe to call from a background coroutine; uses a [Mutex] to prevent concurrent access
     * to the bitmap buffer.
     */
    suspend fun render(state: MatchState): Bitmap = mutex.withLock {
        Log.d(TAG, "Overlay updated for state: $state")

        bitmap.eraseColor(Color.TRANSPARENT)

        // Background
        canvas.drawRect(0f, 0f, streamWidth.toFloat(), overlayHeight.toFloat(), bgPaint)

        val hasLastBalls = state.lastBalls.isNotEmpty()
        val lastBallsRowHeight = if (hasLastBalls) overlayHeight * 0.38f else 0f
        val mainBarTop = lastBallsRowHeight

        // Optional top row: last-ball delivery outcomes
        if (hasLastBalls) {
            var x = 20f
            val ballY = lastBallsRowHeight - 10f
            state.lastBalls.forEach { ball ->
                val paint = when (ball) {
                    "W" -> wicketPaint
                    "4", "6" -> boundaryPaint
                    else -> defaultBallPaint
                }
                canvas.drawText(ball, x, ballY, paint)
                x += paint.measureText(ball) + 20f
            }
        }

        // Main broadcast bar
        val mainBarHeight = overlayHeight - mainBarTop
        val textBaselineY = mainBarTop + mainBarHeight * 0.62f

        // Left: team title
        canvas.drawText(
            "${state.teamAName} vs ${state.teamBName}",
            20f, textBaselineY, primaryPaint
        )

        // Right: score (runs / wickets)
        val scoreText = "${state.runs}/${state.wickets}"
        val scoreX = streamWidth - primaryPaint.measureText(scoreText) - 20f
        canvas.drawText(scoreText, scoreX, textBaselineY, primaryPaint)

        // Right: overs (below score)
        val oversText = "${state.overs}.${state.balls} ov"
        val oversX = streamWidth - secondaryPaint.measureText(oversText) - 20f
        val oversY = textBaselineY + secondaryPaint.textSize + 4f
        if (oversY <= overlayHeight) {
            canvas.drawText(oversText, oversX, oversY, secondaryPaint)
        }

        bitmap
    }
}
