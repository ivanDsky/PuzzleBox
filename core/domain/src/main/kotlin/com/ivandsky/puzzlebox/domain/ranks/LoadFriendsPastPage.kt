package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The friends a board's read page leaves out (backlog #1767). A board is read to its page (A23), the first
 * [BoardPage.PAGE_SIZE] places, so a friend who placed past it is not on it and the Friends boards would leave them
 * out. When a page is full and some of the viewer's friends are not on it, those friends' own entries are read by id
 * ([LeaderboardRepository.fetchPlayers]); [friendBoard] merges them in.
 *
 * A short page is the whole board, so it costs no read. A read that fails or runs long adds nothing, and is asked
 * again by the next pass, so the boards never hold on a friend's read.
 */
class LoadFriendsPastPage(private val leaderboard: LeaderboardRepository) {

    /**
     * What one collection of the Friends boards has read, so a board is asked about a friend once while it is shown:
     * the next collection starts over, and so sees a friend who has placed past the page since. Cheap to make.
     */
    class Memo {
        internal val known = ConcurrentHashMap<LeaderboardRef, Known>()
    }

    /** What one board has been asked and what it answered. */
    internal class Known {
        val asked: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val found = ConcurrentHashMap<String, LeaderboardEntry>()
    }

    /**
     * For each loaded board of [read] with a full page, the entries of [players] who are not on the page, except the
     * viewer ([viewerUid]), whose own entry the viewer's completions give (`beyondPageEntries`). The boards are read
     * at once, each for at most [READ_TIMEOUT_MS], and what [memo] holds is not asked again.
     */
    suspend operator fun invoke(memo: Memo, read: RankWindowBoards, players: Set<String>, viewerUid: String?): Map<LeaderboardRef, List<LeaderboardEntry>> {
        val friends = players - setOfNotNull(viewerUid)
        val loaded = (read as? RankWindowBoards.Boards)?.boards.orEmpty().filterIsInstance<BoardLoad.Loaded>()
        val answers = coroutineScope { loaded.map { async { it.board to entriesPast(memo, it.page, it.board, friends) } }.awaitAll() }
        return answers.filter { (_, entries) -> entries.isNotEmpty() }.toMap()
    }

    private suspend fun entriesPast(memo: Memo, page: BoardPage, board: LeaderboardRef, friends: Set<String>): List<LeaderboardEntry> {
        if (page.entries.size < BoardPage.PAGE_SIZE) return emptyList()
        val missing = friends - page.entries.mapTo(HashSet()) { it.uid }
        if (missing.isEmpty()) return emptyList()
        val state = memo.known.computeIfAbsent(board) { Known() }
        val unasked = missing - state.asked
        if (unasked.isNotEmpty()) {
            val read = withTimeoutOrNull(READ_TIMEOUT_MS) { leaderboard.fetchPlayers(board, unasked) }
            if (read != null) {
                read.filter { it.uid in unasked }.forEach { state.found[it.uid] = it }
                state.asked += unasked
            }
        }
        return missing.mapNotNull(state.found::get)
    }

    private companion object {
        const val READ_TIMEOUT_MS = 5_000L
    }
}
