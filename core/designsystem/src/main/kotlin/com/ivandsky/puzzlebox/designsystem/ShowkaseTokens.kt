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

// The nine type roles (Type.kt, spec A4 — Baloo 2 throughout), by role name; the other
// M3 slots alias these. The tabular entry is how every formatDuration time renders.
@ShowkaseTypography(name = "Wordmark", group = "Typography")
internal val ShowkaseTypeWordmark: TextStyle = WordmarkTextStyle
@ShowkaseTypography(name = "Display", group = "Typography")
internal val ShowkaseTypeDisplay: TextStyle = AppTypography.displaySmall
@ShowkaseTypography(name = "Headline", group = "Typography")
internal val ShowkaseTypeHeadline: TextStyle = AppTypography.headlineMedium
@ShowkaseTypography(name = "Title", group = "Typography")
internal val ShowkaseTypeTitle: TextStyle = AppTypography.titleLarge
@ShowkaseTypography(name = "TitleSmall", group = "Typography")
internal val ShowkaseTypeTitleSmall: TextStyle = AppTypography.titleMedium
@ShowkaseTypography(name = "Body", group = "Typography")
internal val ShowkaseTypeBody: TextStyle = AppTypography.bodyLarge
@ShowkaseTypography(name = "BodySmall", group = "Typography")
internal val ShowkaseTypeBodySmall: TextStyle = AppTypography.bodyMedium
@ShowkaseTypography(name = "Label", group = "Typography")
internal val ShowkaseTypeLabel: TextStyle = AppTypography.labelLarge
@ShowkaseTypography(name = "Eyebrow", group = "Typography")
internal val ShowkaseTypeEyebrow: TextStyle = AppTypography.labelSmall
@ShowkaseTypography(name = "DisplayTabular", group = "Typography")
internal val ShowkaseTypeDisplayTabular: TextStyle = AppTypography.displaySmall.tabular()

// Board roles (ticket 20, values = research U2's primary table), dark values —
// the app-default look, same aliasing rule as the Material ramp above. Group
// "Board" keeps them separable from the Material roles; the light counterparts
// render in the TokenCatalog board section, and the stroke weights show there too
// (spacing-style rows).
@ShowkaseColor(name = "boardSurface", group = "Board")
internal val ShowkaseBoardSurface: Color = DarkBoardTokens.boardSurface
@ShowkaseColor(name = "boardGridLine", group = "Board")
internal val ShowkaseBoardGridLine: Color = DarkBoardTokens.boardGridLine
@ShowkaseColor(name = "boardWall", group = "Board")
internal val ShowkaseBoardWall: Color = DarkBoardTokens.boardWall
@ShowkaseColor(name = "waypointDot", group = "Board")
internal val ShowkaseWaypointDot: Color = DarkBoardTokens.waypointDot
@ShowkaseColor(name = "waypointLabel", group = "Board")
internal val ShowkaseWaypointLabel: Color = DarkBoardTokens.waypointLabel
@ShowkaseColor(name = "waypointDotOnPath", group = "Board")
internal val ShowkaseWaypointDotOnPath: Color = DarkBoardTokens.waypointDotOnPath
@ShowkaseColor(name = "pathTrace", group = "Board")
internal val ShowkasePathTrace: Color = DarkBoardTokens.pathTrace
@ShowkaseColor(name = "boardSelection", group = "Board")
internal val ShowkaseBoardSelection: Color = DarkBoardTokens.boardSelection
@ShowkaseColor(name = "boardRegion", group = "Board")
internal val ShowkaseBoardRegion: Color = DarkBoardTokens.boardRegion

