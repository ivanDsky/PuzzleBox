package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
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

/**
 * One board's entries with a LinkedIn [day] merged in by the rule of the Day board (`linkedInTimeWins`): a connection
 * with a time is one more entry under its [connectionUid]; a hidden time takes no place and is not counted; a
 * connection named like an entry is that same player, so the entry takes the connection's time and the connection is
 * not added; the viewer's own import replaces their time only on an entry they really have. Entries come first, so on
 * a tie the PuzzleBox entry is listed before the connection. Internal: a board takes connections only as a step of
 * [friendBoard], after it is narrowed to the friends.
 */
internal fun BoardPage.withConnections(day: LinkedInDayView, viewerUid: String?): BoardPage {
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
