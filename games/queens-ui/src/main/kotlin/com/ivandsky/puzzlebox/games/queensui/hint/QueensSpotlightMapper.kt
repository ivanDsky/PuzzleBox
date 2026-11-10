package com.ivandsky.puzzlebox.games.queensui.hint

import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightCallout
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.HintReason
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep

/** Turns a [QueensStep] into everything the Spotlight draws (roles, callout, glyphs) and its ledger [Key]. */
internal object QueensSpotlightMapper {

    /** Identity of a step for the shown-step ledger: what it names, so the same step found again is free. */
    sealed interface Key {
        data class Mistake(val cell: Int, val kind: QueensStep.Mistake.Kind) : Key
        data class Deduction(val rungId: String, val unitKind: HintReason.Kind, val unit: Int, val result: Result) : Key
        data class Reveal(val cell: Int) : Key

        /** What a deduction concludes; a crossed set is a [Set], so its order never matters. */
        sealed interface Result {
            data class Cross(val cells: Set<Int>) : Result
            data class LastCell(val cell: Int) : Result
        }
    }

    /** The Spotlight of a keyed [step]; [QueensStep.Solved] and [QueensStep.Unavailable] have none and throw. */
    fun map(step: QueensStep, puzzle: QueensPuzzle, playId: Int): Spotlight = when (step) {
        is QueensStep.Mistake -> spotlight(
            step, puzzle, playId, mistakeRoles(step),
            culpritIsCross = step.kind == QueensStep.Mistake.Kind.WRONG_CROSS,
        )
        is QueensStep.Deduction -> spotlight(step, puzzle, playId, deductionRoles(step), culpritIsCross = false)
        is QueensStep.Reveal -> spotlight(
            step, puzzle, playId, mapOf(step.cell.idx to SpotlightRole.EXCLUDED), culpritIsCross = false,
        )
        QueensStep.Solved, QueensStep.Unavailable -> error("$step has no Spotlight")
    }

    private fun spotlight(
        step: QueensStep,
        puzzle: QueensPuzzle,
        playId: Int,
        roles: Map<Int, SpotlightRole>,
        culpritIsCross: Boolean,
    ): Spotlight {
        val lit = roles.filterValues { it != SpotlightRole.EXCLUDED }.keys
        val anchor = lit.ifEmpty { roles.keys }
        return Spotlight(
            rows = puzzle.size,
            cols = puzzle.size,
            roles = roles,
            callout = SpotlightCallout(QueensStepCopy.text(step), anchor),
            glyphs = QueensSpotlightGlyphs(culpritIsCross = culpritIsCross),
            playId = playId,
        )
    }

    private fun mistakeRoles(step: QueensStep.Mistake): Map<Int, SpotlightRole> {
        val roles = LinkedHashMap<Int, SpotlightRole>()
        step.other?.let { roles[it.idx] = SpotlightRole.EVIDENCE }
        roles[step.cell.idx] = SpotlightRole.CULPRIT
        return roles
    }

    private fun deductionRoles(step: QueensStep.Deduction): Map<Int, SpotlightRole> {
        val roles = LinkedHashMap<Int, SpotlightRole>()
        step.evidence.forEach { roles[it.idx] = SpotlightRole.EVIDENCE }
        when (val result = step.result) {
            is QueensStep.Deduction.Result.Cross -> result.cells.forEach { roles[it.idx] = SpotlightRole.EXCLUDED }
            is QueensStep.Deduction.Result.LastCell -> roles[result.cell.idx] = SpotlightRole.TARGET
        }
        return roles
    }

    fun keyOf(step: QueensStep): Key? = when (step) {
        is QueensStep.Mistake -> Key.Mistake(step.cell.idx, step.kind)
        is QueensStep.Deduction -> Key.Deduction(
            rungId = step.unit.rung.id,
            unitKind = step.unit.kind,
            unit = step.unit.unit,
            result = when (val result = step.result) {
                is QueensStep.Deduction.Result.Cross -> Key.Result.Cross(result.cells.mapTo(HashSet()) { it.idx })
                is QueensStep.Deduction.Result.LastCell -> Key.Result.LastCell(result.cell.idx)
            },
        )
        is QueensStep.Reveal -> Key.Reveal(step.cell.idx)
        QueensStep.Solved, QueensStep.Unavailable -> null
    }
}
