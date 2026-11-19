package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.linkedin.linkedInScore
import com.ivandsky.puzzlebox.game.linkedin.linkedInTimeWins
import com.ivandsky.puzzlebox.game.linkedin.LinkedInTimeWins

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
 * not added. The [viewer]'s own import replaces their time on their entry, and is their entry when they have none: a
 * day they played only on LinkedIn counts for them as a connection's does ([withOwnImport]). Entries come first, so on
 * a tie the PuzzleBox entry is listed before the connection. Internal: a board takes connections only as a step of
 * [friendBoard], after it is narrowed to the friends.
 */
internal fun BoardPage.withConnections(day: LinkedInDayView, viewer: Identity?): BoardPage {
    val board = withOwnImport(day, viewer)
    val wins = board.winsOf(day, viewer?.uid) ?: return board
    val ghosts = day.ghosts()
    val shown = board.entries.mapIndexed { index, entry ->
        val score = wins.rowScores[index]
        entry.copy(timeMs = score.timeMs, hints = score.hints)
    }
    val added = ghosts.indices.mapNotNull { index ->
        val score = ghosts[index].score()?.takeIf { wins.ghostKept[index] } ?: return@mapNotNull null
        // Never stored or posted: the entry exists only to be ranked, so it names no puzzle and no finish time.
        LeaderboardEntry(connectionUid(ghosts[index].memberRef), ghosts[index].name, puzzleRef = "", score.timeMs, score.hints, completedAtMs = 0L)
    }
    return BoardPage(shown + added, board.count + added.size)
}

/**
 * The board with the [viewer]'s own LinkedIn result of [day] as their entry when they have none on it (ADR-0031 §11,
 * 2026-11-19 amendment): ranked under their uid and PuzzleBox name, so a window adds it to the rest of their days, and
 * then the self row of the merge, so a connection named like them stays someone else.
 */
private fun BoardPage.withOwnImport(day: LinkedInDayView, viewer: Identity?): BoardPage {
    val own = day.ownScore ?: return this
    if (viewer == null || entries.any { it.uid == viewer.uid }) return this
    return BoardPage(entries + LeaderboardEntry(viewer.uid, viewer.displayName, puzzleRef = "", own.timeMs, own.hints, completedAtMs = 0L), count + 1)
}

/**
 * The connections of [day] that [friendBoard] leaves off this board though they are none of its entries: their time
 * is hidden, so they take no place and are not counted. A Day board lists them after its ranked rows. Called on the
 * Friends board without connections, so who is the same player is decided by the one rule that merged the rest.
 */
fun BoardPage.unplacedConnections(day: LinkedInDayView, viewerUid: String?): List<LinkedInGhost> {
    val wins = winsOf(day, viewerUid) ?: return emptyList()
    return day.ghosts().filterIndexed { index, ghost -> ghost.timeMs == null && wins.ghostKept[index] }
}

/** What [day] changes on these entries (`linkedInTimeWins`); null when it changes nothing. */
private fun BoardPage.winsOf(day: LinkedInDayView, viewerUid: String?): LinkedInTimeWins? {
    val ghosts = day.ghosts()
    val self = entries.indexOfFirst { it.uid == viewerUid }.takeIf { it >= 0 }
    val own = day.ownScore.takeIf { self != null }
    if (ghosts.isEmpty() && own == null) return null
    return linkedInTimeWins(
        rowNames = entries.map { it.displayName },
        rowScores = entries.map { BestScore(it.timeMs, it.hints) },
        selfRow = self,
        ghostNames = ghosts.map { it.name },
        ghostScores = ghosts.map { it.score() },
        own = own,
    )
}

private fun LinkedInDayView.ghosts(): List<LinkedInGhost> = (board as? LinkedInBoardView.Shown)?.ghosts.orEmpty()

private fun LinkedInGhost.score(): BestScore? = timeMs?.let { linkedInScore(it, hintFree) }
