package com.raybans.claudeassistant.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Claude brand colors
private val ClaudeOrange = Color(0xFFDA7756)
private val ClaudeOrangeDark = Color(0xFFB85C3D)
private val ClaudeCream = Color(0xFFFCF4E8)
private val ClaudeDark = Color(0xFF1A1A1A)

private val DarkColorScheme = darkColorScheme(
    primary = ClaudeOrange,
    onPrimary = Color.White,
    primaryContainer = ClaudeOrangeDark,
    onPrimaryContainer = ClaudeCream,
    secondary = Color(0xFF8B7355),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF5C4A3A),
    onSecondaryContainer = ClaudeCream,
    tertiary = Color(0xFF6B9E78),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF3D5E45),
    onTertiaryContainer = ClaudeCream,
    background = ClaudeDark,
    onBackground = ClaudeCream,
    surface = Color(0xFF2A2A2A),
    onSurface = ClaudeCream,
    surfaceVariant = Color(0xFF3A3A3A),
    onSurfaceVariant = Color(0xFFD0C4B8),
    error = Color(0xFFCF6679),
    onError = Color.Black,
    errorContainer = Color(0xFF8B0000),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = ClaudeOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE8E0),
    onPrimaryContainer = Color(0xFF3D1D12),
    secondary = Color(0xFF8B7355),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DDD4),
    onSecondaryContainer = Color(0xFF2D2318),
    tertiary = Color(0xFF4A7C59),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCE8D4),
    onTertiaryContainer = Color(0xFF1A3D24),
    background = ClaudeCream,
    onBackground = ClaudeDark,
    surface = Color.White,
    onSurface = ClaudeDark,
    surfaceVariant = Color(0xFFF5EDE4),
    onSurfaceVariant = Color(0xFF534341),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun ClaudeAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
