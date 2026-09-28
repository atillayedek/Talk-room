package com.talkroom.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF0F766E),
    secondary = Color(0xFF6D5E00),
    tertiary = Color(0xFF725CFF),
    background = Color(0xFFF7F9F8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE1EAE7)
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    secondary = Color(0xFFE7C84A),
    tertiary = Color(0xFFB8AEFF),
    background = Color(0xFF101412),
    surface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFF26302C)
)

@Composable
fun TalkRoomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
