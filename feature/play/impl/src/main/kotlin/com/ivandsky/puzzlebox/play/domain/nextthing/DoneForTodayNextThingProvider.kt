package com.ivandsky.puzzlebox.play.domain.nextthing

/** The last rung: it always answers, so the chain ends on the countdown to the next set. */
internal object DoneForTodayNextThingProvider : NextThingProvider {
    override val order = 50

    override suspend fun offer(facts: NextThingFacts): NextThing? = NextThing.DoneForToday(facts.nextRollover)
}
