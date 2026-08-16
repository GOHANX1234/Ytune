package com.ytune.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LiquidDark = darkColorScheme(
    primary = Color(0xFF5EABFF),
    onPrimary = Color(0xFF00315E),
    primaryContainer = Color(0xFF18426E),
    onPrimaryContainer = Color(0xFFD4E3FF),
    secondary = Color(0xFFB8C8DC),
    onSecondary = Color(0xFF233140),
    secondaryContainer = Color(0xFF394857),
    onSecondaryContainer = Color(0xFFD4E4F8),
    tertiary = Color(0xFFCDBEE0),
    onTertiary = Color(0xFF352A44),
    tertiaryContainer = Color(0xFF4C405C),
    onTertiaryContainer = Color(0xFFE9DAFC),
    surface = Color(0xFF0E0E12),
    onSurface = Color(0xFFEAE8EF),
    surfaceVariant = Color(0xFF3A3A44),
    onSurfaceVariant = Color(0xFFBDBCC6),
    surfaceTint = Color(0xFF5EABFF),
    surfaceContainerLowest = Color(0xFF08080C),
    surfaceContainerLow = Color(0xFF16161C),
    surfaceContainer = Color(0xFF1C1C24),
    surfaceContainerHigh = Color(0xFF26262E),
    surfaceContainerHighest = Color(0xFF303038),
    background = Color(0xFF08080C),
    onBackground = Color(0xFFEAE8EF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    outline = Color(0xFF8A8A94),
    outlineVariant = Color(0xFF3A3A44),
    inverseSurface = Color(0xFFEAE8EF),
    inversePrimary = Color(0xFF1A5C9E),
    scrim = Color(0xFF000000)
)

private val LiquidShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun YtuneTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(
        colorScheme = LiquidDark,
        shapes = LiquidShapes,
        typography = MaterialTheme.typography.copy(
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            headlineSmall = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            labelLarge = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
        ),
        content = content
    )
}
