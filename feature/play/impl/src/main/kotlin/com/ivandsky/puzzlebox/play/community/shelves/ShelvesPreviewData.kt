package com.ivandsky.puzzlebox.play.community.shelves

import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.community.GameChoice
import com.ivandsky.puzzlebox.play.domain.shelves.DEFAULT_GAME_SHELF_ORDER
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfPuzzle
import com.ivandsky.puzzlebox.play.domain.shelves.buildShelfPlan

private const val NOW = 1_800_000_000_000L
private const val HOUR = 3_600_000L
private const val DAY = 24 * HOUR

private val Choices = listOf(
    GameChoice(GameId("queens"), "Queens", GameBrand(AccentSlot.Violet, GameGlyphs.Queens)),
    GameChoice(GameId("zip"), "Zip", GameBrand(AccentSlot.Blue, GameGlyphs.Zip)),
    GameChoice(GameId("tango"), "Tango", GameBrand(AccentSlot.Orange, GameGlyphs.Tango)),
    GameChoice(GameId("minisudoku"), "Mini Sudoku", GameBrand(AccentSlot.Teal, GameGlyphs.Sudoku)),
    GameChoice(GameId("strands"), "Strands", GameBrand(AccentSlot.Pink, GameGlyphs.Strands)),
)

private val Titles = listOf("Spiral staircase", "Sun and moon", "Corner pegs", "Rainy Tuesday", "Little fox", "Night shift", "Two doors")
private val Authors = listOf("Maya", "Ben", "Ola", "Priya", "Jun")
private val Levels = listOf(Difficulty.Level.Easy, Difficulty.Level.Medium, Difficulty.Level.Hard, null)

private fun codeOf(n: Int) = checkNotNull(ShareCode.parse("P" + n.toString().padStart(5, '0')))

/**
 * Sample shelves for the previews: [count] puzzles over the five games, newest first, and the plan [buildShelfPlan] lays
 * out for them. With [counted] each has a solve count, as it will once the counter exists; [longest] gives every puzzle
 * the longest title and author.
 */
internal fun sampleShelves(count: Int, counted: Boolean = false, longest: Boolean = false): ShelvesUiState.Content {
    val sources = (1..count).map { n ->
        ShelfPuzzle(
            code = codeOf(n),
            game = Choices[n % Choices.size].id,
            createdAtMs = NOW - n * 5 * HOUR - (if (n > count / 2) 9 * DAY else 0),
            solves = if (counted) 40 - n else null,
            isFriend = n % 7 == 0,
        )
    }
    val puzzles = sources.associate { source ->
        val n = source.code.value.drop(1).toInt()
        source.code to ShelfPuzzleModel(
            title = if (longest) "The longest puzzle title a person could still call a title" else Titles[n % Titles.size],
            author = if (longest) "An author with the longest display name" else Authors[n % Authors.size],
            difficulty = Levels[n % Levels.size],
        )
    }
    val plan = buildShelfPlan(sources, DEFAULT_GAME_SHELF_ORDER, NOW)
    return ShelvesUiState.Content(plan, puzzles, Choices.associateBy { it.id }, SampleEntries)
}

/** What the entry tiles read in the previews. */
internal val SampleEntries = ShelvesEntries(friendPuzzles = 7, friendInitials = listOf('M', 'B', 'O'), published = 3, drafts = 2)
