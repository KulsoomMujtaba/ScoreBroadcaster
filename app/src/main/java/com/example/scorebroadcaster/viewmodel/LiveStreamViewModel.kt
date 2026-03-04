package com.example.scorebroadcaster.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.scorebroadcaster.data.StreamConfig
import com.example.scorebroadcaster.data.StreamingStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_FILE = "stream_prefs"
private const val KEY_SERVER_URL = "server_url"
private const val KEY_STREAM_KEY = "stream_key"

class LiveStreamViewModel(application: Application) : AndroidViewModel(application) {

    private val _streamingStatus = MutableStateFlow<StreamingStatus>(StreamingStatus.Idle)
    val streamingStatus: StateFlow<StreamingStatus> = _streamingStatus.asStateFlow()

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

    fun startStreaming(config: StreamConfig) {
        encryptedPrefs.edit()
            .putString(KEY_SERVER_URL, config.serverUrl)
            .putString(KEY_STREAM_KEY, config.streamKey)
            .apply()
        // TODO: implement actual RTMP streaming; update status to Streaming on success
        _streamingStatus.value = StreamingStatus.Connecting
    }

    fun stopStreaming() {
        _streamingStatus.value = StreamingStatus.Idle
    }
}
