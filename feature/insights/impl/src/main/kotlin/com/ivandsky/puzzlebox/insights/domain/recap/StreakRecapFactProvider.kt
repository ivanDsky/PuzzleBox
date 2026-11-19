package com.ivandsky.puzzlebox.insights.domain.recap

/** The Longest streak card: the longest streak inside the period and the freezes spent in it. No streak day in the period, no card. */
internal object StreakRecapFactProvider : RecapFactProvider {

    override suspend fun cards(input: RecapInput): List<RecapCard> {
        val streak = input.facts.longestStreak ?: return emptyList()
        return listOf(
            RecapCard.Streak(days = streak.days, from = streak.start, to = streak.end, freezesSpent = input.facts.freezesSpent),
        )
    }
}
