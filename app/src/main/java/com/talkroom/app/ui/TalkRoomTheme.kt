package com.talkroom.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Forest = Color(0xFF061F18)
val Emerald = Color(0xFF26E5A0)
val White = Color(0xFFF7FFFB)

@Composable
fun TalkRoomTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Emerald, onPrimary = Forest, secondary = Emerald, onSecondary = Forest,
        tertiary = White, background = Forest, onBackground = White,
        surface = Forest, onSurface = White,
        surfaceVariant = Color(0xFF10392C), onSurfaceVariant = White.copy(alpha = .6f),
        primaryContainer = Color(0xFF134D38), onPrimaryContainer = Emerald,
        outline = White.copy(alpha = .2f), error = White, onError = Forest
    ), content = content)
}
