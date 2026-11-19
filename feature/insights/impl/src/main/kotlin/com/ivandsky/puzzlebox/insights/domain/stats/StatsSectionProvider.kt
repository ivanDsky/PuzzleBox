package com.ivandsky.puzzlebox.insights.domain.stats

import kotlinx.coroutines.flow.Flow

/**
 * One section of the stats page. A local section reads the [StatsInput]'s facts and emits its section once, ready; one
 * that needs the network emits [SectionState.Loading], then its answer, and turns its own failure into
 * [SectionState.Unavailable] (A16), so the rest of the page never waits on it. A flow that emits null leaves the
 * section out: it has nothing to say.
 */
internal fun interface StatsSectionProvider {

    fun section(input: StatsInput): Flow<StatsSection?>

    /**
     * What this section's answer depends on. The page asks [section] again only when the key changes, so a section
     * that reads the network keys on what it asks for and is not re-read, nor flashes loading, when only the local
     * facts change. A local section depends on the whole [input].
     */
    fun readKey(input: StatsInput): Any = input
}
