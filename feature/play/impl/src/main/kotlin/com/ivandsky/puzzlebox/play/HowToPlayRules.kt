package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.model.GameId

/**
 * The RulesSheet flag as feature/play's DI reads it once per graph (the [PlayFeel] shape): [copyFor] is null while it
 * is off, so How to play opens the tutorial as today. [copyOf] is the per-game lookup, injectable so a route test can
 * give a stand-in game a short copy.
 */
internal class HowToPlayRules(
    val enabled: Boolean,
    private val copyOf: (GameId) -> RulesSheetCopy? = ::rulesSheetCopy,
) {
    /** The rules sheet's copy for [game]; null when the flag is off or the game has no rules to show. */
    fun copyFor(game: GameId): RulesSheetCopy? = if (enabled) copyOf(game) else null
}
