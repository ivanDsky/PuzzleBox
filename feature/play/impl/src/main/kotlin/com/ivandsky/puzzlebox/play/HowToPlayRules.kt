package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.model.GameId

/**
 * The per-game rules lookup behind How to play, injectable so a route test can give a stand-in game a short copy.
 */
internal class HowToPlayRules(
    private val copyOf: (GameId) -> RulesSheetCopy? = ::rulesSheetCopy,
) {
    /** The rules sheet's copy for [game]; null when the game has no rules to show, so How to play opens the tutorial. */
    fun copyFor(game: GameId): RulesSheetCopy? = copyOf(game)
}
