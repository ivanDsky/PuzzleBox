package com.ivandsky.puzzlebox.games.sudokuui.hint

import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightCallout
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep

/**
 * Turns a [SudokuStep] into everything the Spotlight draws: roles, callout, glyph texts, and the ledger [Key].
 * [board] is the press-time board, equal to the snapshot the step was computed from.
 */
internal object SudokuSpotlightMapper {

    /** Identity of a step for the shown-step ledger: what it names, so a new wrong entry or a new strike is a new key. */
    sealed interface Key {
        data class Single(val cell: Int, val digit: Int) : Key
        data class Elimination(val technique: SudokuTechnique, val unit: SudokuUnit?, val struck: Map<Int, Int>) : Key
        data class Reveal(val cell: Int) : Key
        data class Mistake(val cell: Int, val digit: Int) : Key
    }

    /** The Spotlight of a keyed [step]; [SudokuStep.Solved] has none and throws. */
    fun map(step: SudokuStep, puzzle: SudokuPuzzle, board: SudokuBoard, playId: Int): Spotlight = when (step) {
        is SudokuStep.Single -> spotlight(
            step, puzzle, board, singleRoles(step, puzzle), mapOf(step.place.cell to step.place.digit.toString()), playId,
        )
        is SudokuStep.Elimination -> spotlight(
            step, puzzle, board, eliminationRoles(step),
            step.struck.mapValues { (_, mask) -> SudokuStepCopy.digitsOf(mask).joinToString(" ") }, playId,
        )
        is SudokuStep.Reveal -> spotlight(
            step, puzzle, board, mapOf(step.cell to SpotlightRole.TARGET), mapOf(step.cell to step.digit.toString()), playId,
        )
        is SudokuStep.Mistake -> spotlight(
            step, puzzle, board, mistakeRoles(step), mapOf(step.cell to board.digitAt(step.cell).toString()), playId,
        )
        SudokuStep.Solved -> error("a solved board has no Spotlight")
    }

    private fun spotlight(
        step: SudokuStep,
        puzzle: SudokuPuzzle,
        board: SudokuBoard,
        roles: Map<Int, SpotlightRole>,
        texts: Map<Int, String>,
        playId: Int,
    ): Spotlight {
        val anchor = ANCHOR_ORDER.firstNotNullOf { role ->
            roles.filterValues { it == role }.keys.takeIf { it.isNotEmpty() }
        }
        val size = puzzle.geometry.size
        return Spotlight(
            rows = size,
            cols = size,
            roles = roles,
            callout = SpotlightCallout(SudokuStepCopy.text(step, puzzle, board), anchor),
            glyphs = SudokuSpotlightGlyphs(texts),
            playId = playId,
        )
    }

    fun keyOf(step: SudokuStep): Key? = when (step) {
        is SudokuStep.Single -> Key.Single(step.place.cell, step.place.digit)
        is SudokuStep.Elimination -> Key.Elimination(step.technique, step.unit, step.struck)
        is SudokuStep.Reveal -> Key.Reveal(step.cell)
        is SudokuStep.Mistake -> Key.Mistake(step.cell, step.digit)
        SudokuStep.Solved -> null
    }

    /** The callout sits by the evidence, else by the cell the step is about, else by the struck cells. */
    private val ANCHOR_ORDER = listOf(SpotlightRole.EVIDENCE, SpotlightRole.TARGET, SpotlightRole.CULPRIT, SpotlightRole.EXCLUDED)

    /** One role per cell: later writes win, so the target or struck mark replaces evidence on the same cell. */
    private fun singleRoles(step: SudokuStep.Single, puzzle: SudokuPuzzle): Map<Int, SpotlightRole> {
        val roles = LinkedHashMap<Int, SpotlightRole>()
        step.blockers.forEach { roles[it] = SpotlightRole.EVIDENCE }
        litUnit(step)?.let { unit -> puzzle.geometry.cellsOf(unit).forEach { roles[it] = SpotlightRole.EVIDENCE } }
        roles[step.place.cell] = SpotlightRole.TARGET
        return roles
    }

    private fun eliminationRoles(step: SudokuStep.Elimination): Map<Int, SpotlightRole> {
        val roles = LinkedHashMap<Int, SpotlightRole>()
        step.evidence.forEach { roles[it] = SpotlightRole.EVIDENCE }
        step.struck.keys.forEach { roles[it] = SpotlightRole.EXCLUDED }
        return roles
    }

    /** The wrong cell is the culprit; the cell it clashes with, if any, is evidence. */
    private fun mistakeRoles(step: SudokuStep.Mistake): Map<Int, SpotlightRole> {
        val roles = LinkedHashMap<Int, SpotlightRole>()
        step.conflictsWith?.let { roles[it] = SpotlightRole.EVIDENCE }
        roles[step.cell] = SpotlightRole.CULPRIT
        return roles
    }

    /** The unit a hidden or last-cell single names ("this box", "col 4"); a naked single names none. */
    private fun litUnit(step: SudokuStep.Single): SudokuUnit? = step.lastCell ?: step.place.unit
}
