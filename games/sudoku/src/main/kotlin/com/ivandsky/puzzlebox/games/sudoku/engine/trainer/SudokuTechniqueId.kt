package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep

/**
 * The Mini Sudoku Trainer technique ids (spec data-3: wire and index keys, so they are stated literally
 * and never read from enum names). One mapper because Last cell is no [SudokuTechnique]: it is a
 * single the stepper found as the only empty cell of a unit.
 */
object SudokuTechniqueIds {

    /** The eight ids in stepper order: last cell, the three singles, then the four eliminations. */
    val all: List<TechniqueId> = listOf(
        TechniqueId("last-cell"),
        TechniqueId("hidden-single-box"),
        TechniqueId("hidden-single-line"),
        TechniqueId("naked-single"),
        TechniqueId("pointing"),
        TechniqueId("claiming"),
        TechniqueId("naked-pair"),
        TechniqueId("hidden-pair"),
    )

    /** The id of a single the stepper found as the only empty cell of a unit; no [SudokuTechnique] carries it. */
    val lastCell: TechniqueId = all[0]

    /** The id of each technique the stepper emits (the triples have none). */
    val byTechnique: Map<SudokuTechnique, TechniqueId> = mapOf(
        SudokuTechnique.HIDDEN_SINGLE_BOX to all[1],
        SudokuTechnique.HIDDEN_SINGLE_LINE to all[2],
        SudokuTechnique.NAKED_SINGLE to all[3],
        SudokuTechnique.POINTING to all[4],
        SudokuTechnique.CLAIMING to all[5],
        SudokuTechnique.NAKED_PAIR to all[6],
        SudokuTechnique.HIDDEN_PAIR to all[7],
    )

    /** The id of the technique [step] shows; null for a triple, a reveal, a mistake and the walk's end. */
    fun of(step: SudokuStep): TechniqueId? = when (step) {
        is SudokuStep.Single -> if (step.lastCell != null) lastCell else byTechnique[step.place.technique]
        is SudokuStep.Elimination -> byTechnique[step.technique]
        is SudokuStep.Reveal, is SudokuStep.Mistake, SudokuStep.Solved -> null
    }
}
