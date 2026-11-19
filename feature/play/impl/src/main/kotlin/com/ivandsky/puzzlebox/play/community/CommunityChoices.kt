package com.ivandsky.puzzlebox.play.community

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId

/** One game in the filter: a game that has an editor, so a puzzle of it can be made and shared. [brand] is the glyph its sheet chip wears. */
internal data class GameChoice(val id: GameId, val name: String, val brand: GameBrand = GameBrand.Neutral)

/**
 * The difficulty menu items. Hard also asks for Expert: the analyser's ladder has four levels and the
 * menu shows three. An item narrows to [levels]; with none picked the list is every puzzle,
 * labelled or not.
 */
internal enum class DifficultyChoice(val label: String, val levels: Set<Difficulty.Level>) {
    Easy("Easy", setOf(Difficulty.Level.Easy)),
    Medium("Medium", setOf(Difficulty.Level.Medium)),
    Hard("Hard", setOf(Difficulty.Level.Hard, Difficulty.Level.Expert)),
    ;

    /** The level whose mark the item shows: the lowest it covers, so Hard wears the Hard mark. */
    val mark: Difficulty.Level get() = levels.min()
}
