package com.example.scorebroadcaster.streaming

import android.util.Log
import com.example.scorebroadcaster.data.StreamConfig
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.OpenGlView

private const val TAG = "RtmpLiveStreamer"
private const val RECONNECT_DELAY_MS = 5_000L
private const val MAX_RETRIES = 3

private const val VIDEO_WIDTH = 1280
private const val VIDEO_HEIGHT = 720
private const val VIDEO_FPS = 30
private const val AUDIO_BITRATE = 128_000
private const val AUDIO_SAMPLE_RATE = 44100

/**
 * Callback interface that [RtmpLiveStreamer] uses to report RTMP lifecycle events.
 * All methods may be called from a background thread.
 */
interface StreamStatusCallback {
    fun onConnecting()
    fun onConnected()
    fun onDisconnected()
    fun onReconnecting()
    fun onError(message: String)
}

/**
 * Wraps [RtmpCamera2] (pedroSG94/RootEncoder) and manages the camera + RTMP session.
 *
 * Usage:
 * 1. Construct with an [OpenGlView] and a [StreamStatusCallback].
 * 2. Call [startPreview] to open the camera preview on the surface.
 * 3. Call [start] with a [StreamConfig] to begin RTMP streaming.
 * 4. Call [release] to stop the stream and camera when done.
 */
class RtmpLiveStreamer(
    openGlView: OpenGlView,
    private val callback: StreamStatusCallback
) {
    private var retryCount = 0

    private val rtmpCamera = RtmpCamera2(openGlView, object : ConnectChecker {
        override fun onConnectionStarted(url: String) {
            Log.d(TAG, "RTMP connecting to $url")
            callback.onConnecting()
        }

        override fun onConnectionSuccess() {
            Log.d(TAG, "RTMP connection established")
            retryCount = 0
            callback.onConnected()
        }

        override fun onConnectionFailed(reason: String) {
            Log.w(TAG, "RTMP connection failed ($retryCount/$MAX_RETRIES): $reason")
            // Delegate to a named method so rtmpCamera.getStreamClient().reTry() can be called
            // safely after rtmpCamera is fully initialised.
            handleConnectionFailed(reason)
        }

        override fun onNewBitrate(bitrate: Long) {
            Log.v(TAG, "Bitrate update: $bitrate bps")
        }

        override fun onDisconnect() {
            Log.d(TAG, "RTMP disconnected")
            callback.onDisconnected()
        }

        override fun onAuthError() {
            Log.e(TAG, "RTMP authentication error")
            callback.onError("Authentication failed — check your stream key")
        }

        override fun onAuthSuccess() {
            Log.d(TAG, "RTMP authentication succeeded")
        }
    })

    /** Opens the back-facing camera and shows the feed on the attached [OpenGlView]. */
    fun startPreview() {
        rtmpCamera.startPreview(CameraHelper.Facing.BACK, VIDEO_WIDTH, VIDEO_HEIGHT)
    }

    /**
     * Prepares H.264 + AAC encoders and starts pushing to the RTMP endpoint derived from
     * [config].  Returns `false` and fires [StreamStatusCallback.onError] if encoder
     * preparation fails.
     */
    fun start(config: StreamConfig): Boolean {
        val videoOk = rtmpCamera.prepareVideo(
            VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS,
            config.bitrateKbps * 1_000,
            0,      // iFrameinterval (0 = auto)
            0 // rotation
        )
        val audioOk = rtmpCamera.prepareAudio(AUDIO_BITRATE, AUDIO_SAMPLE_RATE, true)
        if (!videoOk || !audioOk) {
            callback.onError("Encoder preparation failed (video=$videoOk, audio=$audioOk)")
            return false
        }
        val url = buildRtmpUrl(config)
        Log.i(TAG, "Starting RTMP stream → $url")
        rtmpCamera.startStream(url)
        return true
    }

    /** Stops the RTMP stream (if active) and the camera preview. */
    fun release() {
        try {
            rtmpCamera.stopStream()
        } catch (e: Exception) {
            Log.w(TAG, "stopStream: ${e.message}")
        }
        try {
            rtmpCamera.stopPreview()
        } catch (e: Exception) {
            Log.w(TAG, "stopPreview: ${e.message}")
        }
    }

    // ---- private helpers --------------------------------------------------------
    private fun handleConnectionFailed(reason: String) {
        if (retryCount < MAX_RETRIES) {
            retryCount++
            callback.onReconnecting()
            rtmpCamera.getStreamClient().reTry(RECONNECT_DELAY_MS, reason)
        } else {
            retryCount = 0
            // Tell RootEncoder it has no retries left so its internal shouldRetry() returns false,
            // preventing any further reconnect attempts after we surface the error.
            rtmpCamera.getStreamClient().setReTries(0)
            // Stop the stream cleanly to release the connection.
            try { rtmpCamera.stopStream() } catch (_: Exception) {}
            callback.onError("Connection failed after $MAX_RETRIES attempts: $reason")
        }
    }


    /**
     * Builds the final RTMP URL by appending [StreamConfig.streamKey] to
     * [StreamConfig.serverUrl], inserting a `/` separator when needed.
     */
    private fun buildRtmpUrl(config: StreamConfig): String {
        val key = config.streamKey.trim()
        return if (key.isEmpty()) {
            // Full URL was pasted into the Server URL field — use it directly
            config.serverUrl.trimEnd('/')
        } else {
            "${config.serverUrl.trimEnd('/')}/${key.trimStart('/')}"
        }
    }
}
