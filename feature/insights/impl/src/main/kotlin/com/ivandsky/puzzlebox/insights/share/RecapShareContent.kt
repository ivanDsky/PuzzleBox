package com.ivandsky.puzzlebox.insights.share

import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.recap.formatHeroDuration
import com.ivandsky.puzzlebox.insights.recap.puzzles

/**
 * What the share image and the share text say about one recap: the [title] of its period, the player's own
 * [displayName] (null when signed out), one [hero] number, each game's share of the solves in [split] and the [tiles]
 * under it. Only the player's own numbers get here (A9): a card about friends has no place in it, so no friend's name
 * or time can reach the image.
 */
internal data class RecapShareContent(
    val title: String,
    val displayName: String?,
    val hero: Stat?,
    val split: List<RecapCard.Solved.PerGame>,
    val tiles: List<Stat>,
) {

    /** One headline number: its [value] and the [label] that names it; a tile also names the recap [card] whose colours it wears. */
    data class Stat(val value: String, val label: String, val card: PaletteCard? = null)

    /** What a screen reader says for the image, with final values: "Share image: October 2026, Ivan. 96 puzzles solved. Longest streak 23 days. …". */
    fun description(): String = buildString {
        append("Share image: ").append(title)
        displayName?.let { append(", ").append(it) }
        append('.')
        hero?.let { append(' ').append(it.value).append(' ').append(it.label).append('.') }
        tiles.forEach { append(' ').append(it.label).append(' ').append(it.value).append('.') }
    }

    /** The numbers as they read [progress] of the way through their count-up: each run of digits scaled, whole at 1. */
    fun counted(progress: Float): RecapShareContent {
        if (progress >= 1f) return this
        fun Stat.counted() = copy(value = DIGITS.replace(value) { ((it.value.toLong()) * progress.coerceAtLeast(0f)).toLong().toString() })
        return copy(hero = hero?.counted(), tiles = tiles.map { it.counted() })
    }

    companion object {

        private val DIGITS = Regex("\\d+")

        /** The headline numbers of [cards]; a card the recap has none of adds no number. */
        fun of(title: String, displayName: String?, cards: List<RecapCard>): RecapShareContent {
            val solved = cards.filterIsInstance<RecapCard.Solved>().firstOrNull()
            return RecapShareContent(
                title = title,
                displayName = displayName?.takeIf { it.isNotBlank() },
                hero = solved?.let { Stat(it.total.toString(), "${it.total.puzzles()} solved") },
                split = solved?.perGame.orEmpty(),
                tiles = if (solved?.total == 1) emptyList() else tilesOf(solved, cards),
            )
        }

        /** The tiles in their fixed order: streak, solved without a hint, moments, time. The order is the poster's, not the cards'. */
        private fun tilesOf(solved: RecapCard.Solved?, cards: List<RecapCard>): List<Stat> {
            val streak = cards.filterIsInstance<RecapCard.Streak>().firstOrNull()
            val moments = cards.filterIsInstance<RecapCard.Moments>().firstOrNull()
            val time = cards.filterIsInstance<RecapCard.TotalTime>().firstOrNull()
            return listOfNotNull(
                streak?.let { Stat(if (it.days == 1) "1 day" else "${it.days} days", "Longest streak", PaletteCard.Streak) },
                solved?.takeIf { it.hintFree > 0 }?.let { Stat("${it.hintFree} of ${it.total}", "Solved without a hint", PaletteCard.Solved) },
                moments?.takeIf { it.total > 0 }?.let { Stat(it.total.toString(), "Moments earned", PaletteCard.Moments) },
                time?.let { Stat(formatHeroDuration(it.totalMs), "Time spent solving", PaletteCard.TotalTime) },
            )
        }
    }
}
