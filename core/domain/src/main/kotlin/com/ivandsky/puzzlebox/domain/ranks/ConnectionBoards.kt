package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.linkedin.linkedInScore
import com.ivandsky.puzzlebox.game.linkedin.linkedInTimeWins

private const val CONNECTION_UID = "linkedin:"

/**
 * The key a LinkedIn connection is ranked under (F21): their profile's member ref, which is the same on every game
 * and day, so a connection is one player across a window. The prefix keeps it apart from every PuzzleBox uid.
 */
fun connectionUid(memberRef: String): String = CONNECTION_UID + memberRef

/** Whether a ranked row's [uid] is a LinkedIn connection's ([connectionUid]) rather than a PuzzleBox player's. */
fun isConnectionUid(uid: String): Boolean = uid.startsWith(CONNECTION_UID)

/**
 * These Friends boards ([amongFriends]) with the LinkedIn [days] the phone has saved counted on them (F21): every
 * loaded board that has a saved day takes it in ([BoardPage.withConnections]). Ranking the result with [rankBoard],
 * [overall] or [gameWindowStandings] then places the connections among the friends, and N counts them. Local data
 * only: nothing is read, and a board without a saved day is as it was.
 */
fun RankWindowBoards.withConnections(days: Map<LeaderboardRef, LinkedInDayView>, viewerUid: String?): RankWindowBoards {
    if (this !is RankWindowBoards.Boards || days.isEmpty()) return this
    return RankWindowBoards.Boards(
        boards.map { load ->
            val day = days[load.board]
            if (load is BoardLoad.Loaded && day != null) load.copy(page = load.page.withConnections(day, viewerUid)) else load
        },
    )
}

/**
 * One board's entries with a LinkedIn [day] merged in by the rule of the Day board (`linkedInTimeWins`): a connection
 * with a time is one more entry under its [connectionUid]; a hidden time takes no place and is not counted; a
 * connection named like an entry is that same player, so the entry takes the connection's time and the connection is
 * not added; the viewer's own import replaces their time only on an entry they really have. Entries come first, so on
 * a tie the PuzzleBox entry is listed before the connection.
 */
fun BoardPage.withConnections(day: LinkedInDayView, viewerUid: String?): BoardPage {
    val ghosts = (day.board as? LinkedInBoardView.Shown)?.ghosts.orEmpty()
    val self = entries.indexOfFirst { it.uid == viewerUid }.takeIf { it >= 0 }
    val own = day.ownScore.takeIf { self != null }
    if (ghosts.isEmpty() && own == null) return this
    val ghostScores = ghosts.map { ghost -> ghost.timeMs?.let { linkedInScore(it, ghost.hintFree) } }
    val wins = linkedInTimeWins(
        rowNames = entries.map { it.displayName },
        rowScores = entries.map { BestScore(it.timeMs, it.hints) },
        selfRow = self,
        ghostNames = ghosts.map { it.name },
        ghostScores = ghostScores,
        own = own,
    )
    val shown = entries.mapIndexed { index, entry ->
        val score = wins.rowScores[index]
        entry.copy(timeMs = score.timeMs, hints = score.hints)
    }
    val added = ghosts.indices.mapNotNull { index ->
        val score = ghostScores[index]?.takeIf { wins.ghostKept[index] } ?: return@mapNotNull null
        // Never stored or posted: the entry exists only to be ranked, so it names no puzzle and no finish time.
        LeaderboardEntry(connectionUid(ghosts[index].memberRef), ghosts[index].name, puzzleRef = "", score.timeMs, score.hints, completedAtMs = 0L)
    }
    return BoardPage(shown + added, count + added.size)
}
