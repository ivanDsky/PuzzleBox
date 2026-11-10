package com.ivandsky.puzzlebox.compete.reveal

import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.gameui.DuelSideState
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One puzzle of the reveal: both sides as the viewer may see them, and the puzzle's verdict from the viewer's side. */
internal data class LegReveal(val gameId: GameId, val mine: DuelSideState, val theirs: DuelSideState, val verdict: LegVerdict)

internal sealed interface RevealPhase {
    data object VsIntro : RevealPhase

    data class Leg(val index: Int) : RevealPhase

    data object Score : RevealPhase

    data object End : RevealPhase
}

internal enum class Side { Mine, Theirs }

/**
 * One instant of the reveal. [myTickMs] and [theirTickMs] are the figures each ticker shows, counting up linearly from 0
 * to that side's time (0 for a side with no time, and outside a puzzle); the screen may ease them. [locked] is the
 * puzzle's winner once it has locked. [speed] is 1, or [RevealMachine.DuelSpeedUp] after a tap.
 */
internal data class RevealState(
    val phase: RevealPhase,
    val myTickMs: Long,
    val theirTickMs: Long,
    val locked: Side?,
    val speed: Int,
    val myScore: Int,
    val theirScore: Int,
)

/**
 * The reveal race's timeline: pure, with no clock of its own. The screen feeds it elapsed
 * milliseconds through [advance] and the viewer's taps; every timing rule lives here.
 *
 * Phases run [RevealPhase.VsIntro] for [IntroMs], one [RevealPhase.Leg] of [LegMs] per puzzle, then
 * [RevealPhase.Score] and [RevealPhase.End], the only terminal phase. [skip] and [reduced] motion go straight to End;
 * once there, nothing changes.
 */
internal class RevealMachine(legs: List<LegReveal>, reduced: Boolean) {
    private val races = legs.map(::raceOf)
    private val finalMine = races.count { it.winner == Side.Mine }
    private val finalTheirs = races.count { it.winner == Side.Theirs }
    private val legsEndMs = IntroMs + LegMs * races.size

    /** The instant the timeline reaches End by itself. */
    private val endMs = legsEndMs + ScoreHoldMs

    /** Virtual time on the reveal's own timeline: real milliseconds times [speed]. */
    private var elapsedMs = 0L
    private var speed = 1
    private var ended = reduced

    private val _state = MutableStateFlow(current())
    val state: StateFlow<RevealState> = _state.asStateFlow()

    fun advance(ms: Long) {
        if (ended || ms <= 0) return
        val room = endMs - elapsedMs
        elapsedMs = if (ms > room / speed) endMs else elapsedMs + ms * speed
        ended = elapsedMs >= endMs
        publish()
    }

    /** Speeds up everything remaining; sticky, so a second tap changes nothing. */
    fun tap() {
        if (ended || speed == DuelSpeedUp) return
        speed = DuelSpeedUp
        publish()
    }

    fun skip() {
        if (ended) return
        ended = true
        publish()
    }

    private fun publish() {
        _state.value = current()
    }

    private fun current(): RevealState = when {
        ended -> final(RevealPhase.End)
        elapsedMs < IntroMs -> RevealState(RevealPhase.VsIntro, 0, 0, null, speed, 0, 0)
        elapsedMs < legsEndMs -> legAt(elapsedMs - IntroMs)
        else -> final(RevealPhase.Score)
    }

    private fun final(phase: RevealPhase) = RevealState(phase, 0, 0, null, speed, finalMine, finalTheirs)

    private fun legAt(sinceIntroMs: Long): RevealState {
        val index = (sinceIntroMs / LegMs).toInt()
        val inLegMs = sinceIntroMs % LegMs
        val race = races[index]
        val earlier = races.subList(0, index)
        val locked = race.winner?.takeIf { inLegMs >= race.lockAtMs }
        return RevealState(
            phase = RevealPhase.Leg(index),
            myTickMs = race.mine?.at(inLegMs) ?: 0,
            theirTickMs = race.theirs?.at(inLegMs) ?: 0,
            locked = locked,
            speed = speed,
            myScore = earlier.count { it.winner == Side.Mine } + if (locked == Side.Mine) 1 else 0,
            theirScore = earlier.count { it.winner == Side.Theirs } + if (locked == Side.Theirs) 1 else 0,
        )
    }

    companion object {
        const val DuelSpeedUp = 4
        const val IntroMs = 600L
        const val LegMs = 3_000L
    }
}

/** How long the loser's ticker runs, and every ticker in a puzzle nobody wins. */
private const val LoserRunMs = 2_400L

/** The winner's run is `LoserRunMs × winner / loser`, clamped to this range so the faster side visibly locks first. */
private const val WinnerMinRunMs = 1_200L
private const val WinnerMaxRunMs = 1_900L

/** The run of the only side with a time, when the other has none to show. */
private const val SoloRunMs = 1_800L

/** The hold on the final score before the summary. */
private const val ScoreHoldMs = 900L

/** One ticker: counts linearly from 0 up to [toMs] over [runMs], then holds. */
private class Ticker(val toMs: Long, val runMs: Long) {
    fun at(inLegMs: Long): Long = if (inLegMs >= runMs) toMs else toMs * inLegMs / runMs
}

/** One puzzle's timeline: a ticker per side with a time, the winner by the verdict and when that winner locks. */
private class Race(val mine: Ticker?, val theirs: Ticker?, val winner: Side?, val lockAtMs: Long)

private fun raceOf(leg: LegReveal): Race {
    // The verdict decides the side, never the ticker figures: a hints win may show the slower time.
    val winner = when (leg.verdict) {
        is LegVerdict.Win -> Side.Mine
        is LegVerdict.Loss -> Side.Theirs
        LegVerdict.Draw, LegVerdict.Void, LegVerdict.Pending -> null
    }
    val mineMs = (leg.mine as? DuelSideState.Time)?.timeMs
    val theirsMs = (leg.theirs as? DuelSideState.Time)?.timeMs
    val (mineRunMs, theirsRunMs) = when {
        mineMs == null || theirsMs == null -> SoloRunMs to SoloRunMs
        winner == Side.Mine -> winnerRunMs(mineMs, theirsMs) to LoserRunMs
        winner == Side.Theirs -> LoserRunMs to winnerRunMs(theirsMs, mineMs)
        else -> LoserRunMs to LoserRunMs
    }
    val mine = mineMs?.let { Ticker(it, mineRunMs) }
    val theirs = theirsMs?.let { Ticker(it, theirsRunMs) }
    val winnerTicker = if (winner == Side.Mine) mine else theirs
    return Race(mine, theirs, winner, lockAtMs = winnerTicker?.runMs ?: SoloRunMs)
}

private fun winnerRunMs(winnerMs: Long, loserMs: Long): Long =
    if (loserMs <= 0) WinnerMaxRunMs else (LoserRunMs * winnerMs / loserMs).coerceIn(WinnerMinRunMs, WinnerMaxRunMs)
