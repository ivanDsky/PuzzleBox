package com.ivandsky.puzzlebox.designsystem.spotlight

import androidx.compose.runtime.Immutable

/** The whole drawable state for one hint step, glyphs included. */
@Immutable
data class Spotlight(
    val rows: Int,
    val cols: Int,
    /** Row-major cell index → role; unlisted cells dim. */
    val roles: Map<Int, SpotlightRole>,
    val callout: SpotlightCallout?,
    /** Built by the game's mapper from step + board. */
    val glyphs: SpotlightGlyphs,
    /** Bump to run the sequence again on a free re-press. */
    val playId: Int,
)
