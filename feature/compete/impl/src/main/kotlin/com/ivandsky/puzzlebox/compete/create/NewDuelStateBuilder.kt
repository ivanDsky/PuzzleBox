package com.ivandsky.puzzlebox.compete.create

import com.ivandsky.puzzlebox.compete.create.lineup.lineupSummary
import com.ivandsky.puzzlebox.compete.create.lineup.pastYouLineupSummary
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Turns what [NewDuelViewModel] holds into what New duel draws; the clock, zone and locale are read at each build.
 * Past you carries the signed-in player's photo.
 */
internal class NewDuelStateBuilder(
    private val clock: () -> Instant,
    private val zone: () -> ZoneId,
    private val locale: () -> Locale,
) {
    fun build(work: DuelWorking, available: List<DuelGame>, who: Identity?, counts: SolvedCounts?): NewDuelState {
        val offered = work.challenge == null
        val opponent = if (offered) work.opponent else Opponent.Friend
        val self = SelfDuelView(work.form, counts, available)
        val lineup = if (opponent == Opponent.PastYou) work.selfLineup else work.lineup
        val nameOf = { id: GameId -> available.firstOrNull { it.id == id }?.name ?: id.value }
        return NewDuelState(
            draft = work.draft,
            games = available,
            summary = when {
                opponent == Opponent.PastYou ->
                    if (work.form.preset == DuelPreset.Custom) pastYouLineupSummary(lineup.draft.entries, work.form.includeFriends, nameOf) else self.summary
                // The draft, not what Create sends: after a failed try that is the drawn puzzles, which have no names.
                work.draft.preset == DuelPreset.Custom || work.draft.showsRows(lineup, available) -> lineupSummary(
                    lineup.draft.entries, work.draft.deadlineHours, work.draft.includeFriends, clock(), zone(), locale(), nameOf,
                )
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
            selfPhotoUrl = who?.photoUrl,
            self = self,
            lineup = lineup,
        )
    }
}
