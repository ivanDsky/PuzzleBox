package com.ivandsky.puzzlebox.insights.domain.rankhistory

import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.rankhistory.RankHistoryStore
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

/**
 * The signed-in player's kept weekly Friends rank for one game, oldest week first, read from this device only. It never
 * writes and never reaches the cloud: [com.ivandsky.puzzlebox.domain.insights.rankhistory.SyncRankHistory] is what keeps weeks. Signed out it is empty, and an account
 * change reads the new account's weeks.
 *
 * The store has no live query, so each account is read once per collection; a week kept after that shows on the next
 * collection.
 */
internal class ObserveRankHistory(
    private val identity: IdentityService,
    private val store: RankHistoryStore,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(game: GameId): Flow<List<RankHistoryStore.Week>> =
        identity.current.map { it?.uid }.distinctUntilChanged().mapLatest { uid ->
            if (uid == null) emptyList() else store.weeks(uid, game, ALL_WEEKS)
        }

    private companion object {
        /** Every week the store can hold; the store compares ISO dates as text, so the bounds stay four-digit years. */
        val ALL_WEEKS: ClosedRange<LocalDate> = LocalDate.EPOCH..LocalDate.of(9999, 12, 31)
    }
}
