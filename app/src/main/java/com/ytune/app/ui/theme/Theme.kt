package com.ytune.app.ui.theme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val Dark = darkColorScheme(primary = Color(0xFFE53935), secondary = Color(0xFFFF8A80), background = Color(0xFF0D0D0D), surface = Color(0xFF171717))
@Composable fun YtuneTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = Dark, content = content) }
