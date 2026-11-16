package com.ivandsky.puzzlebox.game.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import kotlin.random.Random

/**
 * The stand-in for a game on the score and target seams (90), and the pattern a real game follows:
 * a legacy analysis that labels by its hardest rung, a [ScoredGrader] beside it that scores the
 * whole board through shared [LevelBands], and a completer that aims for a target with a
 * [ClosestCompletion]. A board is three cells; its score is their sum, and its legacy label is
 * always the Easy [LegacyRung]. The completer fills the empty cells with 1, then 2, and so on up to
 * [tries] candidates, so the level it can reach is bounded and a miss is easy to stage.
 */
internal class FakeScoredGrader(private val tries: Int = 9) : GameEditor<List<Int>>, DraftCompleter<List<Int>>, ScoredGrader<List<Int>> {
    override val gameId = GameId("scored")
    override val variants = listOf(VariantId("scored/n3"))
    override val draftFormatVersion = 1

    /** Every board the scored grader was asked about. */
    val graded = mutableListOf<List<Int>>()

    /** Clear to make the scored grader give no grade, as one that ran out of budget or stalled does. */
    var gradeable = true

    /** Set to make the legacy analysis say the ladder cannot finish the board. */
    var needsGuessing = false

    override fun newDraft(variant: VariantId): List<Int> = List(3) { 0 }
    override fun variantOf(draft: List<Int>) = variants.single()
    override fun encodeDraft(state: DraftState<List<Int>>) = ByteArray(state.draft.size) { state.draft[it].toByte() }
    override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<List<Int>>> = DecodeResult.Ok(DraftState(bytes.map { it.toInt() }))

    override fun analyze(draft: List<Int>, budget: Budget): Verdict = when {
        0 in draft -> Verdict.Incomplete(IncompleteReason("scored.empty", draft.count { it == 0 }))
        needsGuessing -> Verdict.Unique(Solution(draft), difficulty = null, needsGuessing = true)
        else -> Verdict.Unique(Solution(draft), Difficulty(LegacyRung))
    }

    override fun grade(draft: List<Int>, solution: Solution, budget: Budget): Difficulty? {
        graded += draft
        return if (gradeable) Bands.difficulty(score = draft.sum(), hardestRung = LegacyRung) else null
    }

    override fun readiness(draft: List<Int>): FinishReadiness = FinishReadiness.Ready

    override fun complete(draft: List<Int>, random: Random, budget: Budget, target: Difficulty.Level?): Completion<List<Int>> {
        val closest = ClosestCompletion<List<Int>>(target)
        for (fill in 1..tries) {
            if (budget.exhausted()) break
            val candidate = draft.map { if (it == 0) fill else it }
            if (closest.offer(candidate, Bands.levelOf(candidate.sum()))) break
        }
        return closest.completion()
    }

    /** A draft handle on [cells]. */
    fun draft(vararg cells: Int): EditorDraft<List<Int>> = EditorDraft(this, DraftState(cells.toList()))

    object LegacyRung : LadderRung {
        override val id = "scored.legacy"
        override val level = Difficulty.Level.Easy
        override val captionKey = "scored.legacy"
    }

    companion object {
        /** Sums under 6 are Easy, 6 to 11 Medium, 12 to 17 Hard, 18 and over Expert. */
        val Bands = LevelBands(medium = 6, hard = 12, expert = 18)
    }
}
