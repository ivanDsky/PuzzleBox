package com.ivandsky.puzzlebox.play.domain.nextthing

/** One rung of the next-thing chain (L3): it offers its next thing, or nothing. */
internal interface NextThingProvider {

    /** Its place in the chain: a lower key is asked earlier. Keys leave gaps so a new provider fits between two. */
    val order: Int

    /** The next thing for [facts], or null for nothing to offer. It may be slow or throw; the chain bounds both. */
    suspend fun offer(facts: NextThingFacts): NextThing?
}
