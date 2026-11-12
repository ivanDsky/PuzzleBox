package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.cloud.OwnerScope
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
import kotlinx.coroutines.flow.first
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
    ownerScope: OwnerScope,
    private val clock: () -> Long,
) : LinkedInDays {

    /** The only state kept between calls; everything else is read from the link and the database. */
    internal val syncState = LinkedInSyncState()

    private val fetchLog = db.fetchLogDao()
    private val ghosts = LinkedInGhostWriter(db, ownerScope)
    private val savedBoards = SavedBoardDays(db, avatars, ownerScope)

    private val window = LinkedInDayWindow(calendar)

    private val daySync = LinkedInDaySync(
        link, source, completions, daily, window, registry, fetchLog, ghosts, budget, avatars, storeLock, syncState, clock,
    )

    override suspend fun sync(game: GameId, day: LocalDate): LinkedInSync {
        // Read once: the profile that keys the claim is the one the sync then asks as.
        val linked = link.state.first()
        if (linked !is LinkState.Connected) return daySync.unlinked(linked)
        val profileId = linked.profileId
        return syncState.shared(DayKey(profileId, game, day)) { daySync.sync(game, day, profileId) }
    }

    override fun willRead(game: GameId, day: LocalDate): Boolean = daySync.willRead(game, day)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(game: GameId, day: LocalDate): Flow<LinkedInDayData> {
        return link.state.flatMapLatest { linked ->
            // Memory hygiene only: results are keyed by profile, so none can show under another account
            // whether or not this ever saw the link go away.
            if (linked !is LinkState.Connected) syncState.clear()
            val profileId = when (linked) {
                LinkState.NotConnected -> null
                is LinkState.Connected -> linked.profileId
                is LinkState.Paused -> linked.profileId
            }
            combine(
                imported(game, day),
                savedBoards.observe(game, day, profileId),
                fetches(game, day, profileId),
                inFlight(game, day, profileId),
                lastSync(game, day, profileId),
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
                    boardFetchedAtMs = board.readAtMs,
                    owner = board.owner,
                    boardComplete = board.complete,
                )
            }
            // Room emits again on any write to a table, so a sync of another day would repeat this one.
        }.distinctUntilChanged()
    }

    override fun observeSavedDays(game: GameId): Flow<Set<LocalDate>> = savedBoards.observeDays(game)

    /**
     * The day's import, whatever the link: a disconnect keeps imports, and the day still shows the
     * result as LinkedIn's. A record and a play-again of one day are the same result, so the first will do.
     */
    private fun imported(game: GameId, day: LocalDate): Flow<CompletionRecord?> =
        completions.observeImports().map { rows ->
            rows.firstOrNull { it.puzzleRef.gameId == game && (it.puzzleRef.key as? PuzzleKey.Daily)?.date == day }
        }

    private fun inFlight(game: GameId, day: LocalDate, profileId: String?): Flow<Boolean> =
        if (profileId == null) flowOf(false) else syncState.inFlight(DayKey(profileId, game, day))

    private fun lastSync(game: GameId, day: LocalDate, profileId: String?): Flow<LinkedInSync?> =
        if (profileId == null) flowOf(null) else syncState.lastSync(DayKey(profileId, game, day))

    private fun fetches(game: GameId, day: LocalDate, profileId: String?): Flow<List<FetchLogEntity>> =
        if (profileId == null) flowOf(emptyList()) else fetchLog.observe(game.value, day.toString(), profileId)

    private fun ownState(import: CompletionRecord?, fetches: List<FetchLogEntity>): LinkedInOwnState = when {
        import != null -> LinkedInOwnState.Imported(import)
        fetches.any { it.item == LinkedInItem.Own.name && it.outcome == LinkedInOutcome.NotPlayed.name } ->
            LinkedInOwnState.NotPlayed
        else -> LinkedInOwnState.Unknown
    }
}
