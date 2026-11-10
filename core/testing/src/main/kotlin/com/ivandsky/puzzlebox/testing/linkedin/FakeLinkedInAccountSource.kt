package com.ivandsky.puzzlebox.testing.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Scriptable [LinkedInAccountSource]. Every call is recorded in [calls] as `"check:<gameTypeId>"`,
 * `"own:<profileId>:<gameTypeId>:<puzzleNumber>"` or `"board:<profileId>:<gameTypeId>:<todayNumber>:<delta>"`.
 * An unscripted call answers [LinkedInRead.Offline], the same as the real source with no network.
 *
 * Thread-safe: the days use case calls it from its own dispatcher while the test scripts it.
 */
class FakeLinkedInAccountSource : LinkedInAccountSource {

    /** The reply to every following [check]. */
    @Volatile
    var checkReply: LinkedInRead<String> = LinkedInRead.Offline

    private val ownReplies = ConcurrentHashMap<String, LinkedInRead<LinkedInOwnResult>>()
    private val boardReplies = ConcurrentHashMap<String, LinkedInRead<LinkedInBoard>>()
    private val recorded = CopyOnWriteArrayList<String>()

    /** Every call so far, oldest first. */
    val calls: List<String> get() = recorded.toList()

    fun respondOwn(profileId: String, gameTypeId: Int, puzzleNumber: Int, reply: LinkedInRead<LinkedInOwnResult>) {
        ownReplies[ownKey(profileId, gameTypeId, puzzleNumber)] = reply
    }

    fun respondBoard(profileId: String, gameTypeId: Int, todayNumber: Int, delta: Int, reply: LinkedInRead<LinkedInBoard>) {
        boardReplies[boardKey(profileId, gameTypeId, todayNumber, delta)] = reply
    }

    override suspend fun check(gameTypeId: Int): LinkedInRead<String> {
        recorded += "check:$gameTypeId"
        return checkReply
    }

    override suspend fun ownResult(
        profileId: String,
        gameTypeId: Int,
        puzzleNumber: Int,
    ): LinkedInRead<LinkedInOwnResult> {
        recorded += "own:${ownKey(profileId, gameTypeId, puzzleNumber)}"
        return ownReplies[ownKey(profileId, gameTypeId, puzzleNumber)] ?: LinkedInRead.Offline
    }

    override suspend fun board(
        profileId: String,
        gameTypeId: Int,
        todayNumber: Int,
        delta: Int,
    ): LinkedInRead<LinkedInBoard> {
        recorded += "board:${boardKey(profileId, gameTypeId, todayNumber, delta)}"
        return boardReplies[boardKey(profileId, gameTypeId, todayNumber, delta)] ?: LinkedInRead.Offline
    }

    private fun ownKey(profileId: String, gameTypeId: Int, puzzleNumber: Int) =
        "$profileId:$gameTypeId:$puzzleNumber"

    private fun boardKey(profileId: String, gameTypeId: Int, todayNumber: Int, delta: Int) =
        "$profileId:$gameTypeId:$todayNumber:$delta"
}
