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

    /** The Spotlight of [step], null for a solved board. */
    fun map(step: SudokuStep, puzzle: SudokuPuzzle, board: SudokuBoard, playId: Int): Spotlight? {
        if (step == SudokuStep.Solved) return null
        val roles = roles(step, puzzle)
        val anchor = ANCHOR_ORDER.firstNotNullOf { role ->
            roles.filterValues { it == role }.keys.takeIf { it.isNotEmpty() }
        }
        val size = puzzle.geometry.size
        return Spotlight(
            rows = size,
            cols = size,
            roles = roles,
            callout = SpotlightCallout(SudokuStepCopy.text(step, puzzle, board), anchor),
            glyphs = SudokuSpotlightGlyphs(texts(step, board)),
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

    /** One role per cell: later writes win, so the target, culprit or struck mark replaces evidence on the same cell. */
    private fun roles(step: SudokuStep, puzzle: SudokuPuzzle): Map<Int, SpotlightRole> {
        val roles = LinkedHashMap<Int, SpotlightRole>()
        when (step) {
            is SudokuStep.Single -> {
                step.blockers.forEach { roles[it] = SpotlightRole.EVIDENCE }
                litUnit(step)?.let { unit -> puzzle.geometry.cellsOf(unit).forEach { roles[it] = SpotlightRole.EVIDENCE } }
                roles[step.place.cell] = SpotlightRole.TARGET
            }
            is SudokuStep.Elimination -> {
                step.evidence.forEach { roles[it] = SpotlightRole.EVIDENCE }
                step.struck.keys.forEach { roles[it] = SpotlightRole.EXCLUDED }
            }
            is SudokuStep.Reveal -> roles[step.cell] = SpotlightRole.TARGET
            is SudokuStep.Mistake -> {
                step.conflictsWith?.let { roles[it] = SpotlightRole.EVIDENCE }
                roles[step.cell] = SpotlightRole.CULPRIT
            }
            SudokuStep.Solved -> Unit
        }
        return roles
    }

    /** The unit a hidden or last-cell single names ("this box", "col 4"); a naked single names none. */
    private fun litUnit(step: SudokuStep.Single): SudokuUnit? = step.lastCell ?: step.place.unit

    private fun texts(step: SudokuStep, board: SudokuBoard): Map<Int, String> = when (step) {
        is SudokuStep.Single -> mapOf(step.place.cell to step.place.digit.toString())
        is SudokuStep.Elimination ->
            step.struck.mapValues { (_, mask) -> SudokuStepCopy.digitsOf(mask).joinToString(" ") }
        is SudokuStep.Reveal -> mapOf(step.cell to step.digit.toString())
        is SudokuStep.Mistake -> mapOf(step.cell to board.digitAt(step.cell).toString())
        SudokuStep.Solved -> emptyMap()
    }
}
