package com.ivandsky.puzzlebox.insights.stats.grouped

import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRecords
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSection
import com.ivandsky.puzzlebox.model.GameId

/**
 * Splits the page's flat [sections] into the four groups, in order, each group holding its chart (Speed: time trend,
 * Hint-free: hint-free rate, Routine: weekday pattern, Friends: rank history) and its side block (the Records that
 * belong to it; for Friends, vs friends). A side block with nothing to list is absent, a group with neither block is
 * left out, and rank history is dropped on the all-games page ([game] null), which has no game to keep weeks for.
 *
 * Records are split by kind: Speed takes the fastest solves, Hint-free the longest run and Routine the longest streak.
 * Records that are still loading or cannot load are not split: they stay whole in the Speed group, so the note shows once.
 */
internal fun groupStatsSections(sections: List<StatsSection>, game: GameId?): List<StatsGroupBlock> {
    var records: StatsSection.Records? = null
    var trend: StatsSection? = null
    var hintFree: StatsSection? = null
    var weekday: StatsSection? = null
    var rankHistory: StatsSection? = null
    var vsFriends: StatsSection? = null
    // Exhaustive on purpose: a new StatsSection subtype must be given a group here, not silently dropped from the page.
    for (section in sections) when (section) {
        is StatsSection.Records -> records = records ?: section
        is StatsSection.TimeTrend -> trend = trend ?: section
        is StatsSection.HintFreeRate -> hintFree = hintFree ?: section
        is StatsSection.Weekday -> weekday = weekday ?: section
        is StatsSection.RankHistory -> rankHistory = rankHistory ?: section.takeIf { game != null }
        is StatsSection.VsFriends -> vsFriends = vsFriends ?: section
    }
    return listOf(
        StatsGroupBlock(StatsGroup.Speed, trend, records.sideFor(StatsGroup.Speed)),
        StatsGroupBlock(StatsGroup.HintFree, hintFree, records.sideFor(StatsGroup.HintFree)),
        StatsGroupBlock(StatsGroup.Routine, weekday, records.sideFor(StatsGroup.Routine)),
        StatsGroupBlock(StatsGroup.Friends, rankHistory, vsFriends),
    ).filter { it.main != null || it.side != null }
}

/** The part of the Records section that belongs to [group], null when it has none (or [group] holds no records). */
private fun StatsSection.Records?.sideFor(group: StatsGroup): StatsSection.Records? {
    val state = this?.state ?: return null
    if (state !is SectionState.Ready) return if (group == StatsGroup.Speed) this else null
    val part = when (group) {
        StatsGroup.Speed -> StatsRecords(state.value.fastest, longestStreak = null, longestHintFreeRun = null)
        StatsGroup.HintFree -> StatsRecords(emptyList(), longestStreak = null, longestHintFreeRun = state.value.longestHintFreeRun)
        StatsGroup.Routine -> StatsRecords(emptyList(), state.value.longestStreak, longestHintFreeRun = null)
        StatsGroup.Friends -> return null
    }
    return if (part.isEmpty) null else StatsSection.Records(SectionState.Ready(part))
}
