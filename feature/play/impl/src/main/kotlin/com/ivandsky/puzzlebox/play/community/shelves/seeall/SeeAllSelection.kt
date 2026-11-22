package com.ivandsky.puzzlebox.play.community.shelves.seeall

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.community.DifficultyChoice

/** What the player has typed and picked on the page. [sort] null is the scope's own order. */
internal data class SeeAllFilters(
    val query: String = "",
    val game: GameId? = null,
    val difficulty: DifficultyChoice? = null,
    val sort: SeeAllSort? = null,
) {
    /** A query, a game or a difficulty is set; the sort is a choice of order, not a filter. */
    val narrowing: Boolean get() = query.isNotBlank() || game != null || difficulty != null
}

/** The sorts this [entries] set can be put in: Newest, and Most played once any puzzle has a solve count. */
internal fun sortOptionsOf(entries: List<SeeAllEntry>): List<SeeAllSort> =
    if (entries.any { it.solves != null }) listOf(SeeAllSort.MostPlayed, SeeAllSort.Newest) else listOf(SeeAllSort.Newest)

/** The sort a page opens on: Most played on its own page when it can be offered, else Newest. */
internal fun defaultSortOf(scope: SeeAllScope?, options: List<SeeAllSort>): SeeAllSort =
    if (scope == SeeAllScope.MostPlayed && SeeAllSort.MostPlayed in options) SeeAllSort.MostPlayed else SeeAllSort.Newest

/** [entries] of [scope] only. Friends' puzzles are already scoped by where they come from. */
internal fun List<SeeAllEntry>.inScope(scope: SeeAllScope?): List<SeeAllEntry> =
    if (scope is SeeAllScope.Game) filter { it.game == scope.game } else this

/** [this] narrowed by [filters] and ordered by [sort]; the sort is Newest or Most played, both newest-first on a tie. Puzzles without a value sort last. The list is the community's newest 30, so there is nothing to page. */
internal fun List<SeeAllEntry>.selected(filters: SeeAllFilters, sort: SeeAllSort): List<SeeAllEntry> {
    val newest = compareByDescending<SeeAllEntry, Long?>(nullsFirst()) { it.createdAtMs }
    val order = if (sort == SeeAllSort.MostPlayed) compareByDescending<SeeAllEntry, Int?>(nullsFirst()) { it.solves }.then(newest) else newest
    return narrowed(filters).sortedWith(order)
}

/** [this] narrowed by [filters], in the order it has. */
internal fun List<SeeAllEntry>.narrowed(filters: SeeAllFilters): List<SeeAllEntry> {
    val query = filters.query.trim()
    return filter { filters.game == null || it.game == filters.game }
        .filter { filters.difficulty == null || it.row.difficulty in filters.difficulty.levels }
        .filter { query.isEmpty() || it.matches(query) }
}

private fun SeeAllEntry.matches(query: String) =
    listOf(row.title, row.author, row.game).any { it.contains(query, ignoreCase = true) }
