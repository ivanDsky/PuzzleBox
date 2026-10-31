package com.ivandsky.puzzlebox.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// One brand token set (ticket 10): the promoted playful look — saturated Duolingo-
// family signals on white in light (prototype C3), the same accents tuned brighter on
// true dark navy at night (prototype C6). Every on-color pair holds WCAG AA (>= 4.5:1)
// against its base in both modes; the designsystem unit tests guard that invariant.

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF58CC02),
    onPrimary = Color(0xFF1F3A0D),
    primaryContainer = Color(0xFFC6F58F),
    onPrimaryContainer = Color(0xFF1D3A00),
    secondary = Color(0xFF1CB0F6),
    onSecondary = Color(0xFF0B2A3B),
    secondaryContainer = Color(0xFFD5EEFB),
    onSecondaryContainer = Color(0xFF0A3141),
    tertiary = Color(0xFFFF9600),
    onTertiary = Color(0xFF3D2500),
    tertiaryContainer = Color(0xFFFFE3BF),
    onTertiaryContainer = Color(0xFF4F2E00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF33363D),
    surfaceVariant = Color(0xFFE9EFDE),
    onSurfaceVariant = Color(0xFF4A4E44),
    outline = Color(0xFF888C80),
    outlineVariant = Color(0xFFD9DCE0),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2F3134),
    inverseOnSurface = Color(0xFFF0F2EF),
    inversePrimary = Color(0xFF5ED413),
    surfaceDim = Color(0xFFE0E2DC),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8F4),
    surfaceContainer = Color(0xFFF1F3EE),
    surfaceContainerHigh = Color(0xFFEBEEE8),
    surfaceContainerHighest = Color(0xFFE4E8E1),
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6BDF17),
    onPrimary = Color(0xFF0C3200),
    primaryContainer = Color(0xFF245C00),
    onPrimaryContainer = Color(0xFFC4F98F),
    secondary = Color(0xFF1CB0F6),
    onSecondary = Color(0xFF072B3F),
    secondaryContainer = Color(0xFF0E4A66),
    onSecondaryContainer = Color(0xFFC9EAFB),
    tertiary = Color(0xFFFFC800),
    onTertiary = Color(0xFF3B2A00),
    tertiaryContainer = Color(0xFF5C4300),
    onTertiaryContainer = Color(0xFFFFDF9B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surface = Color(0xFF0F1A21),
    onSurface = Color(0xFFDBE5EA),
    surfaceVariant = Color(0xFF2A3843),
    onSurfaceVariant = Color(0xFFAFC3CF),
    outline = Color(0xFF7C909D),
    outlineVariant = Color(0xFF2E3C48),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFDBE5EA),
    inverseOnSurface = Color(0xFF2A3843),
    inversePrimary = Color(0xFF2B6B00),
    surfaceDim = Color(0xFF0F1A21),
    surfaceBright = Color(0xFF3B4B58),
    surfaceContainerLowest = Color(0xFF0A1015),
    surfaceContainerLow = Color(0xFF17222B),
    surfaceContainer = Color(0xFF1B2731),
    surfaceContainerHigh = Color(0xFF202E39),
    surfaceContainerHighest = Color(0xFF253441),
)
