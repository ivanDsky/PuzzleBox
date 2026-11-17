package com.ivandsky.puzzlebox.compete.duel

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.data.match.SelfSeats
import com.ivandsky.puzzlebox.domain.LegSeen
import com.ivandsky.puzzlebox.domain.MatchView
import com.ivandsky.puzzlebox.domain.Outcome
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant

/** The duels the page's tablet tests and goldens show: [self] against past you, or [open] against Sam, over the same [refs]. */
internal object DuelPageFixtures {
    val NOW: Instant = Instant.parse("2026-11-09T12:00:00Z")
    val CODE = checkNotNull(ShareCode.parse("ABC234"))
    private val FIRST_SOLVE = Instant.parse("2026-10-21T09:00:00Z")
    private val TIMES = listOf(21_000L, 72_000L, 134_000L)

    /** A self duel over [refs] with the puzzles in [doneLegs] played; puzzle 1, when not done, is saved 32 s in. */
    fun self(refs: List<LegRef>, doneLegs: Set<Int>): DuelUiState.Participant {
        val legs = refs.map { MatchLeg(it, authorUid = null) }
        val match = match(legs, Player(SelfSeats.ME, "You", null), Player(SelfSeats.PAST, SelfSeats.PAST_NAME, null), SelfSeats.NO_DEADLINE_HOURS)
            .copy(progress = mapOf(SelfSeats.PAST to legs.indices.toSet(), SelfSeats.ME to doneLegs))
        val view = view(match, SelfSeats.ME, doneLegs, legs.map { LegSeen.Played })
        return DuelUiState.Participant(
            view = view,
            elapsedMs = if (1 in doneLegs) emptyMap() else mapOf(1 to 32_000L),
            firstSolvedAt = legs.associate { it.ref.wire to FIRST_SOLVE },
            myName = "Ivan",
        )
    }

    /** A duel with Sam, who has played nothing; the puzzles in [doneLegs] are mine. */
    fun open(refs: List<LegRef>, doneLegs: Set<Int>): DuelUiState.Participant {
        val legs = refs.map { MatchLeg(it, authorUid = "sam") }
        val match = match(legs, Player("sam", "Sam", null), Player("me", "Ivan", null), 24)
        return DuelUiState.Participant(view(match, "me", doneLegs, legs.map { LegSeen.NotPlayed }), emptyMap())
    }

    private fun match(legs: List<MatchLeg>, challenger: Player, opponent: Player, hours: Int) = Match(
        code = CODE,
        formatVersion = Match.FORMAT_VERSION,
        createdAt = NOW,
        deadlineHours = hours,
        challenger = challenger,
        invitedUid = null,
        opponent = opponent,
        legs = legs,
        includeFriends = false,
        cancelled = false,
        declined = false,
        rematchOf = null,
        progress = emptyMap(),
    )

    private fun view(match: Match, me: String, doneLegs: Set<Int>, theirs: List<LegSeen>): MatchView {
        val mine = doneLegs.map {
            OutboxResult(CODE, LegResult(me, it, TIMES[it % TIMES.size], 0, completedAtMs = 1, gaveUp = false), ResultPostState.Posted, 0)
        }
        return MatchView(match, me, mine, theirs, Outcome(emptyList(), 0, 0, settled = false, verdict = null), local = null)
    }
}