// Game accent slots (AccentTokens.kt, spec A1), dark values — the app-default look;
// the AccentSlots preview shows both modes.
@ShowkaseColor(name = "blueAccent", group = "Accents")
internal val ShowkaseAccentBlueAccent: Color = DarkAccentTokens.getValue(AccentSlot.Blue).accent
@ShowkaseColor(name = "blueOnAccent", group = "Accents")
internal val ShowkaseAccentBlueOnAccent: Color = DarkAccentTokens.getValue(AccentSlot.Blue).onAccent
@ShowkaseColor(name = "blueContainer", group = "Accents")
internal val ShowkaseAccentBlueContainer: Color = DarkAccentTokens.getValue(AccentSlot.Blue).container
@ShowkaseColor(name = "blueOnContainer", group = "Accents")
internal val ShowkaseAccentBlueOnContainer: Color = DarkAccentTokens.getValue(AccentSlot.Blue).onContainer
@ShowkaseColor(name = "orangeAccent", group = "Accents")
internal val ShowkaseAccentOrangeAccent: Color = DarkAccentTokens.getValue(AccentSlot.Orange).accent
@ShowkaseColor(name = "orangeOnAccent", group = "Accents")
internal val ShowkaseAccentOrangeOnAccent: Color = DarkAccentTokens.getValue(AccentSlot.Orange).onAccent
@ShowkaseColor(name = "orangeContainer", group = "Accents")
internal val ShowkaseAccentOrangeContainer: Color = DarkAccentTokens.getValue(AccentSlot.Orange).container
@ShowkaseColor(name = "orangeOnContainer", group = "Accents")
internal val ShowkaseAccentOrangeOnContainer: Color = DarkAccentTokens.getValue(AccentSlot.Orange).onContainer
@ShowkaseColor(name = "violetAccent", group = "Accents")
internal val ShowkaseAccentVioletAccent: Color = DarkAccentTokens.getValue(AccentSlot.Violet).accent
@ShowkaseColor(name = "violetOnAccent", group = "Accents")
internal val ShowkaseAccentVioletOnAccent: Color = DarkAccentTokens.getValue(AccentSlot.Violet).onAccent
@ShowkaseColor(name = "violetContainer", group = "Accents")
internal val ShowkaseAccentVioletContainer: Color = DarkAccentTokens.getValue(AccentSlot.Violet).container
@ShowkaseColor(name = "violetOnContainer", group = "Accents")
internal val ShowkaseAccentVioletOnContainer: Color = DarkAccentTokens.getValue(AccentSlot.Violet).onContainer
@ShowkaseColor(name = "tealAccent", group = "Accents")
internal val ShowkaseAccentTealAccent: Color = DarkAccentTokens.getValue(AccentSlot.Teal).accent
@ShowkaseColor(name = "tealOnAccent", group = "Accents")
internal val ShowkaseAccentTealOnAccent: Color = DarkAccentTokens.getValue(AccentSlot.Teal).onAccent
@ShowkaseColor(name = "tealContainer", group = "Accents")
internal val ShowkaseAccentTealContainer: Color = DarkAccentTokens.getValue(AccentSlot.Teal).container
@ShowkaseColor(name = "tealOnContainer", group = "Accents")
internal val ShowkaseAccentTealOnContainer: Color = DarkAccentTokens.getValue(AccentSlot.Teal).onContainer
@ShowkaseColor(name = "pinkAccent", group = "Accents")
internal val ShowkaseAccentPinkAccent: Color = DarkAccentTokens.getValue(AccentSlot.Pink).accent
@ShowkaseColor(name = "pinkOnAccent", group = "Accents")
internal val ShowkaseAccentPinkOnAccent: Color = DarkAccentTokens.getValue(AccentSlot.Pink).onAccent
@ShowkaseColor(name = "pinkContainer", group = "Accents")
internal val ShowkaseAccentPinkContainer: Color = DarkAccentTokens.getValue(AccentSlot.Pink).container
@ShowkaseColor(name = "pinkOnContainer", group = "Accents")
internal val ShowkaseAccentPinkOnContainer: Color = DarkAccentTokens.getValue(AccentSlot.Pink).onContainer
@ShowkaseColor(name = "neutralAccent", group = "Accents")
internal val ShowkaseAccentNeutralAccent: Color = DarkAccentTokens.getValue(AccentSlot.Neutral).accent
@ShowkaseColor(name = "neutralOnAccent", group = "Accents")
internal val ShowkaseAccentNeutralOnAccent: Color = DarkAccentTokens.getValue(AccentSlot.Neutral).onAccent
@ShowkaseColor(name = "neutralContainer", group = "Accents")
internal val ShowkaseAccentNeutralContainer: Color = DarkAccentTokens.getValue(AccentSlot.Neutral).container
@ShowkaseColor(name = "neutralOnContainer", group = "Accents")
internal val ShowkaseAccentNeutralOnContainer: Color = DarkAccentTokens.getValue(AccentSlot.Neutral).onContainer
