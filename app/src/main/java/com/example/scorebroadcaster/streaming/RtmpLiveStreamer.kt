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
 * Callback interface that [RtmpLiveStreamer] uses to report RTMP/RTMPS lifecycle events.
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
 * Wraps [RtmpCamera2] (pedroSG94/RootEncoder 2.4.7) and manages the camera + RTMP/RTMPS session.
 *
 * RTMPS support: [RtmpCamera2.startStream] in RootEncoder 2.4.7 accepts both `rtmp://` and
 * `rtmps://` URLs directly — the library negotiates TLS when the scheme is `rtmps://`.
 * No separate RtmpsCamera2 class is required; simply supply an `rtmps://` server URL.
 *
 * Usage:
 * 1. Construct with an [OpenGlView] and a [StreamStatusCallback].
 * 2. Call [startPreview] to open the camera preview on the surface.
 * 3. Call [start] with a [StreamConfig] to begin streaming (RTMP or RTMPS).
 * 4. Call [release] to stop the stream and camera when done.
 */
class RtmpLiveStreamer(
    openGlView: OpenGlView,
    private val callback: StreamStatusCallback
) {
    private var retryCount = 0

    private val rtmpCamera = RtmpCamera2(openGlView, object : ConnectChecker {
        override fun onConnectionStarted(url: String) {
            val scheme = if (url.startsWith("rtmps://", ignoreCase = true)) "RTMPS" else "RTMP"
            Log.i(TAG, "[$scheme] TCP handshake started → $url")
            callback.onConnecting()
        }

        override fun onConnectionSuccess() {
            Log.i(TAG, "Connection established — publish started successfully")
            retryCount = 0
            callback.onConnected()
        }

        override fun onConnectionFailed(reason: String) {
            Log.e(
                TAG,
                "Connection failed (attempt $retryCount/$MAX_RETRIES). "+
                "Reason: \"$reason\". "+
                "Common causes: wrong server URL, stream key, network blocked, "+
                "or server rejected the publish request."
            )
            handleConnectionFailed(reason)
        }

        override fun onNewBitrate(bitrate: Long) {
            Log.v(TAG, "Encoder bitrate update: "+(bitrate / 1_000)+" kbps")
        }

        override fun onDisconnect() {
            Log.i(TAG, "Stream disconnected cleanly")
            callback.onDisconnected()
        }

        override fun onAuthError() {
            Log.e(
                TAG,
                "Authentication error — the stream key or credentials were rejected by the server. "+
                "Verify the stream key in Stream Setup."
            )
            callback.onError("Auth failed — check your stream key")
        }

        override fun onAuthSuccess() {
            Log.i(TAG, "Authentication succeeded")
        }
    })

    /** Opens the back-facing camera and shows the feed on the attached [OpenGlView]. */
    fun startPreview() {
        Log.d(TAG, "Opening back camera preview at ${'${'}VIDEO_WIDTH}{'${'}'${'}'}x${'${'}VIDEO_HEIGHT}{'${'}'${'}'}")
        rtmpCamera.startPreview(CameraHelper.Facing.BACK, VIDEO_WIDTH, VIDEO_HEIGHT)
    }

    /**
     * Prepares H.264 + AAC encoders and starts pushing to the endpoint derived from [config].
     * Supports both `rtmp://` and `rtmps://` server URLs — RootEncoder 2.4.7 handles TLS
     * negotiation automatically when the scheme is `rtmps://`.
     *
     * Returns `false` and fires [StreamStatusCallback.onError] if encoder preparation fails.
     */
    fun start(config: StreamConfig): Boolean {
        val videoOk = rtmpCamera.prepareVideo(
            VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS,
            config.bitrateKbps * 1_000,
            2,  // iFrameInterval — one keyframe every 2 s (required by Facebook / most ingest servers)
            0   // rotation
        )
        val audioOk = rtmpCamera.prepareAudio(AUDIO_BITRATE, AUDIO_SAMPLE_RATE, true)

        if (!videoOk || !audioOk) {
            val msg = "Encoder preparation failed (video=$videoOk, audio=$audioOk). "+
                "This usually means the camera or mic is already in use, or the "+
                "requested resolution/bitrate is not supported by this device."
            Log.e(TAG, msg)
            callback.onError("Encoder init failed (video=$videoOk, audio=$audioOk)")
            return false
        }

        val url = buildStreamUrl(config)
        val scheme = if (url.startsWith("rtmps://", ignoreCase = true)) "RTMPS" else "RTMP"
        Log.i(TAG, "Starting $scheme stream → $url (bitrate=${'${'}config.bitrateKbps}{'${'}'${'}'} kbps, ${'${'}VIDEO_WIDTH}{'${'}'${'}'}x${'${'}VIDEO_HEIGHT}{'${'}'${'}'}@${'${'}VIDEO_FPS}{'${'}'${'}'}fps)")
        rtmpCamera.startStream(url)
        return true
    }

    /** Stops the stream (if active) and releases the camera preview. */
    fun release() {
        Log.d(TAG, "Releasing streamer (stopStream + stopPreview)")
        try {
            rtmpCamera.stopStream()
        } catch (e: Exception) {
            Log.w(TAG, "stopStream threw: ${e.message}")
        }
        try {
            rtmpCamera.stopPreview()
        } catch (e: Exception) {
            Log.w(TAG, "stopPreview threw: ${e.message}")
        }
    }

    // ---- private helpers --------------------------------------------------------

    private fun handleConnectionFailed(reason: String) {
        if (retryCount < MAX_RETRIES) {
            retryCount++
            Log.w(TAG, "Scheduling retry $retryCount/$MAX_RETRIES in ${'${'}RECONNECT_DELAY_MS}{'${'}'${'}'}ms")
            callback.onReconnecting()
            rtmpCamera.getStreamClient().reTry(RECONNECT_DELAY_MS, reason)
        } else {
            Log.e(TAG, "All $MAX_RETRIES retries exhausted — giving up. Last reason: \"$reason\"")
            retryCount = 0
            // Reset RootEncoder's internal retry counter so shouldRetry() returns false,
            // preventing ghost reconnect attempts after we surface the error.
            rtmpCamera.getStreamClient().setReTries(0)
            try { rtmpCamera.stopStream() } catch (_: Exception) {}
            callback.onError("Connection failed after $MAX_RETRIES attempts: $reason")
        }
    }

    /**
     * Builds the final stream URL from [StreamConfig.serverUrl] and [StreamConfig.streamKey].
     *
     * Supports:
     * - `rtmp://`  plain RTMP (e.g. local OBS relay, non-TLS ingest)
     * - `rtmps://` RTMPS/TLS (e.g. Facebook Live `rtmps://live-api-s.facebook.com:443/rtmp/`)
     *
     * If [StreamConfig.streamKey] is blank, [StreamConfig.serverUrl] is used as-is (the user
     * pasted the full URL including the key into the Server URL field).
     */
    private fun buildStreamUrl(config: StreamConfig): String {
        val server = config.serverUrl.trim()
        val key = config.streamKey.trim()

        return if (key.isEmpty()) {
            // Full URL (scheme + host + path + key) was entered in the Server URL field.
            server.trimEnd('/')
        } else {
            // Append key to server base, ensuring exactly one "/" separator.
            "${'${'}server.trimEnd('/')}{'${'}'${'}'}/${'${'}key.trimStart('/')}{'${'}'${'}'}"
        }
    }
}