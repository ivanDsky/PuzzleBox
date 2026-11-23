package com.ivandsky.puzzlebox.play.community.shelves.seeall

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.community.DifficultyChoice

/** What the player has typed and picked on the page. */
internal data class SeeAllFilters(
    val query: String = "",
    val game: GameId? = null,
    val difficulty: DifficultyChoice? = null,
) {
    /** A query, a game or a difficulty is set. */
    val narrowing: Boolean get() = query.isNotBlank() || game != null || difficulty != null
}

/** [entries] of [scope] only. Friends' puzzles are already scoped by where they come from. */
internal fun List<SeeAllEntry>.inScope(scope: SeeAllScope?): List<SeeAllEntry> =
    if (scope is SeeAllScope.Game) filter { it.game == scope.game } else this

/** [this] narrowed by [filters] and ordered newest first. The list is the community's newest 30, so there is nothing to page. */
internal fun List<SeeAllEntry>.selected(filters: SeeAllFilters): List<SeeAllEntry> =
    narrowed(filters).sortedWith(compareByDescending<SeeAllEntry, Long?>(nullsFirst()) { it.createdAtMs })

/** [this] narrowed by [filters], in the order it has. */
internal fun List<SeeAllEntry>.narrowed(filters: SeeAllFilters): List<SeeAllEntry> {
    val query = filters.query.trim()
    return filter { filters.game == null || it.game == filters.game }
        .filter { filters.difficulty == null || it.row.difficulty in filters.difficulty.levels }
        .filter { query.isEmpty() || it.matches(query) }
}

private fun SeeAllEntry.matches(query: String) =
    listOf(row.title, row.author, row.game).any { it.contains(query, ignoreCase = true) }
