package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueIds
import com.ivandsky.puzzlebox.games.sudokuui.hint.SudokuStepCopy
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique

/**
 * The Mini Sudoku technique list: one row per id in [SudokuTechniqueIds.all], the stepper's own order (easiest first).
 * The name is the list's own; the line is the sentence the hint shows for that technique ([SudokuStepCopy.technique]),
 * so a copy edit there shows in both (A1: no third copy set). Rows are keyed by [TechniqueId], the stored key, which
 * the engine alone spells.
 */
internal object SudokuTrainerCatalog {

    private val names: Map<TechniqueId, String> = mapOf(
        SudokuTechniqueIds.lastCell to "Last cell",
        id(SudokuTechnique.HIDDEN_SINGLE_BOX) to "Hidden single (box)",
        id(SudokuTechnique.HIDDEN_SINGLE_LINE) to "Hidden single (row or column)",
        id(SudokuTechnique.NAKED_SINGLE) to "Naked single",
        id(SudokuTechnique.POINTING) to "Pointing",
        id(SudokuTechnique.CLAIMING) to "Claiming",
        id(SudokuTechnique.NAKED_PAIR) to "Naked pair",
        id(SudokuTechnique.HIDDEN_PAIR) to "Hidden pair",
    )

    /** An id with no name here fails on first use, so a new technique cannot ship unnamed. */
    val techniques: List<TrainerTechnique> = SudokuTechniqueIds.all.map { id ->
        TrainerTechnique(id, names.getValue(id), SudokuStepCopy.technique(id))
    }

    /** The list's name for [id], the one the round's prompt uses. */
    fun name(id: TechniqueId): String = names.getValue(id)

    private fun id(technique: SudokuTechnique): TechniqueId = SudokuTechniqueIds.byTechnique.getValue(technique)
}
