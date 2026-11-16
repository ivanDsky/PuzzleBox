package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Completion
import com.ivandsky.puzzlebox.game.editor.DraftCompleter
import com.ivandsky.puzzlebox.game.editor.FinishReadiness
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import kotlin.random.Random

/**
 * Zip's Finish for me (spec E1): one random finished board that keeps everything the author
 * placed. [ZipPathExtension] walks the drawn path on over every cell (or walks a path-less
 * board through its numbers), then [ZipPinnedRepair] adds numbers and walls, mixed as on a
 * daily board, until that path is the one answer. The board comes back drawn, numbered in path
 * order, and is one [ZipDraftAnalyzer] calls Unique.
 */
object ZipCompleter : DraftCompleter<ZipDraft> {
    /** [FinishReadiness.NeedsMore] reason; IncompleteReason.count = cells the path still has to grow by. */
    const val PATH: String = "zip.finish.path"

    /** A path-less board is ready with its two end numbers: a shared board arrives that way. */
    private const val END_NUMBERS = 2

    /**
     * Cells the author's path must cover before Finish for me is offered, the same at every
     * size: a start and a first step. Measured by `ZipCompleterMeasurement` on random walks
     * drawn on a blank board, sizes 5 to 8: every 2-cell walk readiness lets through finished,
     * and each cell more leaves more walks with no full path at all (at 3 cells, 52 of 60 on
     * 6×6 finished). A longer minimum would lower the rate, not raise it.
     */
    const val MIN_PATH: Int = 2

    /** Blocked on [clash]; NeedsMore under [MIN_PATH] unless a path-less board has both end numbers. */
    override fun readiness(draft: ZipDraft): FinishReadiness {
        val clashing = clash(draft)
        if (clashing.isNotEmpty()) return FinishReadiness.Blocked(clashing)
        if (draft.path.isEmpty() && draft.waypoints.size >= END_NUMBERS) return FinishReadiness.Ready
        val missing = MIN_PATH - draft.path.size
        return if (missing > 0) FinishReadiness.NeedsMore(IncompleteReason(PATH, missing)) else FinishReadiness.Ready
    }

    override fun complete(draft: ZipDraft, random: Random, budget: Budget): Completion<ZipDraft> {
        if (clash(draft).isNotEmpty()) return Completion.GaveUp
        val path = ZipPathExtension(draft, budget).extend(random) ?: return Completion.GaveUp
        val board = ZipPinnedRepair(draft, path, random, budget).unique() ?: return Completion.GaveUp
        return Completion.Done(board)
    }

    /**
     * The cells [ZipClashLocator] blames when the pieces already rule out every full path, else
     * none. The end number of a path that is still growing moves with it, so only its start is a
     * fixed end. Each end the pieces do not fix may be one walled dead end, where the path will
     * stop; more dead ends than that clash. A lone start on the minority colour of an odd board
     * clashes by itself: no full path starts there.
     */
    private fun clash(draft: ZipDraft): Set<Int> {
        val growing = draft.path.isNotEmpty() && draft.path.size < draft.n * draft.n
        val ends = if (growing) draft.path.take(1) else draft.waypoints
        val blamed = ZipClashLocator.conflicts(ZipBoard(draft.n, draft.walls, ends.map(::Cell)))
        val freeEnds = END_NUMBERS - setOfNotNull(ends.firstOrNull(), ends.lastOrNull()).size
        if (blamed.size > freeEnds) return blamed
        val start = ends.singleOrNull() ?: return emptySet()
        val minority = draft.n % 2 == 1 && (start / draft.n + start % draft.n) % 2 == 1
        return if (minority) setOf(start) else emptySet()
    }
}
