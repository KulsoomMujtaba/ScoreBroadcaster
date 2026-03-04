package com.example.scorebroadcaster.data

sealed class StreamingStatus {
    data object Idle : StreamingStatus()
    data object Connecting : StreamingStatus()
    data object Streaming : StreamingStatus()
    data object Reconnecting : StreamingStatus()
    data class Error(val message: String) : StreamingStatus()
}
