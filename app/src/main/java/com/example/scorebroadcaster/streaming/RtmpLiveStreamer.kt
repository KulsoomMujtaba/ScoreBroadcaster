package com.example.scorebroadcaster.streaming

import android.util.Log
import android.view.SurfaceView
import com.example.scorebroadcaster.data.StreamConfig
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.rtmp.utils.ConnectCheckerRtmp

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
 * 1. Construct with a [SurfaceView] and a [StreamStatusCallback].
 * 2. Call [startPreview] to open the camera preview on the surface.
 * 3. Call [start] with a [StreamConfig] to begin RTMP streaming.
 * 4. Call [release] to stop the stream and camera when done.
 */
class RtmpLiveStreamer(
    surfaceView: SurfaceView,
    private val callback: StreamStatusCallback
) {
    private var retryCount = 0

    private val rtmpCamera = RtmpCamera2(surfaceView, object : ConnectCheckerRtmp {
        override fun onConnectionSuccessRtmp() {
            Log.d(TAG, "RTMP connection established")
            retryCount = 0
            callback.onConnected()
        }

        override fun onConnectionFailedRtmp(reason: String) {
            Log.w(TAG, "RTMP connection failed ($retryCount/$MAX_RETRIES): $reason")
            // Delegate to a named method so rtmpCamera.reTry() can be called safely
            // after rtmpCamera is fully initialised.
            onConnectionFailed(reason)
        }

        override fun onNewBitrateRtmp(bitrate: Long) {
            Log.v(TAG, "Bitrate update: $bitrate bps")
        }

        override fun onDisconnectRtmp() {
            Log.d(TAG, "RTMP disconnected")
            callback.onDisconnected()
        }

        override fun onAuthErrorRtmp() {
            Log.e(TAG, "RTMP authentication error")
            callback.onError("Authentication failed — check your stream key")
        }

        override fun onAuthSuccessRtmp() {
            Log.d(TAG, "RTMP authentication succeeded")
        }
    })

    /** Opens the back-facing camera and shows the feed on the attached [SurfaceView]. */
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
            config.bitrateKbps * 1_000
        )
        val audioOk = rtmpCamera.prepareAudio(AUDIO_BITRATE, AUDIO_SAMPLE_RATE, true)
        if (!videoOk || !audioOk) {
            callback.onError("Encoder preparation failed (video=$videoOk, audio=$audioOk)")
            return false
        }
        val url = buildRtmpUrl(config)
        Log.i(TAG, "Starting RTMP stream → $url")
        rtmpCamera.startStream(url)
        callback.onConnecting()
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

    private fun onConnectionFailed(reason: String) {
        if (retryCount < MAX_RETRIES) {
            retryCount++
            callback.onReconnecting()
            rtmpCamera.reTry(RECONNECT_DELAY_MS, reason)
        } else {
            retryCount = 0
            callback.onError("Connection failed after $MAX_RETRIES attempts: $reason")
        }
    }

    /**
     * Builds the final RTMP URL by appending [StreamConfig.streamKey] to
     * [StreamConfig.serverUrl], inserting a `/` separator when needed.
     */
    private fun buildRtmpUrl(config: StreamConfig): String {
        val base = config.serverUrl.trimEnd('/')
        return "$base/${config.streamKey}"
    }
}
