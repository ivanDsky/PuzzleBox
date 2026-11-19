package com.ivandsky.puzzlebox.insights.domain.stats

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.insights.domain.recap.FriendsOverallRead
import com.ivandsky.puzzlebox.insights.domain.recap.PeriodFacts
import com.ivandsky.puzzlebox.insights.domain.recap.RecapHistoryFixture
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import java.time.LocalDate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The facts of a stored history over a stats range, on the puzzle day 2026-11-20 (a Friday), for the series tests. */
internal class StatsSeriesFixture {
    val history = RecapHistoryFixture()
    val today: LocalDate = LocalDate.of(2026, 11, 20)

    fun day(dayOfMonth: Int, month: Int = 11): LocalDate = LocalDate.of(2026, month, dayOfMonth)

    suspend fun facts(range: StatsRange, scope: GameId? = null): PeriodFacts {
        val all = history.history()
        val firstSolve = all.filter { scope == null || it.gameId == scope }.mapNotNull { if (it.record == null) null else it.day }.minOrNull()
        return history.computePeriodFacts(all, range.span(today, firstSolve), scope)
    }

    /** The vs friends provider over scripted reads; [viewer] null is signed out. */
    fun vsFriends(
        viewer: Identity? = Identity(uid = "me", displayName = "Me", photoUrl = null),
        timeout: Duration = 15.seconds,
        warnings: MutableList<String> = mutableListOf(),
        friendsOverall: FriendsOverallRead,
    ) = VsFriendsStatsSectionProvider(
        identity = FakeIdentityService(viewer),
        friendsOverall = friendsOverall,
        failures = FailureLog { _, message, _ -> warnings += message },
        timeout = timeout,
    )

    fun input(facts: PeriodFacts, range: StatsRange, games: List<GameId> = emptyList()) = StatsInput(range, facts, games, today)
}
