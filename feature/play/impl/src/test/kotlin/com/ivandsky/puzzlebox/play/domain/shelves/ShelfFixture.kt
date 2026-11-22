package com.ivandsky.puzzlebox.play.domain.shelves

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode

/** Loaded-puzzle fixtures for the shelf plan tests; puzzle i is i hours old and, with a counter, solved i times. */
internal object ShelfFixture {
    const val NOW = 1_800_000_000_000L
    const val HOUR = 3_600_000L
    const val DAY = 24 * HOUR

    val queens = GameId("queens")
    val zip = GameId("zip")
    val tango = GameId("tango")
    val miniSudoku = GameId("minisudoku")
    val strands = GameId("strands")
    val order = listOf(queens, zip, tango, miniSudoku, strands)

    fun code(i: Int): ShareCode = checkNotNull(ShareCode.parse("A%05d".format(i)))

    /**
     * [n] puzzles spread over the five games. Puzzle i is i hours old, so puzzle 0 is the newest; with [counter] it
     * has i solves, so the highest id is the most played and the newest ones are the least played (the two orders never agree).
     */
    fun puzzles(n: Int, counter: Boolean): List<ShelfPuzzle> = List(n) { i ->
        puzzle(i, game = order[i % order.size], solves = if (counter) i else null)
    }

    /** Like [puzzles] with a counter, but every third puzzle (0, 3, 6, ...) was never solved. */
    fun puzzlesWithUnsolved(n: Int): List<ShelfPuzzle> = List(n) { i ->
        puzzle(i, game = order[i % order.size], solves = if (i % 3 == 0) 0 else i)
    }

    fun puzzle(
        i: Int,
        game: GameId = queens,
        solves: Int? = null,
        ageMs: Long = i * HOUR,
        friend: Boolean = false,
        own: Boolean = false,
    ) = ShelfPuzzle(
        code = code(i),
        game = game,
        createdAtMs = NOW - ageMs,
        solves = solves,
        isFriend = friend,
        isOwn = own,
    )

    fun ShelfPlan.allCards(): List<ShelfPlan.Card> = sections.flatMap { section ->
        when (section) {
            is ShelfPlan.Section.Spotlight -> listOf(section.card)
            is ShelfPlan.Section.MostPlayed -> section.cards
            is ShelfPlan.Section.Newest -> section.cards
            is ShelfPlan.Section.GameShelf -> section.cards
            is ShelfPlan.Section.MakeOne -> emptyList()
        }
    }

    fun ShelfPlan.shownCodes(): List<ShareCode> = allCards().map { it.code }

    fun ids(vararg i: Int): List<ShareCode> = i.map(::code)
}
