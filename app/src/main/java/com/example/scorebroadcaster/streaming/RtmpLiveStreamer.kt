package com.example.scorebroadcaster.streaming

import android.util.Log
import com.example.scorebroadcaster.data.StreamConfig
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtmps.RtmpsCamera2
import com.pedro.library.view.OpenGlView

private const val TAG = "RtmpLiveStreamer"
private const val RECONNECT_DELAY_MS = 5_000L
private const val MAX_RETRIES = 3

private const val VIDEO_WIDTH = 1280
private const val VIDEO_HEIGHT = 720
private const val VIDEO_FPS = 30
private const val AUDIO_BITRATE = 128_000
private const val AUDIO_SAMPLE_RATE = 44_100

/**
 * Callback interface that [RtmpLiveStreamer] uses to report RTMPS lifecycle events.
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
 * Wraps [RtmpsCamera2] (pedroSG94/RootEncoder) and manages the camera + RTMPS session.
 *
 * Uses **RTMPS** (TLS on port 443) which is required by Facebook Live and many other
 * modern ingest endpoints that reject plain RTMP.
 *
 * Usage:
 * 1. Construct with an [OpenGlView] and a [StreamStatusCallback].
 * 2. Call [startPreview] to open the camera preview on the surface.
 * 3. Call [start] with a [StreamConfig] to begin RTMPS streaming.
 * 4. Call [release] to stop the stream and camera when done.
 */
class RtmpLiveStreamer(
    openGlView: OpenGlView,
    private val callback: StreamStatusCallback
) {
    private var retryCount = 0

    // RtmpsCamera2 uses TLS – required by Facebook Live (rtmps://live-api-s.facebook.com:443/rtmp/)
    private val rtmpCamera = RtmpsCamera2(openGlView, object : ConnectChecker {
        override fun onConnectionStarted(url: String) {
            Log.d(TAG, "RTMPS connecting to $url")
            callback.onConnecting()
        }

        override fun onConnectionSuccess() {
            Log.d(TAG, "RTMPS connection established")
            retryCount = 0
            callback.onConnected()
        }

        override fun onConnectionFailed(reason: String) {
            Log.w(TAG, "RTMPS connection failed ($retryCount/$MAX_RETRIES): $reason")
            // Delegate to a named method so rtmpCamera.getStreamClient().reTry() can be called
            // safely after rtmpCamera is fully initialised.
            handleConnectionFailed(reason)
        }

        override fun onNewBitrate(bitrate: Long) {
            Log.v(TAG, "Bitrate update: $bitrate bps")
        }

        override fun onDisconnect() {
            Log.d(TAG, "RTMPS disconnected")
            callback.onDisconnected()
        }

        override fun onAuthError() {
            Log.e(TAG, "RTMPS authentication error")
            callback.onError("Authentication failed — check your stream key")
        }

        override fun onAuthSuccess() {
            Log.d(TAG, "RTMPS authentication succeeded")
        }
    })

    /** Opens the back-facing camera and shows the feed on the attached [OpenGlView]. */
    fun startPreview() {
        rtmpCamera.startPreview(CameraHelper.Facing.BACK, VIDEO_WIDTH, VIDEO_HEIGHT)
    }

    /**
     * Prepares H.264 + AAC encoders and starts pushing to the RTMPS endpoint derived from
     * [config].  Returns `false` and fires [StreamStatusCallback.onError] if encoder
     * preparation fails.
     */
    fun start(config: StreamConfig): Boolean {
        val videoOk = rtmpCamera.prepareVideo(
            VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS,
            config.bitrateKbps * 1_000,
            2,      // iFrameInterval — keyframe every 2 s; required by Facebook ingest
            0       // rotation
        )
        val audioOk = rtmpCamera.prepareAudio(AUDIO_BITRATE, AUDIO_SAMPLE_RATE, true)
        if (!videoOk || !audioOk) {
            callback.onError("Encoder preparation failed (video=$videoOk, audio=$audioOk)")
            return false
        }
        val url = buildRtmpUrl(config)
        Log.i(TAG, "Starting RTMPS stream → $url")
        rtmpCamera.startStream(url)
        return true
    }

    /** Stops the RTMPS stream (if active) and the camera preview. */
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
            // Stop the internal retry loop BEFORE surfacing the error, otherwise
            // RootEncoder keeps calling onConnectionFailed indefinitely.
            rtmpCamera.getStreamClient().stopRetry()
            callback.onError("Connection failed after $MAX_RETRIES attempts: $reason")
        }
    }

    /**
     * Builds the final stream URL.
     *
     * - If [StreamConfig.streamKey] is blank the [StreamConfig.serverUrl] is used as-is
     *   (the user already included the key in the URL).
     * - Otherwise the key is appended with exactly one `/` separator, stripping any
     *   trailing `/` from the base URL and leading `/` from the key to avoid doubles.
     *
     * Facebook Live example:
     *   serverUrl  = "rtmps://live-api-s.facebook.com:443/rtmp"
     *   streamKey  = "1234567890?s_ps=1&s_sw=0&s_vt=api-s&a=AbCd1234"
     *   result     = "rtmps://live-api-s.facebook.com:443/rtmp/1234567890?s_ps=1&..."
     */
    private fun buildRtmpUrl(config: StreamConfig): String {
        val key = config.streamKey.trim()
        return if (key.isEmpty()) {
            config.serverUrl.trimEnd('/')
        } else {
            "${config.serverUrl.trimEnd('/')}/${key.trimStart()}"
        }
    }
}