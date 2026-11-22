package com.ivandsky.puzzlebox.insights.stats.grouped

import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRecords
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSection
import com.ivandsky.puzzlebox.insights.stats.StatsFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The split of the page's flat sections into the four groups, and of the Records section into one per group. */
class StatsGroupSplitTest {

    private val trend = StatsSection.TimeTrend(SectionState.Ready(StatsFixtures.trend))
    private val hintFree = StatsSection.HintFreeRate(SectionState.Ready(StatsFixtures.hintFreeRate))
    private val weekday = StatsSection.Weekday(SectionState.Ready(StatsFixtures.weekday))
    private val rankHistory = StatsSection.RankHistory(SectionState.Ready(StatsFixtures.rankHistory))
    private val vsFriends = StatsSection.VsFriends(SectionState.Ready(StatsFixtures.vsFriends))
    private val records = StatsSection.Records(SectionState.Ready(StatsFixtures.records))

    private val all = listOf(trend, records, hintFree, weekday, rankHistory, vsFriends)

    private fun recordsOf(block: StatsGroupBlock): StatsRecords = ((block.side as StatsSection.Records).state as SectionState.Ready).value

    @Test
    fun `the groups come in the owner's order, a chart main with a side block each`() {
        val blocks = groupStatsSections(all, StatsFixtures.zip)

        assertEquals(listOf(StatsGroup.Speed, StatsGroup.HintFree, StatsGroup.Routine, StatsGroup.Friends), blocks.map { it.group })
        assertEquals(trend, blocks[0].main)
        assertEquals(hintFree, blocks[1].main)
        assertEquals(weekday, blocks[2].main)
        assertEquals(rankHistory, blocks[3].main)
        assertEquals(vsFriends, blocks[3].side)
    }

    @Test
    fun `records split one kind to a group`() {
        val blocks = groupStatsSections(all, StatsFixtures.zip)

        val speed = recordsOf(blocks[0])
        assertEquals(StatsFixtures.records.fastest, speed.fastest)
        assertNull(speed.longestStreak)
        assertNull(speed.longestHintFreeRun)

        val hint = recordsOf(blocks[1])
        assertEquals(emptyList<StatsRecords.Fastest>(), hint.fastest)
        assertNull(hint.longestStreak)
        assertEquals(StatsFixtures.records.longestHintFreeRun, hint.longestHintFreeRun)

        val routine = recordsOf(blocks[2])
        assertEquals(emptyList<StatsRecords.Fastest>(), routine.fastest)
        assertEquals(StatsFixtures.records.longestStreak, routine.longestStreak)
        assertNull(routine.longestHintFreeRun)
    }

    @Test
    fun `a record the scope has none of leaves its side block out`() {
        val noStreak = StatsSection.Records(SectionState.Ready(StatsFixtures.records.copy(longestStreak = null)))

        val blocks = groupStatsSections(listOf(trend, noStreak, hintFree, weekday), null)

        assertNull(blocks.single { it.group == StatsGroup.Routine }.side)
        assertEquals(weekday, blocks.single { it.group == StatsGroup.Routine }.main)
    }

    @Test
    fun `a group with nothing in it is not listed`() {
        val blocks = groupStatsSections(listOf(trend), null)

        assertEquals(listOf(StatsGroup.Speed), blocks.map { it.group })
    }

    @Test
    fun `no sections make no groups`() {
        assertEquals(emptyList<StatsGroupBlock>(), groupStatsSections(emptyList(), null))
    }

    @Test
    fun `rank history is hidden on any game`() {
        val blocks = groupStatsSections(all, game = null)

        val friends = blocks.single { it.group == StatsGroup.Friends }
        assertNull(friends.main)
        assertEquals(vsFriends, friends.side)
    }

    @Test
    fun `records that load or cannot load stay whole in the Speed group`() {
        val unavailable = StatsSection.Records(SectionState.Unavailable)

        val blocks = groupStatsSections(listOf(trend, unavailable, hintFree), null)

        assertEquals(unavailable, blocks[0].side)
        assertNull(blocks[1].side)
    }
}
