package com.ivandsky.puzzlebox.insights.domain.stats

import app.cash.turbine.test
import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.insights.domain.recap.RecapHistoryFixture
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import java.time.LocalDate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveStatsPageTest {

    private val fixture = RecapHistoryFixture()
    private val warned = mutableListOf<String>()
    private val failures = FailureLog { _, message, _ -> warned += message }

    private fun day(dayOfMonth: Int, month: Int = 11) = LocalDate.of(2026, month, dayOfMonth)

    private val noFriends = StatsSectionProvider { flowOf(null) }

    private fun observe(providers: List<StatsSectionProvider> = statsSectionProviders(noFriends)) = ObserveStatsPage(
        observeGameHistory = fixture.observeHistory,
        computePeriodFacts = fixture.computePeriodFacts,
        calendar = fixture.calendar,
        registry = fixture.registry,
        vsFriends = noFriends,
        providers = providers,
        failures = failures,
    )

    private fun StatsPage.records(): StatsRecords {
        val section = sections.filterIsInstance<StatsSection.Records>().single()
        return (section.state as SectionState.Ready).value
    }

    @Test
    fun `no solve in the range is an empty page`() = runTest {
        observe()(scope = null, range = StatsRange.Month).test {
            assertEquals(emptyList<StatsSection>(), awaitItem().sections)
        }
    }

    @Test
    fun `records are the fastest per game in game order, the longest streak and the longest hint-free run`() = runTest {
        fixture.store(
            fixture.solve(beta, day(2), time = 50_000, puzzleNumber = 12),
            fixture.solve(alpha, day(3), time = 70_000, puzzleNumber = 13),
            fixture.solve(alpha, day(4), time = 60_000, puzzleNumber = 14, hints = 1),
        )

        observe()(scope = null, range = StatsRange.Month).test {
            val records = awaitItem().records()

            assertEquals(listOf(alpha, beta), records.fastest.map { it.game })
            assertEquals(60_000L, records.fastest.first().solve.timeMs)
            assertEquals(3, records.longestStreak?.run?.days.let { it } ?: 0)
            assertEquals(14, records.longestStreak?.solve?.puzzleNumber)
            assertEquals(2, records.longestHintFreeRun?.solves)
        }
    }

    @Test
    fun `a record the range has nothing for is absent`() = runTest {
        fixture.store(fixture.solve(alpha, day(3), hints = 1))

        observe()(scope = null, range = StatsRange.Month).test {
            assertNull(awaitItem().records().longestHintFreeRun)
        }
    }

    @Test
    fun `a range cuts the records to its days`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(1), time = 10_000),
            fixture.solve(alpha, day(18), time = 20_000),
        )

        observe()(scope = null, range = StatsRange.Week).test {
            assertEquals(20_000L, awaitItem().records().fastest.single().solve.timeMs)
        }
        observe()(scope = null, range = StatsRange.All).test {
            assertEquals(10_000L, awaitItem().records().fastest.single().solve.timeMs)
        }
    }

    @Test
    fun `a game scope reads that game alone`() = runTest {
        fixture.store(
            fixture.solve(alpha, day(3), time = 10_000),
            fixture.solve(beta, day(3), time = 5_000),
        )

        observe()(scope = alpha, range = StatsRange.Month).test {
            val page = awaitItem()

            assertEquals(alpha, page.scope)
            assertEquals(listOf(alpha), page.records().fastest.map { it.game })
        }
    }

    @Test
    fun `a new solve updates the page and the puzzle day moving on slides the week`() = runTest {
        fixture.store(fixture.solve(alpha, day(18), time = 20_000))

        observe()(scope = null, range = StatsRange.Week).test {
            assertEquals(20_000L, awaitItem().records().fastest.single().solve.timeMs)

            fixture.store(fixture.solve(alpha, day(18), time = 20_000), fixture.solve(alpha, day(19), time = 15_000))
            var page = awaitItem()
            while (page.sections.isEmpty() || page.records().fastest.single().solve.timeMs != 15_000L) page = awaitItem()

            fixture.calendar.setAll(day(27))
            while (page.sections.isNotEmpty()) page = awaitItem()
            assertEquals(emptyList<StatsSection>(), page.sections)
        }
    }

    @Test
    fun `a section that is unavailable does not block the records`() = runTest {
        fixture.store(fixture.solve(alpha, day(3)))
        val remote = StatsSectionProvider { flowOf(StatsSection.Records(SectionState.Unavailable)) }

        observe(listOf(RecordsStatsSectionProvider, remote))(scope = null, range = StatsRange.Month).test {
            val sections = awaitItem().sections

            assertEquals(2, sections.size)
            assertTrue((sections[0] as StatsSection.Records).state is SectionState.Ready)
            assertEquals(SectionState.Unavailable, (sections[1] as StatsSection.Records).state)
        }
    }

    @Test
    fun `a provider that throws is logged and left out while the records stay`() = runTest {
        fixture.store(fixture.solve(alpha, day(3)))
        val broken = StatsSectionProvider { flow { error("boom") } }
        val brokenAtOnce = StatsSectionProvider { error("boom now") }

        observe(listOf(brokenAtOnce, RecordsStatsSectionProvider, broken))(scope = null, range = StatsRange.Month).test {
            val sections = awaitItem().sections

            assertEquals(1, sections.size)
            assertEquals(2, warned.size)
        }
    }

    @Test
    fun `a section still loading is on the page beside a ready one`() = runTest {
        fixture.store(fixture.solve(alpha, day(3)))
        val slow = StatsSectionProvider { flow { emit(StatsSection.Records(SectionState.Loading)) } }

        observe(listOf(slow, RecordsStatsSectionProvider))(scope = null, range = StatsRange.Month).test {
            assertEquals(SectionState.Loading, (awaitItem().sections[0] as StatsSection.Records).state)
        }
    }
}
