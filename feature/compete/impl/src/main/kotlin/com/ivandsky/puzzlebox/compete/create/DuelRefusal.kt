package com.ivandsky.puzzlebox.compete.create

import com.ivandsky.puzzlebox.domain.CreateOutcome
import com.ivandsky.puzzlebox.model.GameId

/** The state [outcome] leaves after a Create for the friend duel: creating off, and the notice (and marked lineup row) a refusal gives; [CreateOutcome.Created] leaves it as it is. */
internal fun DuelWorking.after(outcome: CreateOutcome, gameName: (GameId) -> String): DuelWorking = when (outcome) {
    is CreateOutcome.Created -> this
    CreateOutcome.SignedOut -> copy(creating = false, signInRequired = true)
    CreateOutcome.Offline -> copy(creating = false, notice = DuelNotice.Offline)
    CreateOutcome.DailyCapReached -> copy(creating = false, notice = DuelNotice.Busy)
    CreateOutcome.NoPuzzles -> copy(creating = false, notice = DuelNotice.NoPuzzles)
    is CreateOutcome.Failed -> copy(creating = false, notice = DuelNotice.Failed(outcome.message))
    is CreateOutcome.EntryUnavailable -> copy(
        creating = false,
        notice = DuelNotice.EntryUnavailable(outcome.position),
        lineup = lineup.refused(outcome.position),
    )
    is CreateOutcome.EntryUndrawable -> copy(
        creating = false,
        notice = DuelNotice.EntryUndrawable(outcome.position, gameName(outcome.gameId)),
        lineup = lineup.refused(outcome.position),
    )
}

/** The same for the Past you duel: a refusal keeps the player's lineup, marks its row and says it in Past you's words; the only other outcomes are no puzzles, or a failure. */
internal fun DuelWorking.afterSelf(outcome: CreateOutcome?, gameName: (GameId) -> String): DuelWorking = when (outcome) {
    is CreateOutcome.Created -> this
    null -> copy(creating = false)
    CreateOutcome.NoPuzzles -> copy(creating = false, notice = DuelNotice.NoPuzzles)
    is CreateOutcome.EntryUnavailable -> copy(
        creating = false,
        notice = DuelNotice.EntryUnavailable(outcome.position, pastYou = true),
        selfLineup = selfLineup.refused(outcome.position),
    )
    is CreateOutcome.EntryUndrawable -> copy(
        creating = false,
        notice = DuelNotice.EntryUndrawable(outcome.position, gameName(outcome.gameId), pastYou = true),
        selfLineup = selfLineup.refused(outcome.position),
    )
    else -> copy(creating = false, notice = DuelNotice.Failed(null))
}
