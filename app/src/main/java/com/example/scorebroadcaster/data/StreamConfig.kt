package com.example.scorebroadcaster.data

data class StreamConfig(
    val serverUrl: String,
    val streamKey: String,
    val resolutionPreset: String = "720p",
    val bitrateKbps: Int = 2500
)
