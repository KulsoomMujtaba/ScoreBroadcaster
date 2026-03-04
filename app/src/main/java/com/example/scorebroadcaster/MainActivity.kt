package com.example.scorebroadcaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.scorebroadcaster.ui.ScoringScreen
import com.example.scorebroadcaster.ui.theme.ScoreBroadcasterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScoreBroadcasterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ScoringScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}