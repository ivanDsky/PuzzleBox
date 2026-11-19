package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.domain.nextthing.NextThing
import com.ivandsky.puzzlebox.play.domain.nextthing.ObserveResultNextThing
import com.ivandsky.puzzlebox.play.domain.nextthing.ResultNextThing
import com.ivandsky.puzzlebox.resultui.ResultPrimaryAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * What Result adds to a counted solve (L1, L3, L4): the [moments] it earned, the [primary] step, and the friend the
 * [challenge] row names. Empty until the first fact lands, and for good on a past day's result; [primary] is null until
 * the next thing is known.
 */
internal data class ResultNextFacts(
    val moments: List<Moment> = emptyList(),
    val primary: ResultPrimaryAction? = null,
    val challenge: ResultPrimaryAction.ChallengeFriend? = null,
)

/** The one place the domain's next thing becomes Result's primary; [gameName] is a game's display name. */
internal fun NextThing.toPrimaryAction(gameName: (GameId) -> String): ResultPrimaryAction = when (this) {
    is NextThing.NextDaily -> ResultPrimaryAction.Next(game, gameName(game))
    is NextThing.ChallengeFriend -> ResultPrimaryAction.ChallengeFriend(uid, name)
    is NextThing.MissedDay -> ResultPrimaryAction.MissedDay(game, day, gameName(game))
    is NextThing.FriendPuzzle -> ResultPrimaryAction.FriendPuzzle(code, game, authorName)
    is NextThing.CommunityPuzzle -> ResultPrimaryAction.CommunityPuzzle(code, game, authorName)
    is NextThing.DoneForToday -> ResultPrimaryAction.AllDone
}

/** One emission of the next-thing flow as Result draws it. */
internal fun ResultNextThing.toResultFacts(gameName: (GameId) -> String) = ResultNextFacts(
    moments = moments,
    primary = nextThing?.toPrimaryAction(gameName),
    challenge = challenge?.let { ResultPrimaryAction.ChallengeFriend(it.uid, it.name) },
)

/**
 * Today's [solve] of [slot] as [observe] sees it, asked once: the facts it needs first settle, then one run of the
 * use case is mapped to what Result draws.
 *
 * [dayRow] says whether the puzzle is practice (no edition number): a practice puzzle earns nothing, so its primary is
 * [practiceNext], today's next unsolved game, and nothing else is read. Otherwise the new-best flag is [live]'s
 * verdict for this solve, awaited until the record write has answered; a null [live] (no live play holds the solve,
 * as after process death) and a failed write claim no best.
 */
internal fun resultNextFacts(
    observe: ObserveResultNextThing,
    slot: PlaySlot.Daily,
    solve: ActivePlay.Solve,
    live: ActivePlay?,
    dayRow: Flow<GameHistoryDay?>,
    practiceNext: Flow<ResultPrimaryAction>,
    gameName: (GameId) -> String,
): Flow<ResultNextFacts> = flow {
    val practice = dayRow.first().let { it != null && it.puzzleNumber == null }
    if (practice) {
        emitAll(practiceNext.map { ResultNextFacts(primary = it) })
    } else {
        // A write that raced the baseline read ends stored with no verdict, so stored settles it too.
        val written = live?.record?.first { it.solve != solve || it.verdict != null || it.writeFailed || it.stored }
        val newBest = written != null && written.solve == solve && written.verdict == RecordVerdict.NewBest
        val score = BestScore(solve.timeMs, solve.hints)
        emitAll(observe(slot.gameId, slot.day, score, playAgain = false, practice = false, newBest = newBest).map { it.toResultFacts(gameName) })
    }
}
