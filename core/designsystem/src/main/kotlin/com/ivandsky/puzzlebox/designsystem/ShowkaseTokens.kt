package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.airbnb.android.showkase.annotation.ShowkaseColor
import com.airbnb.android.showkase.annotation.ShowkaseTypography

// Token registrations for Showkase's native Colors/Typography tabs (ticket 07). The
// app-default look is the dark scheme, so every color role aliases DarkColorScheme;
// type styles alias AppTypography. One alias per role — these are registrations for
// the browser, not a second token layer; nothing in the design system reads them.
// Kept `internal` per the spec's API rule (previews/token registrations stay out of
// the cross-module API); if the 1.0.5 processor rejects non-public elements these
// become public — resolved by compiling, outcome noted in reports/07-report.md.

// Material3 color roles, dark scheme (the app-default look).
@ShowkaseColor(name = "primary", group = "Colors")
internal val ShowkasePrimary: Color = DarkColorScheme.primary
@ShowkaseColor(name = "onPrimary", group = "Colors")
internal val ShowkaseOnPrimary: Color = DarkColorScheme.onPrimary
@ShowkaseColor(name = "primaryContainer", group = "Colors")
internal val ShowkasePrimaryContainer: Color = DarkColorScheme.primaryContainer
@ShowkaseColor(name = "onPrimaryContainer", group = "Colors")
internal val ShowkaseOnPrimaryContainer: Color = DarkColorScheme.onPrimaryContainer
@ShowkaseColor(name = "secondary", group = "Colors")
internal val ShowkaseSecondary: Color = DarkColorScheme.secondary
@ShowkaseColor(name = "onSecondary", group = "Colors")
internal val ShowkaseOnSecondary: Color = DarkColorScheme.onSecondary
@ShowkaseColor(name = "secondaryContainer", group = "Colors")
internal val ShowkaseSecondaryContainer: Color = DarkColorScheme.secondaryContainer
@ShowkaseColor(name = "onSecondaryContainer", group = "Colors")
internal val ShowkaseOnSecondaryContainer: Color = DarkColorScheme.onSecondaryContainer
@ShowkaseColor(name = "tertiary", group = "Colors")
internal val ShowkaseTertiary: Color = DarkColorScheme.tertiary
@ShowkaseColor(name = "onTertiary", group = "Colors")
internal val ShowkaseOnTertiary: Color = DarkColorScheme.onTertiary
@ShowkaseColor(name = "tertiaryContainer", group = "Colors")
internal val ShowkaseTertiaryContainer: Color = DarkColorScheme.tertiaryContainer
@ShowkaseColor(name = "onTertiaryContainer", group = "Colors")
internal val ShowkaseOnTertiaryContainer: Color = DarkColorScheme.onTertiaryContainer
@ShowkaseColor(name = "error", group = "Colors")
internal val ShowkaseError: Color = DarkColorScheme.error
@ShowkaseColor(name = "onError", group = "Colors")
internal val ShowkaseOnError: Color = DarkColorScheme.onError
@ShowkaseColor(name = "errorContainer", group = "Colors")
internal val ShowkaseErrorContainer: Color = DarkColorScheme.errorContainer
@ShowkaseColor(name = "onErrorContainer", group = "Colors")
internal val ShowkaseOnErrorContainer: Color = DarkColorScheme.onErrorContainer
@ShowkaseColor(name = "background", group = "Colors")
internal val ShowkaseBackground: Color = DarkColorScheme.background
@ShowkaseColor(name = "onBackground", group = "Colors")
internal val ShowkaseOnBackground: Color = DarkColorScheme.onBackground
@ShowkaseColor(name = "surface", group = "Colors")
internal val ShowkaseSurface: Color = DarkColorScheme.surface
@ShowkaseColor(name = "onSurface", group = "Colors")
internal val ShowkaseOnSurface: Color = DarkColorScheme.onSurface
@ShowkaseColor(name = "surfaceVariant", group = "Colors")
internal val ShowkaseSurfaceVariant: Color = DarkColorScheme.surfaceVariant
@ShowkaseColor(name = "onSurfaceVariant", group = "Colors")
internal val ShowkaseOnSurfaceVariant: Color = DarkColorScheme.onSurfaceVariant
@ShowkaseColor(name = "outline", group = "Colors")
internal val ShowkaseOutline: Color = DarkColorScheme.outline
@ShowkaseColor(name = "outlineVariant", group = "Colors")
internal val ShowkaseOutlineVariant: Color = DarkColorScheme.outlineVariant
@ShowkaseColor(name = "scrim", group = "Colors")
internal val ShowkaseScrim: Color = DarkColorScheme.scrim
@ShowkaseColor(name = "inverseSurface", group = "Colors")
internal val ShowkaseInverseSurface: Color = DarkColorScheme.inverseSurface
@ShowkaseColor(name = "inverseOnSurface", group = "Colors")
internal val ShowkaseInverseOnSurface: Color = DarkColorScheme.inverseOnSurface
@ShowkaseColor(name = "inversePrimary", group = "Colors")
internal val ShowkaseInversePrimary: Color = DarkColorScheme.inversePrimary
@ShowkaseColor(name = "surfaceDim", group = "Colors")
internal val ShowkaseSurfaceDim: Color = DarkColorScheme.surfaceDim
@ShowkaseColor(name = "surfaceBright", group = "Colors")
internal val ShowkaseSurfaceBright: Color = DarkColorScheme.surfaceBright
@ShowkaseColor(name = "surfaceContainerLowest", group = "Colors")
internal val ShowkaseSurfaceContainerLowest: Color = DarkColorScheme.surfaceContainerLowest
@ShowkaseColor(name = "surfaceContainerLow", group = "Colors")
internal val ShowkaseSurfaceContainerLow: Color = DarkColorScheme.surfaceContainerLow
@ShowkaseColor(name = "surfaceContainer", group = "Colors")
internal val ShowkaseSurfaceContainer: Color = DarkColorScheme.surfaceContainer
@ShowkaseColor(name = "surfaceContainerHigh", group = "Colors")
internal val ShowkaseSurfaceContainerHigh: Color = DarkColorScheme.surfaceContainerHigh
@ShowkaseColor(name = "surfaceContainerHighest", group = "Colors")
internal val ShowkaseSurfaceContainerHighest: Color = DarkColorScheme.surfaceContainerHighest

