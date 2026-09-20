package com.droidnova.mathfight.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MathFightColorScheme = darkColorScheme(
    primary = GamePrimary,
    onPrimary = GameBackground,
    primaryContainer = GamePrimaryDeep,
    onPrimaryContainer = GameTextPrimary,
    secondary = GameSecondary,
    onSecondary = GameBackground,
    secondaryContainer = GameSecondaryDeep,
    onSecondaryContainer = GameTextPrimary,
    tertiary = GameSuccess,
    onTertiary = GameBackground,
    tertiaryContainer = GameDisabled,
    onTertiaryContainer = GameSuccess,
    error = GameError,
    onError = GameBackground,
    errorContainer = GameSecondaryDeep,
    onErrorContainer = GameTextPrimary,
    background = GameBackground,
    onBackground = GameTextPrimary,
    surface = GameSurface,
    onSurface = GameTextPrimary,
    surfaceVariant = GameSurfaceRaised,
    onSurfaceVariant = GameTextSecondary,
    outline = GameOutline,
    outlineVariant = GameDisabled,
    surfaceTint = GamePrimary,
    inverseSurface = GameTextPrimary,
    inverseOnSurface = GameBackground,
    inversePrimary = GamePrimaryDeep,
    scrim = GameBackground
)

@Composable
fun MathFightTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            activity.window.statusBarColor = AndroidColor.TRANSPARENT
            activity.window.navigationBarColor = GameBackground.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activity.window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = MathFightColorScheme,
        typography = MathFightTypography,
        shapes = MathFightShapes,
        content = content
    )
}
