package com.ivandsky.puzzlebox.games.queensui.hint

import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightCallout
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason
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

    /** The Spotlight of [step], null for a solved board or when no sound step exists. */
    fun map(step: QueensStep, puzzle: QueensPuzzle, playId: Int): Spotlight? {
        val roles = LinkedHashMap<Int, SpotlightRole>()
        when (step) {
            is QueensStep.Mistake -> {
                step.other?.let { roles[it.idx] = SpotlightRole.EVIDENCE }
                roles[step.cell.idx] = SpotlightRole.CULPRIT
            }
            is QueensStep.Deduction -> when (val result = step.result) {
                is QueensStep.Deduction.Result.Cross -> {
                    step.evidence.forEach { roles[it.idx] = SpotlightRole.EVIDENCE }
                    result.cells.forEach { roles[it.idx] = SpotlightRole.EXCLUDED }
                }
                is QueensStep.Deduction.Result.LastCell -> {
                    step.evidence.forEach { roles[it.idx] = SpotlightRole.EVIDENCE }
                    roles[result.cell.idx] = SpotlightRole.TARGET
                }
            }
            is QueensStep.Reveal -> roles[step.cell.idx] = SpotlightRole.EXCLUDED
            QueensStep.Solved, QueensStep.Unavailable -> return null
        }
        val lit = roles.filterValues { it != SpotlightRole.EXCLUDED }.keys
        val anchor = lit.ifEmpty { roles.keys }
        return Spotlight(
            rows = puzzle.size,
            cols = puzzle.size,
            roles = roles,
            callout = SpotlightCallout(QueensStepCopy.text(step), anchor),
            glyphs = QueensSpotlightGlyphs(culpritIsCross = (step as? QueensStep.Mistake)?.kind == QueensStep.Mistake.Kind.WRONG_CROSS),
            playId = playId,
        )
    }

    fun keyOf(step: QueensStep): Key? = when (step) {
        is QueensStep.Mistake -> Key.Mistake(step.cell.idx, step.kind)
        is QueensStep.Deduction -> Key.Deduction(
            rungId = step.rung.id,
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
