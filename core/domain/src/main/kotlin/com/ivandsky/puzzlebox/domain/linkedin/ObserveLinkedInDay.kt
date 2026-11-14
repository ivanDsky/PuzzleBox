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
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * One game day's LinkedIn data as the day card and the boards show it. Every decision about what to show is
 * made here, so the screens only render it. Reads what the phone already holds: observing
 * never sends a call.
 */
class ObserveLinkedInDay(private val days: LinkedInDays, private val registry: GameRegistry) {

    operator fun invoke(game: GameId, day: LocalDate): Flow<LinkedInDayView> =
        days.observe(game, day).map { it.toView(game, registry) }

    /** Every day of [game] from [from] to [to], both included, each as [invoke] shows it, from one observation. */
    fun range(game: GameId, from: LocalDate, to: LocalDate): Flow<Map<LocalDate, LinkedInDayView>> =
        days.observeRange(game, from, to).map { byDay -> byDay.mapValues { (_, day) -> day.toView(game, registry) } }
}

private fun LinkedInDayData.toView(game: GameId, registry: GameRegistry): LinkedInDayView =
    LinkedInDayView(status(), board(registry.rankOrderOf(game).comparator), owner, saved())

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

/**
 * A saved board needs no link and no recent day, so the window only tells a day that can still be read from one that
 * never will be, and only when no board is saved.
 */
private fun LinkedInDayData.saved(): SavedBoardState {
    val readAtMs = boardFetchedAtMs
    return when {
        readAtMs != null -> SavedBoardState.Saved(readAtMs, boardComplete)
        boardInWindow -> SavedBoardState.NotYetRead
        else -> SavedBoardState.NeverSaved
    }
}

private fun LinkedInDayData.board(order: Comparator<BestScore>): LinkedInBoardView = when {
    // Only a board that was read is drawn, so an empty one that was read still shows. Whatever the link says, a
    // disconnected or paused player and a day past the window still have the board that was read.
    boardFetchedAtMs == null -> LinkedInBoardView.Hidden
    else -> shown(order)
}

private fun LinkedInDayData.shown(order: Comparator<BestScore>): LinkedInBoardView.Shown {
    // Equal scores keep board order; a hidden time has no score and takes no rank, so it goes last. The player's own
    // row does not move a connection relative to another, so only the connections are ordered here.
    val slots = rankCombined(
        rows = emptyList(),
        ghosts = ghosts.map { ghost -> ghost.timeMs?.let { linkedInScore(it, ghost.hintFree) } },
        order = order,
    )
    return LinkedInBoardView.Shown(ghosts = slots.map { ghosts[it.index] })
}
