package com.ivandsky.puzzlebox.compete.domain.lineup

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.domain.ComputeSolvedPool
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.PickablePuzzle
import com.ivandsky.puzzlebox.domain.lineup.PickerQuery
import com.ivandsky.puzzlebox.domain.lineup.PickerResult
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The Past you add sheet's list: every puzzle the player has solved that a Past you duel can play (the pool of
 * [ComputeSolvedPool], friends' puzzles included) and has a first-solve record, newest first solve first, narrowed on
 * the device by the query. The player's own published puzzles are not in the pool, so they are not listed.
 */
class ObserveSolvedPuzzles internal constructor(
    private val registry: GameRegistry,
    private val pool: ComputeSolvedPool,
    private val completions: CompletionRepository,
    private val received: ReceivedPuzzleRepository,
) {
    /** Same query type as the friend sheet; [PickerResult.offline] is false and [PickerResult.moreDays] is false. [archivePages] is ignored: there is no archive to page. */
    @Suppress("UNUSED_PARAMETER")
    operator fun invoke(query: Flow<PickerQuery>, archivePages: Flow<Int>): Flow<PickerResult> =
        combine(query, rows()) { q, rows -> PickerResult(rows.filter { it.passes(q) }, offline = false) }

    private fun rows(): Flow<List<PickablePuzzle>> = completions.observeRecords().map { records ->
        val firstSolves = records.filter { it.isRecord }.associateBy { it.puzzleRef.gameId to it.puzzleRef.key }
        pool(registry.all.mapTo(HashSet()) { it.definition.id }, includeFriends = true)
            .mapNotNull { ref -> firstSolves[ref.gameId to ref.key]?.let { row(it) } }
            .sortedByDescending { it.firstSolveMs }.map { it.puzzle }
    }

    private class Row(val firstSolveMs: Long, val puzzle: PickablePuzzle)

    private suspend fun row(record: CompletionRecord): Row? {
        val ref = record.puzzleRef
        val at = record.firstCompletedAt?.toEpochMilli() ?: Long.MIN_VALUE
        val puzzle = when (val key = ref.key) {
            is PuzzleKey.Daily -> key.puzzleNumber?.let { number ->
                PickablePuzzle(
                    entry = ExactPuzzle(LegRef.Daily(ref.gameId, key.date, number), null, "${registry.displayName(ref.gameId)} #$number", null, PuzzleSource.PastDay),
                    isNew = false,
                    played = true,
                    meta = DAY_FORMAT.format(key.date),
                    code = null,
                )
            }
            is PuzzleKey.Custom -> ShareCode.parse(key.puzzleId)?.let { code ->
                received.get(code)?.let {
                    PickablePuzzle(
                        entry = ExactPuzzle(LegRef.Custom(ref.gameId, code), it.authorUid, it.title, it.authorName, PuzzleSource.Friend),
                        isNew = false,
                        played = true,
                        meta = metaOf(it.variant.value),
                        code = code,
                        level = it.difficulty,
                    )
                }
            }
        }
        return puzzle?.let { Row(at, it) }
    }
}
