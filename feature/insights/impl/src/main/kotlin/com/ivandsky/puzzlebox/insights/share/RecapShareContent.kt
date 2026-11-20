package com.ivandsky.puzzlebox.insights.share

import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.recap.puzzles

/**
 * What the share image and the share text say about one recap: the [title] of its period, the player's own
 * [displayName] (null when signed out), one [hero] number and the [tiles] under it. Only the player's own numbers get
 * here (A9): a card about friends has no place in it, so no friend's name or time can reach the image.
 */
internal data class RecapShareContent(
    val title: String,
    val displayName: String?,
    val hero: Stat?,
    val tiles: List<Stat>,
) {

    /** One headline number: its [value] and the [label] that names it. */
    data class Stat(val value: String, val label: String)

    companion object {
        private const val StreakLabel = "Longest streak"


        /** The headline numbers of [cards], in the story's order; a card the recap has none of adds no number. */
        fun of(title: String, displayName: String?, cards: List<RecapCard>): RecapShareContent = RecapShareContent(
            title = title,
            displayName = displayName?.takeIf { it.isNotBlank() },
            hero = cards.filterIsInstance<RecapCard.Solved>().firstOrNull()?.let { Stat(it.total.toString(), "${it.total.puzzles()} solved") },
            tiles = tilesOf(cards),
        )

        /** The tiles of [cards] in story order, with the hint-free count (it lives on Solved, the first card) right after the streak. */
        private fun tilesOf(cards: List<RecapCard>): List<Stat> {
            val tiles = cards.mapNotNull(::tile)
            val hintFree = cards.filterIsInstance<RecapCard.Solved>().firstOrNull()?.let(::hintFreeTile) ?: return tiles
            return tiles.toMutableList().apply { add(indexOfFirst { it.label == StreakLabel } + 1, hintFree) }
        }

        private fun hintFreeTile(card: RecapCard.Solved): Stat? =
            Stat("${card.hintFree} of ${card.total}", "Solved without a hint").takeIf { card.hintFree > 0 }

        private fun tile(card: RecapCard): Stat? = when (card) {
            is RecapCard.TotalTime -> Stat(formatDuration(card.totalMs), "Time spent solving")
            is RecapCard.Streak -> Stat(if (card.days == 1) "1 day" else "${card.days} days", StreakLabel)
            is RecapCard.Moments -> Stat(card.total.toString(), "Moments earned").takeIf { card.total > 0 }
            // Solved is the hero (its hint-free tile is added by tilesOf); Fastest is a list of times, no headline. A card the image does not know (a friends card) is never shown (A9).
            else -> null
        }
    }
}
