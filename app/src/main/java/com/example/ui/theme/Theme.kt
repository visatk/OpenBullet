package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.model.AppThemeMode

private val OpenBulletShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

object ObTheme {
    val colors: ObExtendedColors
        @Composable get() = LocalObExtendedColors.current
}

@Composable
fun OpenBulletTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    useDynamicColor: Boolean = false,
    accentColorOverride: Color? = null,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

    val context = LocalContext.current
    val baseScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> ObDarkColorScheme
        else -> ObLightColorScheme
    }

    val finalScheme = if (!useDynamicColor && accentColorOverride != null) {
        val legibleOnPrimary = if (accentColorOverride.luminance() > 0.52f) {
            Color(0xFF0A0D12)
        } else {
            Color(0xFFFFFFFF)
        }
        val container = if (isDark) {
            accentColorOverride.copy(alpha = 0.22f)
        } else {
            accentColorOverride.copy(alpha = 0.14f)
        }
        baseScheme.copy(
            primary = accentColorOverride,
            onPrimary = legibleOnPrimary,
            secondary = accentColorOverride,
            primaryContainer = container,
            onPrimaryContainer = if (isDark) Color(0xFFEAF4FF) else Color(0xFF0E1B2B)
        )
    } else {
        baseScheme
    }

    val extendedColors = if (isDark) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalObExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = finalScheme,
            typography = OpenBulletTypography,
            shapes = OpenBulletShapes,
            content = content
        )
    }
}
