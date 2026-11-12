package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.database.linkedin.FetchLogEntity
import com.ivandsky.puzzlebox.database.linkedin.LinkedInDatabase
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * What the day view shows and the entry to its sync. The only caller of the account's own-result and
 * board reads is [LinkedInDaySync], so every rule about asking LinkedIn is kept there; a day viewed from
 * two places is still synced once ([LinkedInSyncState.shared]). Takes no dispatcher: Room's suspend DAOs
 * are main-safe and the source is a suspending port.
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
    private val avatars: LinkedInAvatars,
    storeLock: LinkedInStoreLock,
    private val clock: () -> Long,
) : LinkedInDays {

    /** The only state kept between calls; everything else is read from the link and the database. */
    internal val syncState = LinkedInSyncState()

    private val fetchLog = db.fetchLogDao()
    private val ghostDao = db.ghostDao()
    private val ghosts = LinkedInGhostWriter(ghostDao)

    private val window = LinkedInDayWindow(calendar)

    private val daySync = LinkedInDaySync(
        link, source, completions, daily, window, registry, fetchLog, ghosts, budget, avatars, storeLock, syncState, clock,
    )

    override suspend fun sync(game: GameId, day: LocalDate): LinkedInSync =
        syncState.shared(DayKey(game, day)) { daySync.sync(game, day) }

    override fun willRead(game: GameId, day: LocalDate): Boolean = daySync.willRead(game, day)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(game: GameId, day: LocalDate): Flow<LinkedInDayData> {
        val key = DayKey(game, day)
        return link.state.flatMapLatest { linked ->
            // The one place that sees every link change, even when no sync runs between a disconnect and
            // the next connect: what a sync remembered under a link that is gone or paused must not show
            // under the next one.
            if (linked !is LinkState.Connected) syncState.clear()
            val profileId = when (linked) {
                LinkState.NotConnected -> null
                is LinkState.Connected -> linked.profileId
                is LinkState.Paused -> linked.profileId
            }
            combine(
                imported(game, day),
                stored(game, day, profileId),
                fetches(game, day, profileId),
                syncState.inFlight(key),
                syncState.lastSync(key),
            ) { import, board, fetches, inFlight, lastSync ->
                // Read on every emission, never watched: a day held open across the game's midnight
                // keeps its delta until its holder subscribes again.
                val delta = window.deltaOf(game, day)
                LinkedInDayData(
                    link = linked,
                    delta = delta,
                    boardInWindow = window.inBoardWindow(delta),
                    inFlight = inFlight,
                    lastSync = lastSync,
                    own = ownState(import, fetches),
                    ghosts = board.ghosts,
                    boardFetchedAtMs = fetches.firstOrNull { it.item == LinkedInItem.Board.name }?.fetchedAtMs,
                    owner = board.owner,
                )
            }
            // Room emits again on any write to a table, so a sync of another day would repeat this one.
        }.distinctUntilChanged()
    }

    /**
     * The day's import, whatever the link: a disconnect keeps imports, and the day still shows the
     * result as LinkedIn's. A record and a replay of one day are the same result, so the first will do.
     */
    private fun imported(game: GameId, day: LocalDate): Flow<CompletionRecord?> =
        completions.observeImports().map { rows ->
            rows.firstOrNull { it.puzzleRef.gameId == game && (it.puzzleRef.key as? PuzzleKey.Daily)?.date == day }
        }

    /**
     * The day's board; nothing without a link, whatever a disconnect has not wiped yet. The player's own row sits
     * in the same table, so it is split out by the profile it was read for and never reaches the connections.
     * A day whose board holds no row of the player (a past board is never read again) shows the player as the
     * newest row stored for the profile, from any game and day: their name and photo belong to the account.
     */
    private fun stored(game: GameId, day: LocalDate, profileId: String?): Flow<StoredBoard> =
        if (profileId == null) {
            flowOf(StoredBoard(emptyList(), null))
        } else {
            combine(ghostDao.observeWithNewest(game.value, day.toString(), profileId), avatars.present) { rows, photos ->
                val (own, others) = rows.partition { it.memberRef == profileId }
                StoredBoard(
                    ghosts = others.map {
                        LinkedInGhost(it.memberRef, it.name, it.timeMs, it.hintFree, it.flawless, avatars.pathOf(it.memberRef, photos))
                    },
                    owner = own.firstOrNull()?.let { LinkedInOwner(it.name, avatars.pathOf(it.memberRef, photos)) },
                )
            }
        }

    private fun fetches(game: GameId, day: LocalDate, profileId: String?): Flow<List<FetchLogEntity>> =
        if (profileId == null) flowOf(emptyList()) else fetchLog.observe(game.value, day.toString(), profileId)

    private fun ownState(import: CompletionRecord?, fetches: List<FetchLogEntity>): LinkedInOwnState = when {
        import != null -> LinkedInOwnState.Imported(import)
        fetches.any { it.item == LinkedInItem.Own.name && it.outcome == LinkedInOutcome.NotPlayed.name } ->
            LinkedInOwnState.NotPlayed
        else -> LinkedInOwnState.Unknown
    }

    /** A day's stored board as observers see it: the connections and, apart, the player. */
    private class StoredBoard(val ghosts: List<LinkedInGhost>, val owner: LinkedInOwner?)
}
