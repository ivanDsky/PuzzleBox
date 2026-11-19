package com.ivandsky.puzzlebox.play.domain.nextthing

/**
 * Rung 3: the most recent of the last [DAYS_BACK] days, today excluded, whose puzzle of the game just solved
 * is on the phone with no record. A history row without a record is such a captured day.
 */
internal object MissedDayNextThingProvider : NextThingProvider {
    override val order = 30

    private const val DAYS_BACK = 6L

    override suspend fun offer(facts: NextThingFacts): NextThing? {
        val week = facts.today.minusDays(DAYS_BACK)..facts.today.minusDays(1)
        return facts.history.asSequence()
            .filter { it.gameId == facts.game && it.record == null && it.day in week }
            .maxByOrNull { it.day }
            ?.let { NextThing.MissedDay(it.gameId, it.day) }
    }
}
