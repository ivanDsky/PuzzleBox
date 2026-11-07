package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.model.PlaySlot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * From friends (spec A19): every received puzzle with its [FriendStatus], joined from the
 * received rows, the saved plays ([PlaySlot.Custom] under the code) and each puzzle's own
 * completion rows. Whether a board still opens here is decided once per received-rows change,
 * not on every save.
 */
class ObserveFriendPuzzles(
    private val received: ReceivedPuzzleRepository,
    private val saved: SavedPlayRepository,
    private val completions: CompletionRepository,
    private val registry: GameRegistry,
) {
    /** Local data only (failure-12). A19 order: every non-Solved row first, newest openedAt first; then Solved, newest openedAt first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<FriendPuzzleRow>> =
        received.observeAll()
            .map { puzzles -> puzzles.sortedByDescending { it.openedAt }.map { Received(it, outdated = needsUpdate(it)) } }
            .flatMapLatest { puzzles ->
                if (puzzles.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(saved.observeInProgress(), solveTimes(puzzles)) { slots, times ->
                        puzzles.mapIndexed { index, it -> it.row(statusOf(it, times[index], slots)) }
                            .sortedBy { it.status is FriendStatus.Solved }
                    }
                }
            }
            .distinctUntilChanged()

    /** Each puzzle's first-solve time (its record row), null while unsolved; in [puzzles] order. */
    private fun solveTimes(puzzles: List<Received>): Flow<List<Long?>> =
        combine(
            puzzles.map { (puzzle) ->
                completions.observeCustom(puzzle.gameId, puzzle.code.value)
                    .map { rows -> rows.firstOrNull { it.isRecord }?.time }
            },
        ) { it.toList() }

    private fun statusOf(received: Received, solvedMs: Long?, slots: Map<PlaySlot, Long>): FriendStatus {
        val puzzle = received.puzzle
        val elapsed = slots[PlaySlot.Custom(puzzle.gameId, puzzle.code.value)]
        return when {
            received.outdated -> FriendStatus.NeedsUpdate
            solvedMs != null -> FriendStatus.Solved(solvedMs)
            puzzle.unpublished -> FriendStatus.TakenDown
            elapsed != null -> FriendStatus.InProgress(elapsed)
            else -> FriendStatus.New
        }
    }

    private fun needsUpdate(puzzle: ReceivedPuzzle): Boolean {
        val plugin = registry[puzzle.gameId] ?: return true
        val board = Puzzle(puzzle.gameId, puzzle.variant, puzzle.sharedFormatVersion, puzzle.puzzleBytes)
        return (plugin.initialSession(board) as? DecodeResult.Err)?.error is DecodeError.UnsupportedVersion
    }

    private data class Received(val puzzle: ReceivedPuzzle, val outdated: Boolean) {
        fun row(status: FriendStatus) = FriendPuzzleRow(puzzle.code, puzzle.gameId, puzzle.title, puzzle.authorName, status)
    }
}
