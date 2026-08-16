package com.ytune.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Dark = darkColorScheme(
    primary = Color(0xFF1ED760),
    onPrimary = Color(0xFF00210C),
    secondary = Color(0xFF8BD8A5),
    background = Color(0xFF090B0A),
    surface = Color(0xFF101311),
    surfaceVariant = Color(0xFF242925),
    surfaceContainer = Color(0xFF171B18),
    surfaceContainerHigh = Color(0xFF202521),
    onSurface = Color(0xFFF1F5F1),
    onSurfaceVariant = Color(0xFFADB7AF),
    outline = Color(0xFF58625A),
    error = Color(0xFFFFB4AB)
)

@Composable
fun YtuneTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark, shapes = Shapes(medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)), content = content)
}
