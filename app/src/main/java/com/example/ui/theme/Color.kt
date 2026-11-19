package com.example.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

object ObColors {
    val ChromeBackground = Color(0xFF0B0D11)
    val SurfaceRecessed = Color(0xFF0F1217)
    val WorkspaceBackground = Color(0xFF14181F)
    val SurfaceTableBody = Color(0xFF1A1F28)
    val SurfacePanel = Color(0xFF1E242E)
    val SurfaceTableHeader = Color(0xFF232A36)
    val SurfaceButton = Color(0xFF252D3A)
    val SurfaceSelected = Color(0xFF1B334D)

    val BorderHairline = Color(0xFF2F3746)
    val BorderBright = Color(0xFF455166)
    val DividerSubtle = Color(0xFF272E3B)

    val AccentCyan = Color(0xFF2F94E8)
    val AccentCyanBright = Color(0xFF58B4FF)
    val AccentCyanContainer = Color(0xFF143554)

    val TextPrimary = Color(0xFFEEF2F7)
    val TextSecondary = Color(0xFFA7B3C4)
    val TextMuted = Color(0xFF778499)

    val SliderThumb = Color(0xFFF3F6FA)
    val SliderTrack = Color(0xFF0D1015)

    val StatusHitGreen = Color(0xFF4CD964)
    val StatusHitBg = Color(0xFF14331E)
    val StatusCustomOrange = Color(0xFFF59E35)
    val StatusCustomBg = Color(0xFF362612)
    val StatusBadRed = Color(0xFFEF5350)
    val StatusBadBg = Color(0xFF381618)
    val StatusRetryYellow = Color(0xFFF7C948)
    val StatusRetryBg = Color(0xFF362D12)
    val StatusToCheckAqua = Color(0xFF26C6DA)
    val StatusToCheckBg = Color(0xFF123036)
    val StatusBannedPurple = Color(0xFFBA68C8)
    val StatusBannedBg = Color(0xFF321A38)
}

@Immutable
data class ObExtendedColors(
    val hit: Color,
    val hitContainer: Color,
    val custom: Color,
    val customContainer: Color,
    val bad: Color,
    val badContainer: Color,
    val retry: Color,
    val retryContainer: Color,
    val toCheck: Color,
    val toCheckContainer: Color,
    val banned: Color,
    val bannedContainer: Color,
    val codeBackground: Color,
    val terminalBackground: Color
)

val DarkExtendedColors = ObExtendedColors(
    hit = ObColors.StatusHitGreen,
    hitContainer = ObColors.StatusHitBg,
    custom = ObColors.StatusCustomOrange,
    customContainer = ObColors.StatusCustomBg,
    bad = ObColors.StatusBadRed,
    badContainer = ObColors.StatusBadBg,
    retry = ObColors.StatusRetryYellow,
    retryContainer = ObColors.StatusRetryBg,
    toCheck = ObColors.StatusToCheckAqua,
    toCheckContainer = ObColors.StatusToCheckBg,
    banned = ObColors.StatusBannedPurple,
    bannedContainer = ObColors.StatusBannedBg,
    codeBackground = Color(0xFF0C0F14),
    terminalBackground = Color(0xFF0B0E13)
)

val LightExtendedColors = ObExtendedColors(
    hit = Color(0xFF137333),
    hitContainer = Color(0xFFE6F4EA),
    custom = Color(0xFFB06000),
    customContainer = Color(0xFFFEF3E2),
    bad = Color(0xFFC5221F),
    badContainer = Color(0xFFFCE8E6),
    retry = Color(0xFF9A6700),
    retryContainer = Color(0xFFFFF8DC),
    toCheck = Color(0xFF007B83),
    toCheckContainer = Color(0xFFE4F7FB),
    banned = Color(0xFF7627BB),
    bannedContainer = Color(0xFFF3E8FD),
    codeBackground = Color(0xFFF0F4F9),
    terminalBackground = Color(0xFFF4F6FA)
)

val LocalObExtendedColors = staticCompositionLocalOf { DarkExtendedColors }

val ObDarkColorScheme = darkColorScheme(
    primary = ObColors.AccentCyan,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = ObColors.AccentCyanContainer,
    onPrimaryContainer = Color(0xFFD6EBFF),
    secondary = ObColors.AccentCyanBright,
    onSecondary = Color(0xFF0A1929),
    secondaryContainer = ObColors.SurfaceSelected,
    onSecondaryContainer = ObColors.TextPrimary,
    tertiary = ObColors.StatusToCheckAqua,
    onTertiary = Color(0xFF062024),
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
    outline = ObColors.BorderBright,
    outlineVariant = ObColors.BorderHairline,
    error = ObColors.StatusBadRed,
    onError = Color(0xFFFFFFFF),
    errorContainer = ObColors.StatusBadBg,
    onErrorContainer = Color(0xFFFFDAD6)
)

val ObLightColorScheme = lightColorScheme(
    primary = Color(0xFF1669C9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E6FB),
    onPrimaryContainer = Color(0xFF082D5C),
    secondary = Color(0xFF285F9E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2ECF8),
    onSecondaryContainer = Color(0xFF102A47),
    tertiary = Color(0xFF007B83),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE8EDF5),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE8EEF6),
    surfaceContainerHighest = Color(0xFFDCE4F0),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFD5DCE6),
    error = Color(0xFFC5221F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCE8E6),
    onErrorContainer = Color(0xFF690005)
)
