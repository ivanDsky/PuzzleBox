package com.ivandsky.puzzlebox.games.tangoui.hint

import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightCallout
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung

/**
 * Turns a [TangoStep] into everything the Spotlight draws: roles, callout, glyphs, and the ledger [Key].
 * [grid] is the press-time board, equal to the snapshot the step was computed from.
 */
internal object TangoSpotlightMapper {

    /** Identity of a step for the shown-step ledger: what it names, so the same deduction found again is free. */
    sealed interface Key {
        data class Deduction(val rung: TangoStepRung, val idx: Int, val value: TangoCell, val evidence: Set<Int>) : Key
        data class Reveal(val idx: Int) : Key
        data class Mistake(val idx: Int) : Key
    }

    /** The Spotlight of a keyed [step]; [TangoStep.Solved] and [TangoStep.Unavailable] have none and throw. */
    fun map(step: TangoStep, puzzle: TangoPuzzle, grid: List<TangoCell?>, playId: Int): Spotlight = when (step) {
        is TangoStep.Deduction ->
            spotlight(step, puzzle, deductionRoles(step), mapOf(step.idx to step.value), playId)
        is TangoStep.Reveal ->
            spotlight(step, puzzle, single(step.idx, SpotlightRole.TARGET), mapOf(step.idx to step.value), playId)
        is TangoStep.Mistake ->
            spotlight(step, puzzle, single(step.idx, SpotlightRole.CULPRIT), mistakeValues(step, grid), playId)
        TangoStep.Solved, TangoStep.Unavailable -> error("$step has no Spotlight")
    }

    private fun spotlight(
        step: TangoStep,
        puzzle: TangoPuzzle,
        shape: Pair<Map<Int, SpotlightRole>, Set<Int>>,
        values: Map<Int, TangoCell>,
        playId: Int,
    ): Spotlight = Spotlight(
        rows = puzzle.n,
        cols = puzzle.n,
        roles = shape.first,
        callout = SpotlightCallout(TangoStepCopy.text(step, puzzle), shape.second),
        glyphs = TangoSpotlightGlyphs(puzzle.n, values),
        playId = playId,
    )

    fun keyOf(step: TangoStep): Key? = when (step) {
        is TangoStep.Deduction -> Key.Deduction(step.rung, step.idx, step.value, step.evidence)
        is TangoStep.Reveal -> Key.Reveal(step.idx)
        is TangoStep.Mistake -> Key.Mistake(step.idx)
        TangoStep.Solved, TangoStep.Unavailable -> null
    }

    private fun single(idx: Int, role: SpotlightRole): Pair<Map<Int, SpotlightRole>, Set<Int>> =
        mapOf(idx to role) to setOf(idx)

    /**
     * The target wins a cell it shares with the evidence (a sign step lists both marker cells). The count squeeze draws
     * its what-if cells struck out, never as proof. The callout sits by the evidence, else by the target.
     */
    private fun deductionRoles(step: TangoStep.Deduction): Pair<Map<Int, SpotlightRole>, Set<Int>> {
        val evidence = step.evidence - step.idx
        val role = if (step.rung == TangoStepRung.COUNT_SQUEEZE) SpotlightRole.EXCLUDED else SpotlightRole.EVIDENCE
        val roles = LinkedHashMap<Int, SpotlightRole>()
        evidence.forEach { roles[it] = role }
        roles[step.idx] = SpotlightRole.TARGET
        return roles to evidence.ifEmpty { setOf(step.idx) }
    }

    /** The wrong entry's own glyph is read from the press-time board; it is absent when the cell is off the board. */
    private fun mistakeValues(step: TangoStep.Mistake, grid: List<TangoCell?>): Map<Int, TangoCell> =
        grid.getOrNull(step.idx)?.let { mapOf(step.idx to it) } ?: emptyMap()
}
