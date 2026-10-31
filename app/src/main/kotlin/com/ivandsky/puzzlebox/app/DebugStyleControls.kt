package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.designsystem.PaletteVariant
import com.ivandsky.puzzlebox.designsystem.TypographyVariant

/**
 * The debug style switcher's state + write callbacks (ticket 08), resolved at the
 * composition root and threaded to the catalog's switcher section. Release builds pass
 * null everywhere this type appears: the theme resolves the shipped default variants
 * and no switcher entry point exists.
 */
data class DebugStyleControls(
    val typography: TypographyVariant,
    val palette: PaletteVariant,
    val onTypography: (TypographyVariant) -> Unit,
    val onPalette: (PaletteVariant) -> Unit,
)
