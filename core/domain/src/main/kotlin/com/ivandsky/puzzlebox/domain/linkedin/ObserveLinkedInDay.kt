package com.ivandsky.puzzlebox.domain.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDayData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDays
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwnState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInSync
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.linkedInScore
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * One game day's LinkedIn data as the day card shows it. Every decision about what to show is
 * made here, so the screens only render it. Reads what the phone already holds: observing
 * never sends a call.
 */
class ObserveLinkedInDay(private val days: LinkedInDays, private val registry: GameRegistry) {

    operator fun invoke(game: GameId, day: LocalDate): Flow<LinkedInDayView> =
        days.observe(game, day).map { it.toView(game, registry) }
}

private fun LinkedInDayData.toView(game: GameId, registry: GameRegistry): LinkedInDayView =
    LinkedInDayView(status(), board(registry.rankOrderOf(game).comparator), owner)

private fun LinkedInDayData.status(): LinkedInDayStatus = when (val linked = link) {
    LinkState.NotConnected -> LinkedInDayStatus.NotConnected
    // A pause outranks an imported result: the status line is where the player finds Resume or
    // Reconnect, and the import still shows through the card's own completion state.
    is LinkState.Paused -> when (linked.kind) {
        LinkedInRead.Rejected.Kind.SessionExpired -> LinkedInDayStatus.SessionExpired
        else -> LinkedInDayStatus.Paused(linked.kind)
    }
    is LinkState.Connected -> when (val result = own) {
        is LinkedInOwnState.Imported -> LinkedInDayStatus.Imported(result.record.time, result.record.hints)
        LinkedInOwnState.NotPlayed -> LinkedInDayStatus.DidNotPlay
        LinkedInOwnState.Unknown -> when {
            inFlight -> LinkedInDayStatus.Loading
            lastSync == LinkedInSync.Offline -> LinkedInDayStatus.Offline
            lastSync == LinkedInSync.OverBudget -> LinkedInDayStatus.OverBudget
            else -> LinkedInDayStatus.Idle
        }
    }
}

private fun LinkedInDayData.board(order: Comparator<BestScore>): LinkedInBoardView {
    val fetchedAtMs = boardFetchedAtMs
    return when {
        link is LinkState.NotConnected -> LinkedInBoardView.Hidden
        // Only a board that was read is drawn, so an empty one that was read still shows.
        fetchedAtMs == null -> LinkedInBoardView.Hidden
        !boardInWindow -> LinkedInBoardView.OutsideWindow
        // A paused link lands here too: the ghosts read before the pause are still on the phone.
        else -> shown(order, fetchedAtMs)
    }
}

private fun LinkedInDayData.shown(
    order: Comparator<BestScore>,
    fetchedAtMs: Long,
): LinkedInBoardView.Shown {
    val timed = ghosts.mapNotNull { ghost -> ghost.timeMs?.let { ghost to linkedInScore(it, ghost.hintFree) } }
    val yours = (own as? LinkedInOwnState.Imported)?.record?.let { BestScore(it.time, it.hints) }
    // Only a strictly better ghost is ahead, so equal scores share a rank.
    val yourRank = yours?.let { mine -> 1 + timed.count { (_, score) -> order.compare(score, mine) < 0 } }
    return LinkedInBoardView.Shown(
        // The sort is stable, so equal scores keep board order; a hidden time has no score and takes no rank.
        ghosts = timed.sortedWith(compareBy(order) { it.second }).map { it.first } + ghosts.filter { it.timeMs == null },
        yourRank = yourRank,
        total = ghosts.size + if (yourRank != null) 1 else 0,
        updatedAtMs = fetchedAtMs,
    )
}
