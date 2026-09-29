package com.qwaicode.persiansubtitles.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.Shapes
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkNavyScheme = darkColorScheme(
    primary = Sky300,
    onPrimary = Navy950,
    primaryContainer = SkyContainer,
    onPrimaryContainer = SkyOnContainer,
    inversePrimary = SkyContainer,

    secondary = Teal300,
    onSecondary = Navy950,
    secondaryContainer = TealContainer,
    onSecondaryContainer = TealOnContainer,

    tertiary = Amber300,
    onTertiary = Navy950,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberOnContainer,

    background = Navy950,
    onBackground = TextHigh,
    surface = Navy900,
    onSurface = TextHigh,
    surfaceVariant = Navy700,
    onSurfaceVariant = TextMedium,
    surfaceTint = Sky400,
    inverseSurface = TextHigh,
    inverseOnSurface = Navy900,

    surfaceContainerLowest = Navy950,
    surfaceContainerLow = Navy850,
    surfaceContainer = Navy800,
    surfaceContainerHigh = Navy700,
    surfaceContainerHighest = Navy600,
    surfaceBright = Navy600,
    surfaceDim = Navy950,

    outline = OutlineBlue,
    outlineVariant = OutlineSoft,

    error = ErrorRed,
    onError = Navy950,
    errorContainer = ErrorContainer,
    onErrorContainer = ErrorOnContainer,

    scrim = Navy950,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/**
 * The app is dark-navy only — a subtitle tool is used next to a playing movie,
 * so a light theme would be the wrong default.
 */
@Composable
fun PersianSubtitlesTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkNavyScheme,
        typography = PersianTypography,
        shapes = AppShapes,
        content = content,
    )
}
