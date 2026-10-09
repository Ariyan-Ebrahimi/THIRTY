package dev.thirty.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkScheme = darkColorScheme(
    primary = ThirtyWhite,
    onPrimary = ThirtyBlack,
    background = ThirtyBlack,
    onBackground = ThirtyWhite,
    surface = ThirtySurface,
    onSurface = ThirtyWhite,
    surfaceVariant = ThirtySurface,
    onSurfaceVariant = ThirtyGray,
    outline = ThirtyBorder,
    outlineVariant = ThirtyBorder,
    secondary = ThirtyGray,
    tertiary = ThirtyWhite,
    error = ThirtyWhite,
    onError = ThirtyBlack,
    primaryContainer = ThirtyWhite,
    onPrimaryContainer = ThirtyBlack,
    secondaryContainer = ThirtySurface,
    onSecondaryContainer = ThirtyWhite
)

/** Light mode: black ink on white paper. No accent colour at all. */
private val LightScheme = lightColorScheme(
    primary = ThirtyLightText,
    onPrimary = ThirtyLightBackground,
    background = ThirtyLightBackground,
    onBackground = ThirtyLightText,
    surface = ThirtyLightSurface,
    onSurface = ThirtyLightText,
    surfaceVariant = ThirtyLightSurfaceVariant,
    onSurfaceVariant = ThirtyLightGray,
    outline = ThirtyLightBorder,
    outlineVariant = ThirtyLightBorder,
    secondary = ThirtyLightText,
    tertiary = ThirtyLightText,
    error = ThirtyLightText,
    onError = ThirtyLightBackground,
    primaryContainer = ThirtyLightText,
    onPrimaryContainer = ThirtyLightBackground,
    secondaryContainer = ThirtyLightSurfaceVariant,
    onSecondaryContainer = ThirtyLightText
)

@Composable
fun ThirtyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = scheme.background.toArgb()
            window.navigationBarColor = scheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = ThirtyTypography,
        content = content
    )
}
