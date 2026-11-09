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

    /** The Spotlight of [step], null for a solved board or when no sound step exists. */
    fun map(step: TangoStep, puzzle: TangoPuzzle, grid: List<TangoCell?>, playId: Int): Spotlight? {
        val (roles, anchor) = when (step) {
            is TangoStep.Deduction -> deductionRoles(step)
            is TangoStep.Reveal -> single(step.idx, SpotlightRole.TARGET)
            is TangoStep.Mistake -> single(step.idx, SpotlightRole.CULPRIT)
            TangoStep.Solved, TangoStep.Unavailable -> return null
        }
        return Spotlight(
            rows = puzzle.n,
            cols = puzzle.n,
            roles = roles,
            callout = SpotlightCallout(TangoStepCopy.text(step, puzzle), anchor),
            glyphs = TangoSpotlightGlyphs(puzzle.n, values(step, grid)),
            playId = playId,
        )
    }

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

    private fun values(step: TangoStep, grid: List<TangoCell?>): Map<Int, TangoCell> = when (step) {
        is TangoStep.Deduction -> mapOf(step.idx to step.value)
        is TangoStep.Reveal -> mapOf(step.idx to step.value)
        is TangoStep.Mistake -> grid.getOrNull(step.idx)?.let { mapOf(step.idx to it) } ?: emptyMap()
        TangoStep.Solved, TangoStep.Unavailable -> emptyMap()
    }
}
