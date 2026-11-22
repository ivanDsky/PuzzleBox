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
    fun map(
        step: TangoStep,
        puzzle: TangoPuzzle,
        grid: List<TangoCell?>,
        playId: Int,
    ): Spotlight = when (step) {
        is TangoStep.Deduction ->
            spotlight(step, puzzle, deductionRoles(step), mapOf(step.idx to step.value), playId)
        is TangoStep.Reveal -> spotlight(
            step, puzzle, single(step.idx, SpotlightRole.TARGET), mapOf(step.idx to step.value), playId,
        )
        is TangoStep.Mistake -> spotlight(
            step, puzzle, single(step.idx, SpotlightRole.CULPRIT), mistakeValues(step, grid), playId,
        )
        TangoStep.Solved, TangoStep.Unavailable -> error("$step has no Spotlight")
    }

    /**
     * The Spotlight of a technique intro's first evidence frame: the whole evidence of [step] lit, no target, no callout,
     * as the intro's caption says what to look at. The squares are still empty, so a sign step's = pair is lit too.
     */
    fun look(step: TangoStep.Deduction, puzzle: TangoPuzzle, playId: Int): Spotlight =
        litEvidence(step.evidence, puzzle, playId)

    /**
     * The Spotlight of a technique intro's landing frames: the evidence of [step] without the squares in [targets],
     * which the demo lands one by one (a sign step lists its marker squares as both). Every role is evidence: a count
     * squeeze's what-if squares are not struck out, since a x means "different" in Tango.
     */
    fun evidence(step: TangoStep.Deduction, targets: Set<Int>, puzzle: TangoPuzzle, playId: Int): Spotlight =
        litEvidence(step.evidence - targets, puzzle, playId)

    private fun litEvidence(cells: Set<Int>, puzzle: TangoPuzzle, playId: Int): Spotlight = Spotlight(
        rows = puzzle.n,
        cols = puzzle.n,
        roles = cells.associateWith { SpotlightRole.EVIDENCE },
        callout = null,
        glyphs = TangoSpotlightGlyphs(puzzle.n, emptyMap()),
        playId = playId,
    )

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
     * The target wins a cell it shares with the evidence (a sign step lists both marker cells). The callout sits by the
     * evidence, else by the target.
     */
    private fun deductionRoles(step: TangoStep.Deduction): Pair<Map<Int, SpotlightRole>, Set<Int>> {
        val evidence = step.evidence - step.idx
        val roles = LinkedHashMap<Int, SpotlightRole>()
        evidence.forEach { roles[it] = SpotlightRole.EVIDENCE }
        roles[step.idx] = SpotlightRole.TARGET
        return roles to evidence.ifEmpty { setOf(step.idx) }
    }

    /** The wrong entry's own glyph is read from the press-time board; it is absent when the cell is off the board. */
    private fun mistakeValues(step: TangoStep.Mistake, grid: List<TangoCell?>): Map<Int, TangoCell> =
        grid.getOrNull(step.idx)?.let { mapOf(step.idx to it) } ?: emptyMap()
}
