package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.FinalBoardStore
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** In-memory [FinalBoardStore] for tests; counts gets and puts. */
class FakeFinalBoardStore(initial: Map<LeaderboardRef, BoardPage> = emptyMap()) : FinalBoardStore {

    private val boards = ConcurrentHashMap(initial)
    private val gets = AtomicInteger()
    private val puts = AtomicInteger()
    val getCalls: Int get() = gets.get()
    val putCalls: Int get() = puts.get()

    override suspend fun get(board: LeaderboardRef): BoardPage? {
        gets.incrementAndGet()
        return boards[board]
    }

    override suspend fun put(board: LeaderboardRef, page: BoardPage) {
        puts.incrementAndGet()
        boards[board] = page
    }
}
