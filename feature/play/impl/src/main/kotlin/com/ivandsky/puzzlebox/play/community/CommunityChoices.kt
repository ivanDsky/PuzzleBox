package com.ivandsky.puzzlebox.play.community

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId

/** One game chip: a game that has an editor, so a puzzle of it can be made and shared. */
internal data class GameChoice(val id: GameId, val name: String, val brand: GameBrand)

/**
 * The difficulty chips. Hard also asks for Expert: the analyser's ladder has four levels and the
 * list shows three chips. A chip narrows to [levels]; with none picked the list is every puzzle,
 * labelled or not.
 */
internal enum class DifficultyChoice(val label: String, val levels: Set<Difficulty.Level>) {
    Easy("Easy", setOf(Difficulty.Level.Easy)),
    Medium("Medium", setOf(Difficulty.Level.Medium)),
    Hard("Hard", setOf(Difficulty.Level.Hard, Difficulty.Level.Expert)),
}
