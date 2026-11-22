package com.ivandsky.puzzlebox.play.domain.shelves

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * The community home as an ordered list of [sections], top to bottom, built by [buildShelfPlan]. Every loaded
 * public puzzle sits on exactly one card of one section; [Section.MakeOne] carries none.
 */
data class ShelfPlan(val sections: List<Section>) {

    /** What a card needs to draw a puzzle: no title or author, the screen looks those up by [code]. */
    data class Card(
        val code: ShareCode,
        val game: GameId,
        /** Null while there is no solve counter. */
        val solves: Int?,
        /** Created under seven days ago. */
        val isNew: Boolean,
        val origin: Origin,
    ) {
        enum class Origin { Community, Friend, You }
    }

    sealed interface Section {
        /** The single most played puzzle. */
        data class Spotlight(val card: Card) : Section

        /** Up to six most played puzzles, plus one or two leftovers ([buildShelfPlan] R4). */
        data class MostPlayed(val cards: List<Card>) : Section

        /** Up to six newest puzzles of any age. */
        data class Newest(val cards: List<Card>) : Section

        /**
         * [game]'s shelf: [cards] are only the puzzles not shown above, [inAll] counts every loaded puzzle of the game.
         */
        data class GameShelf(val game: GameId, val cards: List<Card>, val inAll: Int) : Section {
            val summary: Summary
                get() = when {
                    cards.isNotEmpty() -> Summary.More(cards.size, inAll)
                    inAll > 0 -> Summary.AllAbove(inAll)
                    else -> Summary.None
                }

            /** The shelf's sub line: "N more · M in all", "all M are above" or "none yet · be the first". */
            sealed interface Summary {
                data class More(val count: Int, val inAll: Int) : Summary
                data class AllAbove(val inAll: Int) : Summary
                data object None : Summary
            }
        }

        /** The five make cards in [games] order, in place of the game shelves when no puzzle is left for them. */
        data class MakeOne(val games: List<GameId>) : Section
    }
}
