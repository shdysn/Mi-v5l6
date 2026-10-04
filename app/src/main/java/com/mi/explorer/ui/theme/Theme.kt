package com.mi.explorer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = MiOrange,
    onPrimary = Color.White,
    primaryContainer = MiOrangeSubtle,
    onPrimaryContainer = MiOrangeDark,
    secondary = MiBlue,
    onSecondary = Color.White,
    secondaryContainer = MiBlueLight,
    onSecondaryContainer = MiBlue,
    background = Color(0xFFF7F8FA),
    onBackground = Gray900,
    surface = Color.White,
    onSurface = Gray900,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray600,
    outline = Gray300
)

private val DarkColorScheme = darkColorScheme(
    primary = MiOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2C1B11),
    onPrimaryContainer = MiOrangeLight,
    secondary = MiBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF14243A),
    onSecondaryContainer = MiBlue,
    background = Color(0xFF121212),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Gray400,
    outline = Color(0xFF383838)
)

private val AmoledColorScheme = darkColorScheme(
    primary = MiOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1C130D),
    onPrimaryContainer = MiOrangeLight,
    secondary = MiBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF0F1B2B),
    onSecondaryContainer = MiBlue,
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF121212),
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF262626)
)

@Composable
fun MiExplorerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        amoledMode -> AmoledColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