// The app's type styles (AppTypography, Type.kt — Baloo 2 throughout).
@ShowkaseTypography(name = "displayLarge", group = "Typography")
internal val ShowkaseTypographyDisplayLarge: TextStyle = AppTypography.displayLarge
@ShowkaseTypography(name = "displayMedium", group = "Typography")
internal val ShowkaseTypographyDisplayMedium: TextStyle = AppTypography.displayMedium
@ShowkaseTypography(name = "displaySmall", group = "Typography")
internal val ShowkaseTypographyDisplaySmall: TextStyle = AppTypography.displaySmall
@ShowkaseTypography(name = "headlineLarge", group = "Typography")
internal val ShowkaseTypographyHeadlineLarge: TextStyle = AppTypography.headlineLarge
@ShowkaseTypography(name = "headlineMedium", group = "Typography")
internal val ShowkaseTypographyHeadlineMedium: TextStyle = AppTypography.headlineMedium
@ShowkaseTypography(name = "headlineSmall", group = "Typography")
internal val ShowkaseTypographyHeadlineSmall: TextStyle = AppTypography.headlineSmall
@ShowkaseTypography(name = "titleLarge", group = "Typography")
internal val ShowkaseTypographyTitleLarge: TextStyle = AppTypography.titleLarge
@ShowkaseTypography(name = "titleMedium", group = "Typography")
internal val ShowkaseTypographyTitleMedium: TextStyle = AppTypography.titleMedium
@ShowkaseTypography(name = "titleSmall", group = "Typography")
internal val ShowkaseTypographyTitleSmall: TextStyle = AppTypography.titleSmall
@ShowkaseTypography(name = "bodyLarge", group = "Typography")
internal val ShowkaseTypographyBodyLarge: TextStyle = AppTypography.bodyLarge
@ShowkaseTypography(name = "bodyMedium", group = "Typography")
internal val ShowkaseTypographyBodyMedium: TextStyle = AppTypography.bodyMedium
@ShowkaseTypography(name = "bodySmall", group = "Typography")
internal val ShowkaseTypographyBodySmall: TextStyle = AppTypography.bodySmall
@ShowkaseTypography(name = "labelLarge", group = "Typography")
internal val ShowkaseTypographyLabelLarge: TextStyle = AppTypography.labelLarge
@ShowkaseTypography(name = "labelMedium", group = "Typography")
internal val ShowkaseTypographyLabelMedium: TextStyle = AppTypography.labelMedium
@ShowkaseTypography(name = "labelSmall", group = "Typography")
internal val ShowkaseTypographyLabelSmall: TextStyle = AppTypography.labelSmall
