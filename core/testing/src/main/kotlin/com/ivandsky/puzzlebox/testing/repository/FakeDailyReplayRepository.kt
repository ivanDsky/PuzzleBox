package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.replay.DailyReplayRepository

/** In-memory [DailyReplayRepository]: publishes as [uid]; script other players' replays, denials and write failures. */
class FakeDailyReplayRepository(private val uid: String? = "me") : DailyReplayRepository {

    private val blobs = mutableMapOf<Pair<String, String>, ByteArray>()

    /** Boards whose replays read as denied (null), like an owner who is not on the replays tier. */
    val deniedReads = mutableSetOf<Pair<LeaderboardRef, String>>()

    /** Makes [publish] return false, like an offline or rejected write. */
    var failPublish = false

    val publishCalls = mutableListOf<Pair<LeaderboardRef, ByteArray>>()
    val deleteCalls = mutableListOf<LeaderboardRef>()

    /** Stores another player's replay. */
    fun putReplay(board: LeaderboardRef, uid: String, blob: ByteArray) {
        blobs[board.boardId to uid] = blob.copyOf()
    }

    override suspend fun publish(board: LeaderboardRef, blob: ByteArray): Boolean {
        publishCalls += board to blob.copyOf()
        val owner = uid ?: return false
        if (failPublish || blob.isEmpty() || blob.size > DailyReplayRepository.MAX_BYTES) return false
        blobs[board.boardId to owner] = blob.copyOf()
        return true
    }

    override suspend fun fetch(board: LeaderboardRef, uid: String): ByteArray? {
        if (board to uid in deniedReads) return null
        return blobs[board.boardId to uid]?.copyOf()
    }

    override suspend fun delete(board: LeaderboardRef) {
        deleteCalls += board
        uid?.let { blobs.remove(board.boardId to it) }
    }
}
