package com.ivandsky.puzzlebox.app.leaderboard

import android.util.Log
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.CustomBoardEntries
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.SubmitError
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.model.ShareCode
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Posts this user's first win of each friend puzzle to that puzzle's board, and records the
 * outcome on the received row. Friend wins never go through [LeaderboardSubmitWatcher], so the
 * daily boards never see them.
 *
 * Each puzzle is tried at most once per process and identity: a rejection waits for the next app
 * start, so [MAX_ATTEMPTS] spans app starts rather than one burst. An unavailable board leaves the
 * row Pending and untried, so the next change of identity, rows or records tries again.
 */
class CustomBoardSubmitter(
    private val identity: IdentityService,
    private val received: ReceivedPuzzleRepository,
    private val completions: CompletionRepository,
    private val boards: CustomBoardRepository,
    private val scope: CoroutineScope,
) {

    private val tried: MutableSet<Pair<String, ShareCode>> = ConcurrentHashMap.newKeySet()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        scope.launch {
            identity.current.flatMapLatest { me ->
                if (me == null) flowOf(emptyList()) else postsFor(me)
            }.collect { posts ->
                for (post in posts) {
                    val key = post.entry.uid to post.puzzle.code
                    if (!tried.add(key)) continue
                    scope.launch { if (!post(post)) tried.remove(key) }
                }
            }
        }
    }

    /** Every Pending friend puzzle [me] may post, paired with the entry its first win makes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun postsFor(me: Identity): Flow<List<Post>> =
        received.observeAll()
            .map { rows -> rows.filter { it.postableBy(me.uid) } }
            .flatMapLatest { rows ->
                if (rows.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(rows.map { firstWin(it, me) }) { posts -> posts.filterNotNull() }
                }
            }

    private fun firstWin(puzzle: ReceivedPuzzle, me: Identity): Flow<Post?> =
        completions.observeCustom(puzzle.gameId, puzzle.code.value).map { records ->
            records.firstNotNullOfOrNull { CustomBoardEntries.fromRecord(it, me.uid, me.displayName) }
                ?.let { Post(puzzle, it) }
        }

    /** True when the outcome is settled on the row; false leaves the puzzle to retry on a later change. */
    private suspend fun post(post: Post): Boolean = try {
        val puzzle = post.puzzle
        when (val result = boards.submit(puzzle.code, post.entry)) {
            is SubmitResult.Ok, is SubmitResult.Deduped -> {
                received.updatePost(puzzle.code, PostState.Posted, puzzle.postAttempts)
                true
            }
            is SubmitResult.Err -> when (result.error) {
                is SubmitError.Rejected -> {
                    val attempts = puzzle.postAttempts + 1
                    val state = if (attempts >= MAX_ATTEMPTS) PostState.LocalOnly else PostState.Pending
                    received.updatePost(puzzle.code, state, attempts)
                    true
                }
                is SubmitError.Unavailable, is SubmitError.Unknown -> {
                    Log.w(TAG, "custom board post deferred: ${result.error}")
                    false
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "custom board post deferred: ${e.message}")
        false
    }

    private fun ReceivedPuzzle.postableBy(uid: String): Boolean =
        postState == PostState.Pending && !unpublished && authorUid != uid

    private data class Post(val puzzle: ReceivedPuzzle, val entry: LeaderboardEntry)

    companion object {
        const val MAX_ATTEMPTS: Int = 3
        private const val TAG = "CustomBoardSubmit"
    }
}
