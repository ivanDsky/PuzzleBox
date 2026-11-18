package com.ivandsky.puzzlebox.compete.create

import com.ivandsky.puzzlebox.compete.create.lineup.LineupEditor

/** What [NewDuelViewModel] holds between emissions: the choices, a Create in flight, its notice, and the lineup being edited. */
internal data class DuelWorking(
    val draft: DuelDraft,
    val creating: Boolean = false,
    val notice: DuelNotice? = null,
    val signInRequired: Boolean = false,
    val signIn: DuelSignIn = DuelSignIn.Idle,
    val generation: Int = 0,
    val challenge: DuelChallenge? = null,
    val opponent: Opponent = Opponent.Friend,
    val form: SelfDuelForm = SelfDuelForm.quick(emptyList()),
    val lineup: LineupEditor = LineupEditor(),
    /** The lineup Customize shows for Past you, apart from the friend duel's [lineup] so each keeps its own rows. */
    val selfLineup: LineupEditor = LineupEditor(),
) {
    /** Drops the notice and the lineup rows it marked, which are the same refusal. */
    fun withoutNotice(): DuelWorking = copy(notice = null, lineup = lineup.unmarked(), selfLineup = selfLineup.unmarked())
}
