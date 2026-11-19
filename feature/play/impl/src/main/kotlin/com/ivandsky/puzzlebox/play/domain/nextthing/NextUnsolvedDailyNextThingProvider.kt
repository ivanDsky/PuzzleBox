package com.ivandsky.puzzlebox.play.domain.nextthing

import com.ivandsky.puzzlebox.domain.nextUnsolved
import com.ivandsky.puzzlebox.domain.nextthing.NextThing

/** Rung 1: today's next unsolved game after the one just solved, by the same rule as Result's "Next: <game>". */
internal object NextUnsolvedDailyNextThingProvider : NextThingProvider {
    override val order = 10

    override suspend fun offer(facts: NextThingFacts): NextThing? =
        facts.todaySet.nextUnsolved(after = facts.game)?.let(NextThing::NextDaily)
}
