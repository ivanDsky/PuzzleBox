package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.COMMUNITY_WINDOW_MS
import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.data.repository.CommunityListResult
import com.ivandsky.puzzlebox.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * The newest public puzzles of every game, asked of the server once each time [observe] is collected, so one sheet
 * life is one fetch however the query changes. The last good list of each game is kept in [cache]; a game the
 * server can't be reached for shows that list and marks the rows offline. Needs to be one instance for the app.
 */
internal class CommunitySource(
    private val community: ListCommunityPuzzles,
    private val registry: GameRegistry,
    private val clock: () -> Long,
) {
    private val cache = ConcurrentHashMap<GameId, List<SharedPuzzleDoc>>()

    fun observe(played: Flow<Set<String>>): Flow<SourceRows> = flow {
        val offline = fetch()
        emitAll(played.map { solved -> SourceRows(cache.values.flatten().map { it.row(solved) }, offline) })
    }

    /** Refreshes [cache]; true when any game could not be reached. */
    private suspend fun fetch(): Boolean = coroutineScope {
        registry.all.map { game ->
            val id = game.definition.id
            async {
                when (val result = list(id)) {
                    is CommunityListResult.Listed -> false.also { cache[id] = result.puzzles }
                    CommunityListResult.Offline -> true
                    CommunityListResult.Refused -> false
                }
            }
        }.awaitAll().any { it }
    }

    private suspend fun list(game: GameId): CommunityListResult = try {
        community(CommunityFilter(gameId = game.value))
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        CommunityListResult.Offline
    }

    private fun SharedPuzzleDoc.row(solved: Set<String>): SourceRows.Dated {
        val ref = LegRef.Custom(GameId(gameId), code)
        return SourceRows.Dated(
            atMs = createdAtMs,
            puzzle = PickablePuzzle(
                entry = ExactPuzzle(ref, authorUid, title, authorName, PuzzleSource.Community),
                isNew = createdAtMs >= clock() - COMMUNITY_WINDOW_MS,
                played = ref.wire in solved,
                meta = metaOf(variant, difficulty?.name),
                code = code,
            ),
        )
    }
}
