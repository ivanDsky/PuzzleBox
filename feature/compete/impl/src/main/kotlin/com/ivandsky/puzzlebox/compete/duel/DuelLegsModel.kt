package com.ivandsky.puzzlebox.compete.duel

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.domain.LegSeen
import com.ivandsky.puzzlebox.domain.MatchView
import com.ivandsky.puzzlebox.domain.revealReady
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Duration
import java.time.Instant

private const val MINUTES_PER_HOUR = 60L

/** Where one of my puzzles stands: not opened, saved part-way, or finished. */
internal sealed interface LegStatus {
    data object New : LegStatus

    data class InProgress(val elapsedMs: Long) : LegStatus

    data class Solved(val timeMs: Long, val hints: Int) : LegStatus

    data object GaveUp : LegStatus
}

/** What I may know of the opponent's play of the same puzzle: their score only once the match lets me see it. */
internal sealed interface OpponentLine {
    data object NotPlayed : OpponentLine

    data object Sealed : OpponentLine

    data class Revealed(val timeMs: Long, val hints: Int, val gaveUp: Boolean) : OpponentLine
}

/** A result that has not settled with the server: still posting, rejected as late, or kept on this device only. */
internal enum class PostingCaption { Pending, Late, LocalOnly }

internal enum class LegAction { Start, Resume }

/** One puzzle's card: [action] is null once it is done; [actionEnabled] is false for an unstarted puzzle of a settled duel. */
internal data class LegRowModel(
    val legIndex: Int,
    val gameId: GameId,
    val title: String,
    val rankOrder: RankOrder,
    val status: LegStatus,
    val opponent: OpponentLine,
    val posting: PostingCaption?,
    val action: LegAction?,
    val actionEnabled: Boolean,
)

/** Playing: a puzzle of mine is open. Waiting: mine are done. RevealReady: [MatchView.match]'s reveal rule says so. */
internal enum class DuelFooter { Playing, Waiting, RevealReady }

/** The puzzles screen's facts about one duel at [now]: both players, one row per puzzle and the footer. */
internal data class DuelLegsModel(
    val me: Player,
    val opponent: Player,
    val rows: List<LegRowModel>,
    val footer: DuelFooter,
)

/**
 * Maps [view] and the in-progress play times ([elapsedMs], by puzzle index) to what the screen draws. A finished
 * puzzle is the viewer's outbox row; otherwise a saved play makes it In progress, else New. The footer asks the
 * match's own reveal rule rather than restating it.
 */
internal fun duelLegsModel(
    view: MatchView,
    elapsedMs: Map<Int, Long>,
    now: Instant,
    nameOf: (GameId) -> String,
    rankOrderOf: (GameId) -> RankOrder,
): DuelLegsModel {
    val match = view.match
    val mine = view.mine.filter { it.result.uid == view.me }.associateBy { it.result.legIndex }
    val settled = view.outcome.settled
    val rows = match.legs.mapIndexed { index, leg ->
        val outbox = mine[index]
        val status = when {
            outbox == null -> elapsedMs[index]?.let(LegStatus::InProgress) ?: LegStatus.New
            outbox.result.gaveUp -> LegStatus.GaveUp
            else -> LegStatus.Solved(outbox.result.timeMs, outbox.result.hints)
        }
        LegRowModel(
            legIndex = index,
            gameId = leg.ref.gameId,
            title = legTitle(leg.ref, nameOf(leg.ref.gameId)),
            rankOrder = rankOrderOf(leg.ref.gameId),
            status = status,
            opponent = opponentLine(view.theirs.getOrNull(index)),
            posting = outbox?.state?.caption(),
            action = when (status) {
                LegStatus.New -> LegAction.Start
                is LegStatus.InProgress -> LegAction.Resume
                is LegStatus.Solved, LegStatus.GaveUp -> null
            },
            actionEnabled = status !is LegStatus.New || !settled,
        )
    }
    val footer = when {
        match.revealReady(view.me, view.mine, now) -> DuelFooter.RevealReady
        match.legs.indices.all { it in mine } -> DuelFooter.Waiting
        else -> DuelFooter.Playing
    }
    val me = (if (match.challenger.uid == view.me) match.challenger else match.opponent) ?: Player(view.me, "You", null)
    val opponent = match.opponentOf(view.me) ?: Player("", "a friend", null)
    return DuelLegsModel(me, opponent, rows, footer)
}

private fun legTitle(ref: LegRef, gameName: String): String = when (ref) {
    is LegRef.Daily -> "$gameName #${ref.number}"
    is LegRef.Custom -> "$gameName · ${ref.code.display}"
}

private fun opponentLine(seen: LegSeen?): OpponentLine = when (seen) {
    null, LegSeen.NotPlayed -> OpponentLine.NotPlayed
    LegSeen.Played -> OpponentLine.Sealed
    is LegSeen.Revealed -> OpponentLine.Revealed(seen.result.timeMs, seen.result.hints, seen.result.gaveUp)
}

private fun ResultPostState.caption(): PostingCaption? = when (this) {
    ResultPostState.Pending -> PostingCaption.Pending
    ResultPostState.Late -> PostingCaption.Late
    ResultPostState.LocalOnly -> PostingCaption.LocalOnly
    ResultPostState.Posted -> null
}

/** "17 h 42 m" until [closesAt], hours dropped under one hour, "<1 m" under a minute; null once [closesAt] has passed. */
private fun remaining(closesAt: Instant, now: Instant): String? {
    if (isClosed(closesAt, now)) return null
    val minutes = Duration.between(now, closesAt).toMinutes()
    val hours = minutes / MINUTES_PER_HOUR
    return when {
        hours > 0 -> "$hours h ${minutes % MINUTES_PER_HOUR} m"
        minutes > 0 -> "$minutes m"
        else -> "<1 m"
    }
}

/** The countdown pill: "Closes in 17 h 42 m", or "Closed" once [closesAt] has passed. */
internal fun timeLeftText(closesAt: Instant, now: Instant): String =
    remaining(closesAt, now)?.let { "Closes in $it" } ?: "Closed"

/** True once [now] reaches [closesAt]. */
internal fun isClosed(closesAt: Instant, now: Instant): Boolean = now >= closesAt

/** What the Nudge button shares: who it is for, when the duel closes, and the match link. */
internal fun nudgeText(opponentName: String, code: ShareCode, closesAt: Instant, now: Instant): String {
    val line = remaining(closesAt, now)?.let { "Your duel with $opponentName closes in $it." }
        ?: "Your duel with $opponentName has closed."
    return "$line\n${code.matchLink}"
}
