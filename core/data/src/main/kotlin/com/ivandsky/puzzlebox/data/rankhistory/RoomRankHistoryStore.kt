package com.ivandsky.puzzlebox.data.rankhistory

import com.ivandsky.puzzlebox.database.RankHistoryDao
import com.ivandsky.puzzlebox.database.RankHistoryEntity
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/** [RankHistoryStore] over the main database's `rank_history` table; week starts are stored as ISO dates. */
class RoomRankHistoryStore(private val dao: RankHistoryDao) : RankHistoryStore {

    override suspend fun weeks(ownerUid: String, game: GameId, weekStarts: ClosedRange<LocalDate>): List<RankHistoryStore.Week> =
        dao.weeks(ownerUid, game.value, weekStarts.start.toString(), weekStarts.endInclusive.toString()).map { it.toWeek() }

    override suspend fun insertOnce(week: RankHistoryStore.Week): Boolean = dao.insertOnce(week.toEntity()) != NOT_INSERTED

    private fun RankHistoryEntity.toWeek() =
        RankHistoryStore.Week(ownerUid, GameId(gameId), LocalDate.parse(weekStart), pointsPerDay, friendCount, formatVersion)

    private fun RankHistoryStore.Week.toEntity() =
        RankHistoryEntity(ownerUid, game.value, weekStart.toString(), pointsPerDay, friendCount, formatVersion)

    private companion object {
        /** What Room's insert returns when an ignored conflict wrote nothing. */
        const val NOT_INSERTED = -1L
    }
}
