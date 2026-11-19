package com.ivandsky.puzzlebox.compete.domain.lineup

import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.PickablePuzzle
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** The player's own published puzzles that are still up, from the local listing. */
internal class OwnSource(
    private val published: PublishedPuzzleRepository,
    private val identity: IdentityService,
) {
    fun observe(played: Flow<Set<String>>): Flow<SourceRows> = combine(published.observeAll(), played) { rows, solved ->
        val me = identity.current.value?.uid
        SourceRows(
            rows.filterNot { it.unpublished }.map {
                val ref = LegRef.Custom(it.gameId, it.code)
                SourceRows.Dated(
                    atMs = it.createdAt.toEpochMilli(),
                    puzzle = PickablePuzzle(
                        entry = ExactPuzzle(ref, me, it.title, author = null, PuzzleSource.Mine),
                        isNew = false,
                        played = ref.wire in solved,
                        meta = metaOf(it.variant.value),
                        code = it.code,
                    ),
                )
            },
        )
    }
}
