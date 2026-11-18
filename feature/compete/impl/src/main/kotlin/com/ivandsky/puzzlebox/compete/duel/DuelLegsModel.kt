package com.ivandsky.puzzlebox.compete.duel

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.data.match.isSelf
import com.ivandsky.puzzlebox.domain.LegSeen
import com.ivandsky.puzzlebox.domain.MatchView
import com.ivandsky.puzzlebox.domain.lineup.LegName
import com.ivandsky.puzzlebox.domain.revealReady
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Duration
import java.time.Instant

private const val MINUTES_PER_HOUR = 60L
private const val SELF_NAME = "You"

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

    /** A self duel's past you: the [day] of the first solve its score comes from (null when not known), never the score. */
    data class FirstSolve(val day: String?) : OpponentLine
}

/** A result that has not settled with the server: still posting, rejected as late, or kept on this device only. */
internal enum class PostingCaption { Pending, Late, LocalOnly }

internal enum class LegAction { Start, Resume }

/**
 * One puzzle's card: [action] is null once it is done; [actionEnabled] is false for an unstarted puzzle of a settled duel.
 * A [custom] puzzle whose title is known is titled by it, with the game, its author and the code as the
 * [subtitle]; else the title is the game and code and there is no subtitle.
 */
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
    val subtitle: String? = null,
    val custom: Boolean = false,
)

/** Playing: a puzzle of mine is open. Waiting: mine are done. RevealReady: [MatchView.match]'s reveal rule says so. */
internal enum class DuelFooter { Playing, Waiting, RevealReady }

/** The puzzles screen's facts about one duel at [now]: both players, one row per puzzle and the footer. */
internal data class DuelLegsModel(
    val me: Player,
    val opponent: Player,
    val rows: List<LegRowModel>,
    val footer: DuelFooter,
    val self: Boolean = false,
)

/**
 * Maps [view] and the in-progress play times ([elapsedMs], by puzzle index) to what the screen draws. A finished
 * puzzle is the viewer's outbox row; otherwise a saved play makes it In progress, else New. The footer asks the
 * match's own reveal rule rather than restating it. A self duel names me [myName] (its stored "You" is a placeholder)
 * and shows each opponent line as the [dayOf] the puzzle's [firstSolvedAt]: past you's score is never read here.
 * [names] holds the custom puzzles' titles and authors where known; none keeps today's titles.
 */
internal fun duelLegsModel(
    view: MatchView,
    elapsedMs: Map<Int, Long>,
    now: Instant,
    nameOf: (GameId) -> String,
    rankOrderOf: (GameId) -> RankOrder,
    myName: String? = null,
    firstSolvedAt: Map<String, Instant> = emptyMap(),
    dayOf: (Instant) -> String = Instant::toString,
    names: Map<ShareCode, LegName> = emptyMap(),
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
        val gameName = nameOf(leg.ref.gameId)
        val named = (leg.ref as? LegRef.Custom)?.let { names[it.code] }
        LegRowModel(
            legIndex = index,
            gameId = leg.ref.gameId,
            title = named?.title ?: legTitle(leg.ref, gameName),
            subtitle = named?.let { legSubtitle(leg.ref as LegRef.Custom, gameName, it) },
            custom = leg.ref is LegRef.Custom,
            rankOrder = rankOrderOf(leg.ref.gameId),
            status = status,
            opponent = if (match.isSelf) firstSolve(firstSolvedAt[leg.ref.wire], dayOf) else opponentLine(view.theirs.getOrNull(index)),
            posting = outbox?.state?.caption(),
            action = when (status) {
                LegStatus.New -> LegAction.Start
                is LegStatus.InProgress -> LegAction.Resume
                is LegStatus.Solved, LegStatus.GaveUp -> null
            },
            actionEnabled = status !is LegStatus.New || (!settled && now < match.deadline),
        )
    }
    val footer = when {
        match.revealReady(view.me, view.mine, now) -> DuelFooter.RevealReady
        // A self duel has nobody to wait on or nudge: its footer is the results or nothing.
        !match.isSelf && match.legs.indices.all { it in mine } -> DuelFooter.Waiting
        else -> DuelFooter.Playing
    }
    val me = when {
        match.isSelf -> Player(view.me, myName ?: SELF_NAME, match.challenger.photoUrl)
        match.challenger.uid == view.me -> match.challenger
        else -> match.opponent ?: Player(view.me, SELF_NAME, null)
    }
    val opponent = match.opponentOf(view.me) ?: Player("", "a friend", null)
    return DuelLegsModel(me, opponent, rows, footer, self = match.isSelf)
}

private fun legTitle(ref: LegRef, gameName: String): String = when (ref) {
    is LegRef.Daily -> "$gameName #${ref.number}"
    is LegRef.Custom -> "$gameName · ${ref.code.display}"
}

private fun legSubtitle(ref: LegRef.Custom, gameName: String, name: LegName): String =
    listOfNotNull(gameName, name.author?.let { "by $it" }, ref.code.display).joinToString(" · ")

private fun opponentLine(seen: LegSeen?): OpponentLine = when (seen) {
    null, LegSeen.NotPlayed -> OpponentLine.NotPlayed
    LegSeen.Played -> OpponentLine.Sealed
    is LegSeen.Revealed -> OpponentLine.Revealed(seen.result.timeMs, seen.result.hints, seen.result.gaveUp)
}

private fun firstSolve(at: Instant?, dayOf: (Instant) -> String): OpponentLine = OpponentLine.FirstSolve(at?.let(dayOf))

private fun ResultPostState.caption(): PostingCaption? = when (this) {
    ResultPostState.Pending -> PostingCaption.Pending
    ResultPostState.Late -> PostingCaption.Late
    ResultPostState.LocalOnly -> PostingCaption.LocalOnly
    ResultPostState.Posted -> null
}

/** "17 h 42 m" until [deadline], hours dropped under one hour, "<1 m" under a minute; null once [deadline] has passed. */
private fun remaining(deadline: Instant, now: Instant): String? {
    if (isClosed(deadline, now)) return null
    val minutes = Duration.between(now, deadline).toMinutes()
    val hours = minutes / MINUTES_PER_HOUR
    return when {
        hours > 0 -> "$hours h ${minutes % MINUTES_PER_HOUR} m"
        minutes > 0 -> "$minutes m"
        else -> "<1 m"
    }
}

/** The countdown pill: "Closes in 17 h 42 m", or "Closed" once [deadline] has passed. */
internal fun timeLeftText(deadline: Instant, now: Instant): String =
    remaining(deadline, now)?.let { "Closes in $it" } ?: "Closed"

/** True once [now] reaches [at]. */
internal fun isClosed(at: Instant, now: Instant): Boolean = now >= at

/** What the Nudge button shares: who it is for, when the duel closes, and the match link. */
internal fun nudgeText(opponentName: String, code: ShareCode, deadline: Instant, now: Instant): String {
    val line = remaining(deadline, now)?.let { "Your duel with $opponentName closes in $it." }
        ?: "Your duel with $opponentName has closed."
    return "$line\n${code.matchLink}"
}
