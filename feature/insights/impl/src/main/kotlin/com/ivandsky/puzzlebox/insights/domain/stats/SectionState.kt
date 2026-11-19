package com.ivandsky.puzzlebox.insights.domain.stats

/**
 * Where one stats section is: still loading, ready with its [Ready.value], or unavailable (A16). A local section is
 * always [Ready]; a section that needs the network moves through the other two on its own, so one failing never blocks
 * the rest of the page.
 */
internal sealed interface SectionState<out T> {

    /** The section's read is still coming. */
    data object Loading : SectionState<Nothing>

    /** The section's [value] is ready. */
    data class Ready<out T>(val value: T) : SectionState<T>

    /** The section could not load right now ("Can't load right now"). */
    data object Unavailable : SectionState<Nothing>
}
