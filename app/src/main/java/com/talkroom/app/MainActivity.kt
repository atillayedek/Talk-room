package com.talkroom.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.talkroom.app.ui.AppViewModel
import com.talkroom.app.ui.AppViewModelFactory
import com.talkroom.app.ui.TalkRoomApp
import com.talkroom.app.ui.TalkRoomTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(6, 31, 24)),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(6, 31, 24))
        )
        val container = (application as TalkRoomApplication).container
        setContent {
            TalkRoomTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val viewModel: AppViewModel = viewModel(
                        factory = AppViewModelFactory(container)
                    )
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    Box(Modifier.fillMaxSize()) {
                        TalkRoomApp(state = state, actions = viewModel)
                    }
                }
            }
        }
    }
}
