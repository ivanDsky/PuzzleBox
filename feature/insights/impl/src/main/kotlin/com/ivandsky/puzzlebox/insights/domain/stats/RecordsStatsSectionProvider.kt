package com.ivandsky.puzzlebox.insights.domain.stats

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** The Records section, read off the facts' fastest solves, longest streak and longest hint-free run; left out when the range has none. */
internal object RecordsStatsSectionProvider : StatsSectionProvider {

    override fun section(input: StatsInput): Flow<StatsSection?> {
        val facts = input.facts
        val records = StatsRecords(
            fastest = input.inGameOrder(facts.fastestByGame).map { (game, solve) -> StatsRecords.Fastest(game, solve) },
            longestStreak = facts.longestStreak?.let { StatsRecords.Streak(it, facts.longestStreakSolve) },
            longestHintFreeRun = facts.longestHintFreeRun,
        )
        return flowOf(if (records.isEmpty) null else StatsSection.Records(SectionState.Ready(records)))
    }
}
