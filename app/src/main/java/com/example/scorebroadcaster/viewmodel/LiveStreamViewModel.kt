package com.example.scorebroadcaster.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.scorebroadcaster.data.MatchState
import com.example.scorebroadcaster.data.StreamConfig
import com.example.scorebroadcaster.data.StreamingStatus
import com.example.scorebroadcaster.streaming.RtmpLiveStreamer
import com.example.scorebroadcaster.streaming.ScoreboardOverlayRenderer
import com.example.scorebroadcaster.streaming.StreamStatusCallback
import com.pedro.library.view.OpenGlView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS_FILE = "stream_prefs"
private const val KEY_SERVER_URL = "server_url"
private const val KEY_STREAM_KEY = "stream_key"

class LiveStreamViewModel(application: Application) : AndroidViewModel(application) {

    private val _streamingStatus = MutableStateFlow<StreamingStatus>(StreamingStatus.Idle)
    val streamingStatus: StateFlow<StreamingStatus> = _streamingStatus.asStateFlow()

    /** Config staged by [prepareStreaming]; consumed by [startStreaming]. */
    private var pendingConfig: StreamConfig? = null

    private var streamer: RtmpLiveStreamer? = null

    /** Renders [MatchState] to a [Bitmap]; allocated once and reused. */
    private val overlayRenderer = ScoreboardOverlayRenderer()

    /** Active coroutine that collects match state and pushes overlay bitmaps. */
    private var overlayJob: Job? = null

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(application)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            application,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getLastServerUrl(): String = encryptedPrefs.getString(KEY_SERVER_URL, "") ?: ""

    fun getLastStreamKey(): String = encryptedPrefs.getString(KEY_STREAM_KEY, "") ?: ""

    /**
     * Persists [config] to encrypted prefs and stages it for [startStreaming].
     * Call this before navigating to `StreamPreviewScreen`.
     */
    fun prepareStreaming(config: StreamConfig) {
        encryptedPrefs.edit {
            putString(KEY_SERVER_URL, config.serverUrl)
            putString(KEY_STREAM_KEY, config.streamKey)
        }
        pendingConfig = config
    }

    /**
     * Creates an [RtmpLiveStreamer] using the [OpenGlView] from `StreamPreviewScreen`
     * and the config staged by [prepareStreaming], then begins camera preview + RTMP.
     *
     * While streaming, [matchStateFlow] is collected (debounced to ≤ 10 updates/s) and
     * each new [MatchState] is rendered to a bitmap on [Dispatchers.Default] before being
     * pushed into the stream as a scoreboard overlay.
     *
     * Status transitions: Connecting → Streaming (on connect) / Reconnecting / Error.
     */
    @OptIn(FlowPreview::class)
    fun startStreaming(openGlView: OpenGlView, matchStateFlow: StateFlow<MatchState>) {
        val config = pendingConfig ?: run {
            _streamingStatus.value = StreamingStatus.Error("No stream configuration set")
            return
        }
        _streamingStatus.value = StreamingStatus.Connecting

        val callback = object : StreamStatusCallback {
            override fun onConnecting() { _streamingStatus.value = StreamingStatus.Connecting }
            override fun onConnected() { _streamingStatus.value = StreamingStatus.Streaming }
            override fun onDisconnected() { _streamingStatus.value = StreamingStatus.Idle }
            override fun onReconnecting() { _streamingStatus.value = StreamingStatus.Reconnecting }
            override fun onError(message: String) {
                _streamingStatus.value = StreamingStatus.Error(message)
            }
        }

        streamer?.release()
        val activeStreamer = RtmpLiveStreamer(openGlView, callback).also { s ->
            s.startPreview()
            s.start(config)
        }
        streamer = activeStreamer

        // Collect MatchState changes and burn the scoreboard into each RTMP frame.
        overlayJob?.cancel()
        overlayJob = viewModelScope.launch {
            matchStateFlow
                .debounce(100L) // throttle to max ~10 updates/second
                .collect { state ->
                    val bitmap = withContext(Dispatchers.Default) {
                        overlayRenderer.render(state)
                    }
                    activeStreamer.updateOverlayBitmap(bitmap)
                }
        }
    }

    /** Stops the active RTMP stream and resets status to [StreamingStatus.Idle]. */
    fun stopStreaming() {
        overlayJob?.cancel()
        overlayJob = null
        streamer?.release()
        streamer = null
        _streamingStatus.value = StreamingStatus.Idle
    }

    override fun onCleared() {
        super.onCleared()
        overlayJob?.cancel()
        streamer?.release()
        streamer = null
    }
}
