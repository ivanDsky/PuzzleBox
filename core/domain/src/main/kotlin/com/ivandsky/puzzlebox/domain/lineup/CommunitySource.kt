package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.COMMUNITY_WINDOW_MS
import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.data.repository.CommunityListResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.visibility.HiddenPuzzles
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The newest public puzzles of every game (the repository's newest 30 each, older than a day included), asked of the
 * server once each time [observe] is collected, so one sheet life is one fetch however the query changes. The cached
 * rows come out first and the fetched ones replace them, so nothing waits on the network. The last good list of each
 * game is kept in [cache]; a game the server can't be reached for keeps that list and marks the rows offline. Hidden
 * puzzles and the player's own are left out. Needs to be one instance for the app.
 */
internal class CommunitySource(
    private val shared: SharedPuzzleRepository,
    private val hidden: HiddenPuzzles,
    private val identity: IdentityService,
    private val registry: GameRegistry,
    private val clock: () -> Long,
) {
    private val cache = ConcurrentHashMap<GameId, List<SharedPuzzleDoc>>()

    private class Snapshot(val docs: List<SharedPuzzleDoc>, val offline: Boolean)

    fun observe(played: Flow<Set<String>>): Flow<SourceRows> = channelFlow {
        val snapshot = MutableStateFlow(Snapshot(cache.values.flatten(), offline = false))
        launch { snapshot.value = fetch() }
        combine(played, snapshot) { solved, now -> SourceRows(now.docs.map { it.row(solved) }, now.offline) }
            .collect { send(it) }
    }

    /** Refreshes [cache]; offline when any game could not be reached. */
    private suspend fun fetch(): Snapshot = coroutineScope {
        val me = identity.current.value?.uid
        val hiddenCodes = hidden.codes()
        val offline = registry.all.map { game ->
            val id = game.definition.id
            async {
                when (val result = list(id)) {
                    is CommunityListResult.Listed -> false.also {
                        cache[id] = result.puzzles.filter { it.authorUid != me && it.code !in hiddenCodes }
                    }
                    CommunityListResult.Offline -> true
                    CommunityListResult.Refused -> false
                }
            }
        }.awaitAll().any { it }
        Snapshot(cache.values.flatten(), offline)
    }

    private suspend fun list(game: GameId): CommunityListResult = try {
        shared.listPublic(CommunityFilter(gameId = game.value))
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
