package com.ivandsky.puzzlebox.train.practice

import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique
import kotlin.math.max

/**
 * One run through a chapter's [boards], tallied for its result page. Right trained turns and the streak come from the
 * board's verdicts ([verdict]); Show me taps, out-of-order slips, undone moves and finished boards come from the statuses the band
 * shows ([see]). In memory for the run only, and only touched on the main thread. [wasMastered] is the technique's mark
 * when the run began, set once the first board opens.
 */
internal class ChapterRun(private val boards: Int) {
    private val rights = IntArray(boards)
    private val shown = IntArray(boards)
    private val slips = IntArray(boards)
    private val undone = IntArray(boards)
    private val done = BooleanArray(boards)
    private var streak = 0
    private var best = 0

    var wasMastered: Boolean? = null

    /** A trained turn on [board] (from 0): a right one extends the streak, a wrong one or a Show me ends it. */
    fun verdict(board: Int, verdict: TrainerVerdict) {
        if (verdict != TrainerVerdict.Right) {
            streak = 0
            return
        }
        rights[board] += 1
        streak += 1
        best = max(best, streak)
    }

    /** The band on [board] (from 0) showed [status]. */
    fun see(board: Int, status: PracticeStatus) {
        when (status) {
            is PracticeStatus.Easier -> slips[board] += 1
            is PracticeStatus.Wrong -> undone[board] += 1
            is PracticeStatus.Shown -> shown[board] += 1
            is PracticeStatus.Done -> done[board] = true
            else -> Unit
        }
    }

    /** The tally of the chapter of the technique at [index] in [catalog], against the stored [marks] once its last verdict is recorded. */
    fun result(catalog: List<TrainerTechnique>, index: Int, marks: Map<TechniqueId, TechniqueProgress>): ChapterResult {
        fun mastered(id: TechniqueId) = marks[id]?.mark == TechniqueProgress.Mark.Mastered
        return ChapterResult(
            technique = catalog[index],
            next = catalog.getOrNull(index + 1),
            outcome = when {
                !mastered(catalog[index].id) -> ChapterOutcome.NotMastered
                wasMastered == true -> ChapterOutcome.StillMastered
                else -> ChapterOutcome.NewlyMastered
            },
            boards = boards,
            boardsDone = done.count { it },
            rights = rights.sum(),
            shown = shown.sum(),
            slips = slips.sum(),
            undone = undone.sum(),
            best = best,
            rows = List(boards) { board ->
                val kind = when {
                    shown[board] > 0 -> BoardRowKind.Shown
                    undone[board] > 0 -> BoardRowKind.Undone
                    slips[board] > 0 -> BoardRowKind.Slip
                    else -> BoardRowKind.Clean
                }
                BoardRow(board + 1, kind, rights[board], undone[board])
            },
            allMastered = catalog.all { mastered(it.id) },
            techniques = catalog.size,
        )
    }
}
