package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val OpenBulletDarkColorScheme = darkColorScheme(
    primary = ObColors.AccentCyan,
    onPrimary = ObColors.TextPrimary,
    primaryContainer = ObColors.AccentCyanContainer,
    onPrimaryContainer = ObColors.AccentCyanBright,
    secondary = ObColors.AccentCyanBright,
    onSecondary = ObColors.ChromeBackground,
    secondaryContainer = ObColors.SurfaceSelected,
    onSecondaryContainer = ObColors.TextPrimary,
    tertiary = ObColors.StatusToCheckAqua,
    background = ObColors.WorkspaceBackground,
    onBackground = ObColors.TextPrimary,
    surface = ObColors.SurfacePanel,
    onSurface = ObColors.TextPrimary,
    surfaceVariant = ObColors.SurfaceTableHeader,
    onSurfaceVariant = ObColors.TextSecondary,
    surfaceContainerLowest = ObColors.ChromeBackground,
    surfaceContainerLow = ObColors.SurfaceTableBody,
    surfaceContainer = ObColors.SurfacePanel,
    surfaceContainerHigh = ObColors.SurfaceTableHeader,
    surfaceContainerHighest = ObColors.SurfaceButton,
    outline = ObColors.BorderHairline,
    outlineVariant = ObColors.DividerSubtle,
    error = ObColors.StatusBadRed,
    onError = ObColors.TextPrimary
)

private val OpenBulletShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun OpenBulletTheme(
    accentColorOverride: Color? = null,
    content: @Composable () -> Unit
) {
    val scheme = if (accentColorOverride != null) {
        OpenBulletDarkColorScheme.copy(
            primary = accentColorOverride,
            secondary = accentColorOverride,
            primaryContainer = accentColorOverride.copy(alpha = 0.22f),
            onPrimaryContainer = accentColorOverride
        )
    } else {
        OpenBulletDarkColorScheme
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = OpenBulletTypography,
        shapes = OpenBulletShapes,
        content = content
    )
}
