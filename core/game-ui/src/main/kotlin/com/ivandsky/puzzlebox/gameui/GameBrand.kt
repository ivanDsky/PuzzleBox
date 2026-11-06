package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.graphics.vector.ImageVector
import com.ivandsky.puzzlebox.designsystem.AccentSlot

/**
 * A game's identity outside its board (spec A2): the design-system hue slot every
 * surface tints the game with, and [glyph], the game's 24-grid icon (a tiny picture of
 * its board, identity §2.5; build it with [gameGlyph], draw it with [GameGlyph]). Each
 * game's UI module registers it on its [GamePlayUi]; [GamePlayRegistry.brandOf] is the
 * one lookup, and an unknown id reads [Neutral].
 */
data class GameBrand(val accent: AccentSlot, val glyph: ImageVector) {
    companion object {
        /** No hue, and a plain rounded square for a glyph. */
        val Neutral = GameBrand(
            AccentSlot.Neutral,
            gameGlyph(
                name = "Neutral",
                stroke = "M8 4H16A4 4 0 0 1 20 8V16A4 4 0 0 1 16 20H8A4 4 0 0 1 4 16V8A4 4 0 0 1 8 4Z",
            ),
        )
    }
}
