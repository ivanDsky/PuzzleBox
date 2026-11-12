package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.database.linkedin.FetchLogDao
import com.ivandsky.puzzlebox.database.linkedin.FetchLogEntity
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * One day's sync: the own result, then the board when the day is in LinkedIn's window. Reads nothing
 * unless the link is connected, an item's cache window says it is due and a call slot of the budget is
 * free (a shown day waits for one), and the first rejection pauses every later read. The day view reads
 * the same state through [RoomLinkedInDays]; this class only writes it.
 */
internal class LinkedInDaySync(
    private val link: LinkedInLink,
    private val source: LinkedInAccountSource,
    private val completions: CompletionRepository,
    private val daily: DailyRepository,
    private val window: LinkedInDayWindow,
    private val registry: GameRegistry,
    private val fetchLog: FetchLogDao,
    private val ghosts: LinkedInGhostWriter,
    private val budget: LinkedInCallBudget,
    private val avatars: LinkedInAvatars,
    private val storeLock: LinkedInStoreLock,
    private val syncState: LinkedInSyncState,
    private val clock: () -> Long,
) {

    suspend fun sync(game: GameId, day: LocalDate): LinkedInSync {
        val profileId = when (val linked = link.state.first()) {
            is LinkState.Connected -> linked.profileId
            else -> return unlinked(linked)
        }
        val readable = readable(game, day) ?: return LinkedInSync.NotLinkedInGame
        val request = Request(game, day, readable.delta, profileId, readable.typeId)

        syncOwn(request, readable.ownNumber)?.let { return it }
        if (window.inBoardWindow(request.delta)) {
            // Without a number for today the board cannot be addressed; the own result already read stands.
            val todayNumber = daily.puzzleNumber(game, request.today)
            if (todayNumber != null) syncBoard(request, todayNumber)?.let { return it }
        }
        return LinkedInSync.Done
    }

    /**
     * Whether [sync] can send a read for the day at all, link and cache windows aside: the game is a LinkedIn
     * one, the day is today or earlier and has a puzzle number. The day view asks this to tell a day that
     * will settle on its stored state from one a sync is about to read.
     */
    fun willRead(game: GameId, day: LocalDate): Boolean = readable(game, day) != null

    private fun readable(game: GameId, day: LocalDate): Readable? {
        val typeId = daily.linkedInGameTypeId(game) ?: return null
        // A day in the future has nothing to read.
        val delta = window.deltaOf(game, day)?.takeIf { it >= 0 } ?: return null
        val ownNumber = daily.puzzleNumber(game, day) ?: return null
        return Readable(typeId, delta, ownNumber)
    }

    /** Null when the own result is done (read, or not due) and the sync goes on; else the result that ends it. */
    private suspend fun syncOwn(request: Request, number: Int): LinkedInSync? {
        if (!due(request, LinkedInItem.Own, clock())) return null
        awaitSlot(request)?.let { return it }
        val nowMs = clock()
        val read = source.ownResult(request.profileId, request.typeId, number)
        return settle(request, read) { result ->
            when (result) {
                is LinkedInOwnResult.Solved -> {
                    val record = LinkedInImport.toRecord(request.game, request.day, number, result, registry)
                        ?: return@settle LinkedInSync.NotLinkedInGame
                    completions.onCompleted(record)
                    log(request, LinkedInItem.Own, LinkedInOutcome.Solved, nowMs)
                }
                LinkedInOwnResult.NotPlayed -> log(request, LinkedInItem.Own, LinkedInOutcome.NotPlayed, nowMs)
            }
            null
        }
    }

    /** Null when the board is done (read, or not due); else the result that ends the sync. */
    private suspend fun syncBoard(request: Request, todayNumber: Int): LinkedInSync? {
        if (!due(request, LinkedInItem.Board, clock())) return null
        awaitSlot(request)?.let { return it }
        val nowMs = clock()
        val read = source.board(request.profileId, request.typeId, todayNumber, request.delta)
        return settle(request, read) { board ->
            val kept = ghosts.write(request.game, request.day, request.today, request.profileId, board, nowMs)
            // A board read while its day is still running is partial; the outcome marks it for one more read.
            val outcome = if (request.delta == 0) LinkedInOutcome.BoardToday else LinkedInOutcome.Board
            log(request, LinkedInItem.Board, outcome, nowMs)
            // After the ghosts and the log are stored, and not awaited: a slow or failed photo never holds them
            // back. The wipe count is read here, under the store lock, so a disconnect cannot slip in before it.
            avatars.downloadInBackground(kept, avatars.generation())
            null
        }
    }

    /**
     * Waits for a free call slot for as long as the day is on screen: the sync is cancelled with the day, so
     * a day left never sends the call it waited for. The wait can be long and another day's call may have been
     * rejected meanwhile, so the link is read again once the slot is taken: null when the call may go out,
     * else the result that ends the sync.
     */
    private suspend fun awaitSlot(request: Request): LinkedInSync? {
        budget.acquire()
        val linked = link.state.first()
        return if (linked == LinkState.Connected(request.profileId)) null else unlinked(linked)
    }

    private suspend fun due(request: Request, item: LinkedInItem, nowMs: Long): Boolean {
        val row = fetchLog.get(request.game.value, request.day.toString(), item.name, request.profileId)
        // A row whose outcome no longer names an outcome says nothing about the item: it counts as never read.
        val last = row?.let { logged ->
            LinkedInOutcome.entries.firstOrNull { it.name == logged.outcome }?.let { LinkedInFetch(logged.fetchedAtMs, it) }
        }
        return LinkedInFetchPolicy.shouldFetch(item, request.delta, last, nowMs)
    }

    /**
     * What a finished read means for the sync. The link is read again first: a disconnect wipes the
     * store while a read is still on the wire, and nothing else stops that read from landing. The check
     * and the store run under [storeLock], which a disconnect holds for its whole wipe, so the link
     * cannot change in between and a store that began is wiped with the rest. When the link is no longer
     * the one that asked, the answer is dropped and nothing is written, not even a pause. Only a
     * successful read reaches [store], so an offline or rejected read is never logged.
     */
    private suspend fun <T> settle(
        request: Request,
        read: LinkedInRead<T>,
        store: suspend (T) -> LinkedInSync?,
    ): LinkedInSync? {
        return storeLock.withLock {
            val linked = link.state.first()
            if (linked != LinkState.Connected(request.profileId)) return@withLock unlinked(linked)
            when (read) {
                is LinkedInRead.Ok -> store(read.value)
                LinkedInRead.Offline -> LinkedInSync.Offline
                // Nothing was sent, but the session is gone all the same, so the player is asked to reconnect.
                LinkedInRead.SignedOut -> pause(LinkedInRead.Rejected.Kind.SessionExpired)
                is LinkedInRead.Rejected -> pause(read.kind)
            }
        }
    }

    private suspend fun pause(kind: LinkedInRead.Rejected.Kind): LinkedInSync {
        link.pause(kind)
        return LinkedInSync.Paused(kind)
    }

    /** The result for a link that is not (or no longer) the connected member a sync needs. */
    private fun unlinked(state: LinkState): LinkedInSync {
        syncState.clear()
        return if (state is LinkState.Paused) LinkedInSync.Paused(state.kind) else LinkedInSync.NotLinked
    }

    private suspend fun log(request: Request, item: LinkedInItem, outcome: LinkedInOutcome, nowMs: Long) {
        fetchLog.put(
            FetchLogEntity(
                game = request.game.value,
                day = request.day.toString(),
                item = item.name,
                profileId = request.profileId,
                fetchedAtMs = nowMs,
                outcome = outcome.name,
            ),
        )
    }

    /** What one sync asks for, fixed when it starts: a link or a midnight that changes meanwhile cannot move it. */
    private class Request(
        val game: GameId,
        val day: LocalDate,
        val delta: Int,
        val profileId: String,
        val typeId: Int,
    ) {
        /** The game's today as this sync saw it. */
        val today: LocalDate get() = day.plusDays(delta.toLong())
    }
}

/** What a read of one day needs besides the link: the game's LinkedIn type, the day's age and its puzzle number. */
private class Readable(val typeId: Int, val delta: Int, val ownNumber: Int)
