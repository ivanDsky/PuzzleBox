package com.ivandsky.puzzlebox.play.community.shelves.seeall

import com.ivandsky.puzzlebox.play.community.GameChoice

/** The page's title: the shelf's own name, or the game's name on a game's page. */
internal fun SeeAllScope?.title(games: List<GameChoice>): String = when (this) {
    SeeAllScope.MostPlayed -> "Most played"
    SeeAllScope.Newest -> "Newest"
    SeeAllScope.Friends -> "From friends"
    SeeAllScope.Search, null -> "Search"
    is SeeAllScope.Game -> games.firstOrNull { it.id == game }?.name ?: "Puzzles"
}
