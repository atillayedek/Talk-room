package com.talkroom.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
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
        val container = (application as TalkRoomApplication).container
        setContent {
            TalkRoomTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val permissions = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) {}
                    LaunchedEffect(Unit) {
                        permissions.launch(
                            arrayOf(
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.CAMERA
                            )
                        )
                    }
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
