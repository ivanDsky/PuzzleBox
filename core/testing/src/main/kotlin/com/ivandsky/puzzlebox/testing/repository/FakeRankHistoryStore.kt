package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.rankhistory.RankHistoryStore
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/** In-memory [RankHistoryStore] for tests; [initial] weeks are kept from the start. */
class FakeRankHistoryStore(initial: List<RankHistoryStore.Week> = emptyList()) : RankHistoryStore {

    private data class Key(val ownerUid: String, val game: GameId, val weekStart: LocalDate)

    private val kept = ConcurrentHashMap<Key, RankHistoryStore.Week>()

    init {
        initial.forEach { kept.putIfAbsent(it.key(), it) }
    }

    override suspend fun weeks(ownerUid: String, game: GameId, weekStarts: ClosedRange<LocalDate>): List<RankHistoryStore.Week> =
        kept.values
            .filter { it.ownerUid == ownerUid && it.game == game && it.weekStart in weekStarts }
            .sortedBy { it.weekStart }

    override suspend fun insertOnce(week: RankHistoryStore.Week): Boolean = kept.putIfAbsent(week.key(), week) == null

    private fun RankHistoryStore.Week.key() = Key(ownerUid, game, weekStart)
}
