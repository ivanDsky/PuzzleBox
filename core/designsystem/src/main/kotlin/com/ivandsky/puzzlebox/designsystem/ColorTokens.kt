package com.ivandsky.puzzlebox.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// One brand token set (ticket 10): the promoted playful look — saturated Duolingo-
// family signals on white in light (prototype C3), the same accents tuned brighter
// at night (prototype C6). Every on-color pair holds WCAG AA (>= 4.5:1) against its
// base in both modes; the designsystem unit tests guard that invariant.
//
// Ticket 11 neutralized the neutral surface ramp (user report: warm/red cast in the
// deepest layers): light is cool paper, dark is near-black slate with chroma that
// scales with elevation. The windowBackground in app/src/main/res values(-night)/
// colors.xml pairs with `surface` here — it IS the deepest layer at launch and in
// edge-to-edge areas, so the two must move together. Accent containers keep their
// character; only the neutral ramp changed.

// The primary hue's TEXT role. #58CC02 is a fill/background green — as text on white it
// holds only 2.09:1 — so text-bearing components (SecondaryButton) render this darker
// same-family step: 6.03:1 on white, 5.15:1 on the dialog surface. Dark mode's primary
// already clears AA as text (10.3:1), so it doubles as the dark text role.
val LightPrimaryText = Color(0xFF377000)
val DarkPrimaryText = Color(0xFF6BDF17)


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
    // background is painted by the app shell's Scaffold as the deepest canvas —
    // unset it defaults to Material's purple-pink baseline (#FEF7FF), the warm cast
    // of ticket 11's user report. It stays on the cool-paper ground with the cards.
    background = Color(0xFFF2F4F7),
    onBackground = Color(0xFF33363D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF33363D),
    surfaceVariant = Color(0xFFE2E7EE),
    onSurfaceVariant = Color(0xFF454A54),
    outline = Color(0xFF878D99),
    outlineVariant = Color(0xFFD9DCE0),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2F3134),
    inverseOnSurface = Color(0xFFF0F2F5),
    inversePrimary = Color(0xFF5ED413),
    surfaceDim = Color(0xFFDEE1E6),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color(0xFFF2F4F7),
    surfaceContainerHigh = Color(0xFFECEFF3),
    surfaceContainerHighest = Color(0xFFE4E8ED),
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
    // Same Scaffold note as light: unset background defaults to Material's warm
    // purple-dark baseline (#141218), which read reddish on device. Slates to surface.
    background = Color(0xFF101418),
    onBackground = Color(0xFFDBDEE2),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFDBDEE2),
    surfaceVariant = Color(0xFF292F38),
    onSurfaceVariant = Color(0xFFAEB4BD),
    outline = Color(0xFF7F8790),
    outlineVariant = Color(0xFF2C323A),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFDBDEE2),
    inverseOnSurface = Color(0xFF292F38),
    inversePrimary = Color(0xFF2B6B00),
    surfaceDim = Color(0xFF101418),
    surfaceBright = Color(0xFF3A424B),
    surfaceContainerLowest = Color(0xFF0B0D10),
    surfaceContainerLow = Color(0xFF16191F),
    surfaceContainer = Color(0xFF1A1E25),
    surfaceContainerHigh = Color(0xFF1F242C),
    surfaceContainerHighest = Color(0xFF242A33),
)

// Catalog-side hex label ("#RRGGBB") for the token screen's color rows; the 0xFFFFFF
// mask drops the alpha channel so both modes print the pure token color.
internal fun Color.toHex6(): String = "#%06X".format(toArgb() and 0xFFFFFF)
