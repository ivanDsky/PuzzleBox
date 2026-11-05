package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.designsystem.AccentSlot

/**
 * A game's identity outside its board (spec A2): the design-system hue slot every
 * surface tints the game with. Each game's UI module registers it on its [GamePlayUi];
 * [GamePlayRegistry.brandOf] is the one lookup, and an unknown id reads [Neutral].
 */
data class GameBrand(val accent: AccentSlot) {
    companion object {
        val Neutral = GameBrand(AccentSlot.Neutral)
    }
}
