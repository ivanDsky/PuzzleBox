package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.model.PuzzleRefKeys
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.domain.ComputeSolvedPool
import com.ivandsky.puzzlebox.game.CompletionRecord
import kotlin.random.Random
import kotlinx.coroutines.flow.first

/**
 * Turns a lineup into a self duel's puzzles, in the lineup's order, each with the first-solve record that becomes past
 * you's result. Every exact puzzle is reserved first, so no "Any" row draws it; then rows resolve one by one, each
 * "Any" drawing at random from the [ComputeSolvedPool] list minus the reserved and the already used puzzles. The
 * switch widens "Any" rows only: an exact row is the player's choice. The first row that can't be filled ends the draw.
 */
internal class ResolveSelfLineup(
    private val pool: ComputeSolvedPool,
    private val completions: CompletionRepository,
    private val received: ReceivedPuzzleRepository,
    private val random: Random,
) {
    suspend operator fun invoke(lineup: List<LineupEntry>, includeFriends: Boolean): SelfDraw {
        val records = completions.observeRecords().first().filter { it.isRecord }.associateBy { wire(it) }
        val reserved = lineup.filterIsInstance<ExactPuzzle>().mapTo(HashSet()) { it.ref.wire }
        val solved = pool(lineup.mapTo(HashSet()) { it.gameId }, includeFriends).mapNotNull { records[PuzzleRefKeys.encode(it)] }
        val used = HashSet<String>()
        val picked = ArrayList<Pair<MatchLeg, CompletionRecord>>(lineup.size)
        for ((index, entry) in lineup.withIndex()) {
            val record = when (entry) {
                is ExactPuzzle -> records[entry.ref.wire]?.takeIf { holds(entry.ref) }
                    ?: return SelfDraw.Unavailable(index + 1)
                is AnyGame -> solved
                    .filter { it.puzzleRef.gameId == entry.gameId && wire(it) !in reserved && wire(it) !in used }
                    .randomOrNull(random)
                    ?: return SelfDraw.Undrawable(index + 1, entry.gameId)
            }
            used += wire(record)
            picked += received.legOf(record.puzzleRef) to record
        }
        return SelfDraw.Drawn(picked)
    }

    private fun wire(record: CompletionRecord) = PuzzleRefKeys.encode(record.puzzleRef)

    private suspend fun holds(ref: LegRef): Boolean =
        ref !is LegRef.Custom || received.get(ref.code)?.unpublished == false
}
