package com.example.scorebroadcaster.viewmodel

import androidx.lifecycle.ViewModel
import com.example.scorebroadcaster.data.MatchState
import com.example.scorebroadcaster.data.ScoreEvent
import com.example.scorebroadcaster.domain.reduce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MatchViewModel : ViewModel() {

    private val _events = MutableStateFlow<List<ScoreEvent>>(emptyList())

    private val _state = MutableStateFlow(MatchState())
    val state: StateFlow<MatchState> = _state.asStateFlow()

    fun addEvent(event: ScoreEvent) {
        _events.value = _events.value + event
        _state.value = reduce(_events.value)
    }

    fun undo() {
        if (_events.value.isNotEmpty()) {
            _events.value = _events.value.dropLast(1)
            _state.value = reduce(_events.value)
        }
    }
}
