package com.ivandsky.puzzlebox.testing.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDayData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDays
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwnState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInSync
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Scripted [LinkedInDays]. It stores no days and derives nothing: a test sets the exact
 * [LinkedInDayData] a day reports through [emit], and a sync only records its call and answers
 * [syncResult], so nothing a sync does shows up in [observe] by itself.
 */
class FakeLinkedInDays : LinkedInDays {

    private val recorded = CopyOnWriteArrayList<Pair<GameId, LocalDate>>()
    private val days = MutableStateFlow<Map<Pair<GameId, LocalDate>, LinkedInDayData>>(emptyMap())

    /** Every sync so far as `(game, day)`, oldest first, recorded when the sync is entered. */
    val syncCalls: List<Pair<GameId, LocalDate>> get() = recorded.toList()

    /** The answer of every following [sync]. */
    @Volatile
    var syncResult: LinkedInSync = LinkedInSync.Done

    /** When set, a sync suspends on it after recording its call, so a test can cancel it midway. */
    @Volatile
    var syncGate: CompletableDeferred<Unit>? = null

    override suspend fun sync(game: GameId, day: LocalDate): LinkedInSync {
        recorded += game to day
        syncGate?.await()
        return syncResult
    }

    private val unread = CopyOnWriteArrayList<Pair<GameId, LocalDate>>()

    /** Every day reads (true) until a test says a day is not read: not a LinkedIn game, a future day. */
    fun setWillRead(game: GameId, day: LocalDate, reads: Boolean) {
        if (reads) unread -= game to day else unread += game to day
    }

    override fun willRead(game: GameId, day: LocalDate): Boolean = (game to day) !in unread

    /** A day never set through [emit] reports an unlinked install that knows nothing. */
    override fun observe(game: GameId, day: LocalDate): Flow<LinkedInDayData> =
        days.map { it[game to day] ?: UNKNOWN }.distinctUntilChanged()

    fun emit(game: GameId, day: LocalDate, data: LinkedInDayData) {
        days.update { it + ((game to day) to data) }
    }

    private val savedDays = MutableStateFlow<Map<GameId, Set<LocalDate>>>(emptyMap())

    /** A game never set through [emitSavedDays] has no saved board. */
    override fun observeSavedDays(game: GameId): Flow<Set<LocalDate>> =
        savedDays.map { it[game].orEmpty() }.distinctUntilChanged()

    fun emitSavedDays(game: GameId, days: Set<LocalDate>) {
        savedDays.update { it + (game to days) }
    }

    private companion object {
        val UNKNOWN = LinkedInDayData(
            link = LinkState.NotConnected,
            delta = null,
            boardInWindow = false,
            inFlight = false,
            lastSync = null,
            own = LinkedInOwnState.Unknown,
            ghosts = emptyList(),
            boardFetchedAtMs = null,
        )
    }
}
