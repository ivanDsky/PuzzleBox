package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The puzzles friends sent that this device holds, minus withdrawn ones and the player's own. A friend's puzzle is
 * New until the player has a solve of it: the device only keeps the puzzles it has opened, so "not opened" is
 * "never solved" here.
 */
internal class FriendSource(
    private val received: ReceivedPuzzleRepository,
    private val identity: IdentityService,
) {
    fun observe(played: Flow<Set<String>>): Flow<SourceRows> = combine(received.observeAll(), played) { rows, solved ->
        val me = identity.current.value?.uid
        SourceRows(
            rows.filter { !it.unpublished && it.authorUid != me }.map {
                val ref = LegRef.Custom(it.gameId, it.code)
                val solvedIt = ref.wire in solved
                SourceRows.Dated(
                    atMs = it.openedAt.toEpochMilli(),
                    puzzle = PickablePuzzle(
                        entry = ExactPuzzle(ref, it.authorUid, it.title, it.authorName, PuzzleSource.Friend),
                        isNew = !solvedIt,
                        played = solvedIt,
                        meta = metaOf(it.variant.value, it.difficulty?.name),
                        code = it.code,
                    ),
                )
            },
        )
    }
}
