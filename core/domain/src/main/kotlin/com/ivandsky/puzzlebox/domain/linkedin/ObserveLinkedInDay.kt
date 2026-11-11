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
import com.ivandsky.puzzlebox.game.linkedin.rankCombined
import com.ivandsky.puzzlebox.game.linkedin.rankedCount
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
    is LinkState.Connected -> when {
        // The player's result is known but the board is not, and the sync that was to read it could not reach
        // LinkedIn: without this line the card would show a result and no board with no word on why.
        !inFlight && lastSync == LinkedInSync.Offline && boardInWindow && boardFetchedAtMs == null -> LinkedInDayStatus.Offline
        else -> when (val result = own) {
            is LinkedInOwnState.Imported -> LinkedInDayStatus.Imported(result.record.time, result.record.hints)
            LinkedInOwnState.NotPlayed -> LinkedInDayStatus.DidNotPlay
            LinkedInOwnState.Unknown -> when {
                inFlight -> LinkedInDayStatus.Loading
                lastSync == LinkedInSync.Offline -> LinkedInDayStatus.Offline
                else -> LinkedInDayStatus.Idle
            }
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
    val yours = (own as? LinkedInOwnState.Imported)?.record?.let { BestScore(it.time, it.hints) }
    val slots = rankCombined(
        rows = listOfNotNull(yours),
        ghosts = ghosts.map { ghost -> ghost.timeMs?.let { linkedInScore(it, ghost.hintFree) } },
        order = order,
    )
    val yourRank = slots.firstOrNull { !it.ghost }?.place
    return LinkedInBoardView.Shown(
        // Equal scores keep board order; a hidden time has no score and takes no rank.
        ghosts = slots.filter { it.ghost }.map { ghosts[it.index] },
        yourRank = yourRank,
        // The rank's "of N" counts the player and the connections with a time. Without a rank the line only says how
        // many connections played, and one with a hidden time did play.
        total = if (yourRank != null) slots.rankedCount() else ghosts.size,
        updatedAtMs = fetchedAtMs,
    )
}
