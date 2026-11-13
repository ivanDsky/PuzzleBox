package com.ivandsky.puzzlebox.data.leaderboard

import com.ivandsky.puzzlebox.database.finalboard.FinalBoardDao
import com.ivandsky.puzzlebox.database.finalboard.FinalBoardEntity
import com.ivandsky.puzzlebox.database.finalboard.FinalBoardEntryEntity
import java.time.Clock

/** [FinalBoardStore] over the Final board database; [clock] stamps when a board was fetched. */
class RoomFinalBoardStore(
    private val dao: FinalBoardDao,
    private val clock: Clock = Clock.systemUTC(),
) : FinalBoardStore {

    override suspend fun get(board: LeaderboardRef): BoardPage? {
        val gameId = board.gameId
        val day = board.day.toString()
        val header = dao.header(gameId, day) ?: return null
        val entries = dao.entries(gameId, day).map {
            LeaderboardEntry(it.uid, it.displayName, it.puzzleRef, it.timeMs, it.hints, it.completedAtMs)
        }
        return BoardPage(entries, header.count)
    }

    override suspend fun put(board: LeaderboardRef, page: BoardPage) {
        val gameId = board.gameId
        val day = board.day.toString()
        dao.put(
            FinalBoardEntity(gameId, day, page.count, clock.millis()),
            page.entries.map {
                FinalBoardEntryEntity(gameId, day, it.uid, it.displayName, it.puzzleRef, it.timeMs, it.hints, it.completedAtMs)
            },
        )
    }
}
