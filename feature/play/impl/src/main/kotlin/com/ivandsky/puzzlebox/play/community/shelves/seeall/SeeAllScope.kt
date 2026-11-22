package com.ivandsky.puzzlebox.play.community.shelves.seeall

import com.ivandsky.puzzlebox.model.GameId

/** What a See all page lists: a shelf's whole set, one game's puzzles, the friends' puzzles, or everything for Search. */
internal sealed interface SeeAllScope {
    data object MostPlayed : SeeAllScope
    data object Newest : SeeAllScope
    data object Friends : SeeAllScope
    data object Search : SeeAllScope
    data class Game(val game: GameId) : SeeAllScope
}
