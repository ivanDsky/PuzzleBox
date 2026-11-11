package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.database.linkedin.FetchLogEntity
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first

/**
 * The only caller of the account's own-result and board reads, so every rule about asking LinkedIn
 * is kept in one place: nothing is sent while the link is paused or absent, an item is asked only
 * when its cache window says it is due and the call budget allows it, and the first rejection pauses
 * every later read. Takes no dispatcher: Room's suspend DAOs are main-safe and the source is a
 * suspending port.
 */
internal class RoomLinkedInDays(
    private val link: LinkedInLink,
    private val source: LinkedInAccountSource,
    private val completions: CompletionRepository,
    private val daily: DailyRepository,
    private val calendar: GameCalendar,
    private val registry: GameRegistry,
    db: LinkedInDatabase,
    private val budget: LinkedInCallBudget,
    private val clock: () -> Long,
) : LinkedInDays {

    /** The only state kept between calls; everything else is read from the link and the database. */
    internal val syncState = LinkedInSyncState()

    private val fetchLog = db.fetchLogDao()
    private val ghosts = LinkedInGhostWriter(db.ghostDao())

    override suspend fun sync(game: GameId, day: LocalDate): LinkedInSync =
        syncState.shared(DayKey(game, day)) { syncDay(game, day) }

    private suspend fun syncDay(game: GameId, day: LocalDate): LinkedInSync {
        val profileId = when (val linked = link.state.first()) {
            is LinkState.Connected -> linked.profileId
            else -> return unlinked(linked)
        }
        val typeId = daily.linkedInGameTypeId(game) ?: return LinkedInSync.NotLinkedInGame
        // A day in the future has nothing to read.
        val delta = deltaOf(game, day)?.takeIf { it >= 0 } ?: return LinkedInSync.NotLinkedInGame
        val ownNumber = daily.puzzleNumber(game, day) ?: return LinkedInSync.NotLinkedInGame
        val request = Request(game, day, delta, profileId, typeId)

        syncOwn(request, ownNumber)?.let { return it }
        if (inBoardWindow(delta)) {
            // Without a number for today the board cannot be addressed; the own result already read stands.
            val todayNumber = daily.puzzleNumber(game, request.today)
            if (todayNumber != null) syncBoard(request, todayNumber)?.let { return it }
        }
        return LinkedInSync.Done
    }

    /**
     * Whole days from [day] to the game's today, or null when the calendar has no today for [game].
     * Today is the game's cadence-zone date, never the device's, and the difference is taken between
     * calendar dates, so neither a 23- or 25-hour day nor the zone's midnight can shift it.
     */
    private fun deltaOf(game: GameId, day: LocalDate): Int? =
        calendar.today(game)?.let { today -> ChronoUnit.DAYS.between(day, today).toInt() }

    /** LinkedIn serves a connections board only for today and the few days before it. */
    private fun inBoardWindow(delta: Int?): Boolean =
        delta != null && delta in 0..LinkedInFetchPolicy.BOARD_WINDOW_DAYS

    /** Null when the own result is done (read, or not due) and the sync goes on; else the result that ends it. */
    private suspend fun syncOwn(request: Request, number: Int): LinkedInSync? {
        val nowMs = clock()
        if (!due(request, LinkedInItem.Own, nowMs)) return null
        if (!budget.tryAcquire()) return LinkedInSync.OverBudget
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
        val nowMs = clock()
        if (!due(request, LinkedInItem.Board, nowMs)) return null
        if (!budget.tryAcquire()) return LinkedInSync.OverBudget
        val read = source.board(request.profileId, request.typeId, todayNumber, request.delta)
        return settle(request, read) { board ->
            ghosts.write(request.game, request.day, request.today, request.profileId, board, nowMs)
            // A board read while its day is still running is partial; the outcome marks it for one more read.
            val outcome = if (request.delta == 0) LinkedInOutcome.BoardToday else LinkedInOutcome.Board
            log(request, LinkedInItem.Board, outcome, nowMs)
            null
        }
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
     * store while a read is still on the wire, and nothing else stops that read from landing. When the
     * link is no longer the one that asked, the answer is dropped and nothing is written, not even a
     * pause. Only a successful read reaches [store], so an offline or rejected read is never logged.
     */
    private suspend fun <T> settle(
        request: Request,
        read: LinkedInRead<T>,
        store: suspend (T) -> LinkedInSync?,
    ): LinkedInSync? {
        val linked = link.state.first()
        if (linked != LinkState.Connected(request.profileId)) return unlinked(linked)
        return when (read) {
            is LinkedInRead.Ok -> store(read.value)
            LinkedInRead.Offline -> LinkedInSync.Offline
            // Nothing was sent, but the session is gone all the same, so the player is asked to reconnect.
            LinkedInRead.SignedOut -> pause(LinkedInRead.Rejected.Kind.SessionExpired)
            is LinkedInRead.Rejected -> pause(read.kind)
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
