package com.ivandsky.puzzlebox.compete.create

import com.ivandsky.puzzlebox.compete.create.lineup.lineupSummary
import com.ivandsky.puzzlebox.data.identity.Identity
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Turns what [NewDuelViewModel] holds into what the sheet draws; the clock, zone and locale are read at each build. */
internal class NewDuelStateBuilder(
    private val clock: () -> Instant,
    private val zone: () -> ZoneId,
    private val locale: () -> Locale,
) {
    fun build(work: DuelWorking, available: List<DuelGame>, who: Identity?, counts: SolvedCounts?, lineupOn: Boolean): NewDuelState {
        val offered = work.challenge == null
        val opponent = if (offered) work.opponent else Opponent.Friend
        val self = SelfDuelView(work.form, counts, available)
        val lineup = work.lineup.takeIf { lineupOn && opponent == Opponent.Friend }
        return NewDuelState(
            draft = work.draft,
            games = available,
            summary = when {
                opponent == Opponent.PastYou -> self.summary
                // The draft, not what Create sends: after a failed try that is the drawn puzzles, which have no names.
                lineup != null && work.draft.preset == DuelPreset.Custom -> lineupSummary(
                    lineup.draft.entries, work.draft.deadlineHours, work.draft.includeFriends, clock(), zone(), locale(),
                ) { id -> available.firstOrNull { it.id == id }?.name ?: id.value }
                else -> work.draft.summary(clock(), zone(), available, locale())
            },
            invitee = work.challenge?.friendName,
            // Past you needs no account: only a friend duel meets the sign-in panel.
            step = if (opponent == Opponent.Friend && (who == null || work.signInRequired)) DuelStep.SignIn else DuelStep.Create,
            creating = work.creating,
            notice = work.notice,
            signIn = work.signIn,
            selfOffered = offered,
            opponent = opponent,
            selfName = who?.displayName ?: "You",
            self = self,
            lineup = lineup,
        )
    }
}
