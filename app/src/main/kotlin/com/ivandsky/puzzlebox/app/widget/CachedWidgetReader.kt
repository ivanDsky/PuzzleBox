package com.ivandsky.puzzlebox.app.widget

import com.ivandsky.puzzlebox.domain.TodayStatus
import java.time.LocalDate

/**
 * The widget's two pictures. [cached] is the last good read from [store], at once: as it was on the same puzzle day, and
 * as a new day when the day has turned (every game unsolved, no ranks, no new bests, the first game up next, the streak as
 * stored). Nothing cached is null, the empty state. [fresh] is a new [read]; a rank the read missed (Missing) keeps
 * today's cached rank, and the result is saved as today's picture. A failed read is null and leaves the store as it was.
 */
class CachedWidgetReader(
    private val read: suspend () -> WidgetState?,
    private val store: WidgetSnapshotStore,
    private val puzzleDay: () -> LocalDate,
) {

    suspend fun cached(): WidgetState? {
        val snapshot = store.load() ?: return null
        return if (snapshot.day == puzzleDay()) snapshot.state else newDay(snapshot.state)
    }

    suspend fun fresh(): WidgetState? {
        // The day before the read, so a read that crosses the rollover is filed under the day it started on.
        val day = puzzleDay()
        val state = read() ?: return null
        val sameDay = store.load()?.takeIf { it.day == day }?.state
        val kept = if (sameDay == null) state else keepRanks(state, sameDay)
        store.save(WidgetSnapshot(day, kept))
        return kept
    }

    private fun newDay(state: WidgetState): WidgetState {
        val rings = state.rings.map { it.copy(status = TodayStatus.NotStarted, rank = WidgetRank.Missing, newBest = false) }
        val first = rings.firstOrNull()
        return state.copy(
            rings = rings,
            upNext = if (first == null) WidgetUpNext.Unknown else WidgetUpNext.Play(first.game, first.displayName),
            nextRollover = null,
        )
    }

    private fun keepRanks(fresh: WidgetState, cached: WidgetState): WidgetState {
        val ranked = cached.rings.associate { it.game to it.rank }.filterValues { it is WidgetRank.Ranked }
        return fresh.copy(
            rings = fresh.rings.map { ring ->
                val kept = ranked[ring.game]
                if (ring.rank == WidgetRank.Missing && kept != null) ring.copy(rank = kept) else ring
            },
        )
    }
}
