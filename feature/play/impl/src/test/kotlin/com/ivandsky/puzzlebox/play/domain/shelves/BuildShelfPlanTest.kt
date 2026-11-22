package com.ivandsky.puzzlebox.play.domain.shelves

import com.ivandsky.puzzlebox.play.domain.shelves.ShelfFixture.DAY
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfFixture.NOW
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfFixture.allCards
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfFixture.ids
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfFixture.order
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfFixture.puzzle
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfFixture.puzzles
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfFixture.puzzlesWithUnsolved
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfFixture.shownCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildShelfPlanTest {

    private fun plan(n: Int, counter: Boolean) = buildShelfPlan(puzzles(n, counter), order, NOW)

    private inline fun <reified T : ShelfPlan.Section> ShelfPlan.section(): T? = sections.filterIsInstance<T>().singleOrNull()

    private fun ShelfPlan.shelfCodes() = sections.filterIsInstance<ShelfPlan.Section.GameShelf>().flatMap { it.cards }.map { it.code }

    @Test
    fun `R1 every puzzle appears exactly once for N 0 1 2 3 8 13 14 30 31 with the counter on and off`() {
        for (counter in listOf(true, false)) {
            for (n in listOf(0, 1, 2, 3, 8, 13, 14, 30, 31)) {
                val shown = plan(n, counter).shownCodes()
                assertEquals("n=$n counter=$counter shown once each", puzzles(n, counter).map { it.code }.toSet(), shown.toSet())
                assertEquals("n=$n counter=$counter no repeats", shown.size, shown.toSet().size)
            }
        }
    }

    @Test
    fun `R1 a puzzle listed twice is shown once`() {
        val doubled = puzzles(5, counter = false) + puzzles(5, counter = false)

        val shown = buildShelfPlan(doubled, order, NOW).shownCodes()

        assertEquals(5, shown.size)
    }

    @Test
    fun `R3 no Most played or Newest shelf holds fewer than 3 for N 0 1 2 3 8 13 14 30 31 with the counter on and off`() {
        for (counter in listOf(true, false)) {
            for (n in listOf(0, 1, 2, 3, 8, 13, 14, 30, 31)) {
                val plan = plan(n, counter)
                plan.section<ShelfPlan.Section.MostPlayed>()?.let { assertTrue("n=$n counter=$counter most played", it.cards.size >= 3) }
                plan.section<ShelfPlan.Section.Newest>()?.let { assertTrue("n=$n counter=$counter newest", it.cards.size >= 3) }
            }
        }
    }

    @Test
    fun `R2 with the counter 30 puzzles fill Spotlight then Most played then Newest then game shelves`() {
        val plan = plan(30, counter = true)

        assertEquals(ids(29), listOf(plan.section<ShelfPlan.Section.Spotlight>()!!.card.code))
        assertEquals(ids(28, 27, 26, 25, 24, 23), plan.section<ShelfPlan.Section.MostPlayed>()!!.cards.map { it.code })
        assertEquals(ids(0, 1, 2, 3, 4, 5), plan.section<ShelfPlan.Section.Newest>()!!.cards.map { it.code })
        assertEquals(17, plan.shelfCodes().size)
        assertEquals(
            listOf("Spotlight", "MostPlayed", "Newest") + List(5) { "GameShelf" },
            plan.sections.map { it::class.simpleName },
        )
    }

    @Test
    fun `A3 without a counter there is no Spotlight and no Most played`() {
        val plan = plan(30, counter = false)

        assertNull(plan.section<ShelfPlan.Section.Spotlight>())
        assertNull(plan.section<ShelfPlan.Section.MostPlayed>())
        assertEquals(ids(0, 1, 2, 3, 4, 5), plan.section<ShelfPlan.Section.Newest>()!!.cards.map { it.code })
        assertEquals(24, plan.shelfCodes().size)
    }

    @Test
    fun `R3 a Most played and a Newest shelf left with 2 hide and release their puzzles to the game shelves`() {
        val plan = plan(3, counter = true)

        assertEquals(ids(2), listOf(plan.section<ShelfPlan.Section.Spotlight>()!!.card.code))
        assertNull(plan.section<ShelfPlan.Section.MostPlayed>())
        assertNull(plan.section<ShelfPlan.Section.Newest>())
        assertEquals(ids(0, 1).toSet(), plan.shelfCodes().toSet())
    }

    @Test
    fun `R3 without a counter 2 puzzles hide Newest and sit on their game shelves`() {
        val plan = plan(2, counter = false)

        assertNull(plan.section<ShelfPlan.Section.Newest>())
        assertEquals(ids(0, 1).toSet(), plan.shelfCodes().toSet())
    }

    @Test
    fun `R3 Newest shows at exactly 3 left after Most played`() {
        val plan = plan(10, counter = true)

        assertEquals(6, plan.section<ShelfPlan.Section.MostPlayed>()!!.cards.size)
        assertEquals(ids(0, 1, 2), plan.section<ShelfPlan.Section.Newest>()!!.cards.map { it.code })
        assertTrue(plan.sections.last() is ShelfPlan.Section.MakeOne)
    }

    @Test
    fun `R4 three or more leftovers after Newest go to the game shelves`() {
        val plan = plan(9, counter = false)

        assertEquals(6, plan.section<ShelfPlan.Section.Newest>()!!.cards.size)
        assertEquals(ids(6, 7, 8).toSet(), plan.shelfCodes().toSet())
        assertNull(plan.section<ShelfPlan.Section.MakeOne>())
    }

    @Test
    fun `R4 one or two leftovers after Newest are appended to Most played`() {
        val oneLeft = plan(14, counter = true)
        val twoLeft = plan(15, counter = true)

        assertEquals(7, oneLeft.section<ShelfPlan.Section.MostPlayed>()!!.cards.size)
        assertEquals(8, twoLeft.section<ShelfPlan.Section.MostPlayed>()!!.cards.size)
        assertEquals(ids(13, 12, 11, 10, 9, 8, 7, 6), twoLeft.section<ShelfPlan.Section.MostPlayed>()!!.cards.map { it.code })
        for (plan in listOf(oneLeft, twoLeft)) {
            assertTrue(plan.sections.last() is ShelfPlan.Section.MakeOne)
            assertTrue(plan.sections.none { it is ShelfPlan.Section.GameShelf })
        }
    }

    @Test
    fun `R4 a Newest left with 1 or 2 after Most played hands them to Most played`() {
        val plan = plan(9, counter = true)

        assertNull(plan.section<ShelfPlan.Section.Newest>())
        // Puzzle 0 was never solved, so it stays out of Most played and sits on its game shelf.
        assertEquals(ids(7, 6, 5, 4, 3, 2, 1), plan.section<ShelfPlan.Section.MostPlayed>()!!.cards.map { it.code })
        assertEquals(ids(0), plan.shelfCodes())
    }

    @Test
    fun `R4 no leftovers give one Make one row listing the games instead of game shelves`() {
        val plan = plan(13, counter = true)

        val make = plan.section<ShelfPlan.Section.MakeOne>()!!
        assertEquals(order, make.games)
        assertTrue(plan.sections.none { it is ShelfPlan.Section.GameShelf })
        assertTrue(plan.sections.last() is ShelfPlan.Section.MakeOne)
    }

    @Test
    fun `R4 an empty community is only the Make one row`() {
        val plan = plan(0, counter = true)

        assertEquals(listOf(ShelfPlan.Section.MakeOne(order)), plan.sections)
    }

    @Test
    fun `R5 game shelves follow the given order and hold only puzzles not shown above, most played first`() {
        val plan = plan(30, counter = true)

        val shelves = plan.sections.filterIsInstance<ShelfPlan.Section.GameShelf>()
        assertEquals(order, shelves.map { it.game })
        val queens = shelves.first()
        assertEquals(queens.cards.map { it.code }, queens.cards.sortedByDescending { it.solves }.map { it.code })
        assertTrue(queens.cards.all { it.game == queens.game })
        assertEquals(6, queens.inAll)
        assertEquals(ShelfPlan.Section.GameShelf.Summary.More(count = 3, inAll = 6), queens.summary)
    }

    @Test
    fun `R5 a different game order moves its shelf first`() {
        val plan = buildShelfPlan(puzzles(30, counter = true), gameShelfOrder(mostPlayedFirst = listOf(order[3])), NOW)

        assertEquals(order[3], plan.sections.filterIsInstance<ShelfPlan.Section.GameShelf>().first().game)
    }

    @Test
    fun `R5 the sub line says all above or none yet when nothing is left`() {
        val loaded = listOf(
            puzzle(0, game = order[0], solves = 9),
            puzzle(1, game = order[0], solves = 8),
            puzzle(2, game = order[0], solves = 7),
            puzzle(3, game = order[0], solves = 6),
            puzzle(4, game = order[0], solves = 5),
            puzzle(5, game = order[0], solves = 4),
            puzzle(6, game = order[0], solves = 3),
            puzzle(7, game = order[0], solves = 2),
            puzzle(8, game = order[0], solves = 1),
            puzzle(9, game = order[0], solves = 0),
            puzzle(10, game = order[1], solves = 0),
            puzzle(11, game = order[1], solves = 0),
            puzzle(12, game = order[1], solves = 0),
            puzzle(13, game = order[1], solves = 0),
            puzzle(14, game = order[1], solves = 0),
            puzzle(15, game = order[1], solves = 0),
            puzzle(16, game = order[1], solves = 0),
            puzzle(17, game = order[1], solves = 0),
            puzzle(18, game = order[1], solves = 0),
            puzzle(19, game = order[2], solves = 0),
        )

        val plan = buildShelfPlan(loaded, order, NOW)

        val shelves = plan.sections.filterIsInstance<ShelfPlan.Section.GameShelf>().associateBy { it.game }
        for (game in order) assertTrue("$game shelf exists", game in shelves)
        for (shelf in shelves.values) assertEquals(shelf.cards.size, shelf.cards.map { it.code }.toSet().size)
        val strands = shelves.getValue(order[4])
        assertEquals(ShelfPlan.Section.GameShelf.Summary.None, strands.summary)
        assertEquals(emptyList<Any>(), strands.cards)
        assertTrue(shelves.values.any { it.summary is ShelfPlan.Section.GameShelf.Summary.AllAbove })
    }

    @Test
    fun `R5 a game missing from the order still gets its puzzles shown once, after the listed shelves`() {
        val plan = buildShelfPlan(puzzles(30, counter = false), order.take(3), NOW)

        assertEquals(30, plan.shownCodes().toSet().size)
        assertEquals(order.take(3) + order.drop(3), plan.sections.filterIsInstance<ShelfPlan.Section.GameShelf>().map { it.game })
    }

    @Test
    fun `A4 a puzzle is NEW under 7 days old`() {
        val loaded = listOf(
            puzzle(0, ageMs = 7 * DAY - 1),
            puzzle(1, ageMs = 7 * DAY),
        )

        val cards = buildShelfPlan(loaded, order, NOW).allCards()

        assertEquals(true, cards.single { it.code == ShelfFixture.code(0) }.isNew)
        assertEquals(false, cards.single { it.code == ShelfFixture.code(1) }.isNew)
    }

    @Test
    fun `R6 friend and own puzzles flow through the shelves with their origin`() {
        val loaded = listOf(
            puzzle(0, friend = true),
            puzzle(1, own = true),
            puzzle(2),
        )

        val cards = buildShelfPlan(loaded, order, NOW).allCards()

        assertEquals(ShelfPlan.Card.Origin.Friend, cards.single { it.code == ShelfFixture.code(0) }.origin)
        assertEquals(ShelfPlan.Card.Origin.You, cards.single { it.code == ShelfFixture.code(1) }.origin)
        assertEquals(ShelfPlan.Card.Origin.Community, cards.single { it.code == ShelfFixture.code(2) }.origin)
    }

    @Test
    fun `A3 a counter on some puzzles counts the others as unsolved`() {
        val loaded = List(8) { puzzle(it, solves = if (it == 5) 4 else null) }

        val plan = buildShelfPlan(loaded, order, NOW)

        assertEquals(ids(5), listOf(plan.section<ShelfPlan.Section.Spotlight>()!!.card.code))
        assertEquals(loaded.map { it.code }.toSet(), plan.shownCodes().toSet())
    }

    private val tableSizes = listOf(0, 1, 2, 3, 8, 13, 14, 30, 31)

    @Test
    fun `S2 puzzles with unsolved ones appear exactly once and no Most played or Newest shelf holds fewer than 3`() {
        val inputs = tableSizes.map { it to puzzlesWithUnsolved(it) } + tableSizes.map { n ->
            n to List(n) { puzzle(it, game = order[it % order.size], solves = 0) }
        }
        for ((n, loaded) in inputs) {
            val plan = buildShelfPlan(loaded, order, NOW)
            val shown = plan.shownCodes()
            assertEquals("n=$n once each", loaded.map { it.code }.toSet(), shown.toSet())
            assertEquals("n=$n no repeats", shown.size, shown.toSet().size)
            plan.section<ShelfPlan.Section.MostPlayed>()?.let { assertTrue("n=$n most played", it.cards.size >= 3) }
            plan.section<ShelfPlan.Section.Newest>()?.let { assertTrue("n=$n newest", it.cards.size >= 3) }
        }
    }

    @Test
    fun `A3 Spotlight and Most played hold only puzzles solved at least once`() {
        for (n in tableSizes) {
            val plan = buildShelfPlan(puzzlesWithUnsolved(n), order, NOW)
            val ranked = listOfNotNull(plan.section<ShelfPlan.Section.Spotlight>()?.card) +
                plan.section<ShelfPlan.Section.MostPlayed>()?.cards.orEmpty()
            assertTrue("n=$n", ranked.all { (it.solves ?: 0) >= 1 })
        }
    }

    @Test
    fun `A3 a counter where no puzzle was solved gives no Spotlight and no Most played`() {
        val loaded = List(8) { puzzle(it, game = order[it % order.size], solves = 0) }

        val plan = buildShelfPlan(loaded, order, NOW)

        assertNull(plan.section<ShelfPlan.Section.Spotlight>())
        assertNull(plan.section<ShelfPlan.Section.MostPlayed>())
        assertEquals(ids(0, 1, 2, 3, 4, 5), plan.section<ShelfPlan.Section.Newest>()!!.cards.map { it.code })
        assertEquals(ids(6, 7).toSet(), plan.shelfCodes().toSet())
    }

    @Test
    fun `A3 one solved puzzle is the Spotlight, Most played hides and the unsolved ones fill Newest`() {
        val loaded = List(8) { puzzle(it, game = order[it % order.size], solves = if (it == 5) 3 else 0) }

        val plan = buildShelfPlan(loaded, order, NOW)

        assertEquals(ids(5), listOf(plan.section<ShelfPlan.Section.Spotlight>()!!.card.code))
        assertNull(plan.section<ShelfPlan.Section.MostPlayed>())
        assertEquals(ids(0, 1, 2, 3, 4, 6), plan.section<ShelfPlan.Section.Newest>()!!.cards.map { it.code })
        assertEquals(ids(7), plan.shelfCodes())
    }

    @Test
    fun `R3 fewer than 3 solved puzzles after the Spotlight hide Most played and release them`() {
        // Two solved puzzles (7, 8): 7 is the Spotlight and 8 alone cannot fill Most played, so it goes on to the game shelves.
        val loaded = List(9) { puzzle(it, game = order[it % order.size], solves = if (it >= 7) 7 - (it - 7) else 0) }

        val plan = buildShelfPlan(loaded, order, NOW)

        assertEquals(ids(7), listOf(plan.section<ShelfPlan.Section.Spotlight>()!!.card.code))
        assertNull(plan.section<ShelfPlan.Section.MostPlayed>())
        assertEquals(ids(0, 1, 2, 3, 4, 5), plan.section<ShelfPlan.Section.Newest>()!!.cards.map { it.code })
        assertEquals(ids(6, 8).toSet(), plan.shelfCodes().toSet())
    }

    @Test
    fun `A3 Most played shows with 3 solved puzzles and leaves the unsolved to Newest`() {
        val loaded = List(10) { puzzle(it, game = order[it % order.size], solves = if (it >= 6) 10 - it else 0) }

        val plan = buildShelfPlan(loaded, order, NOW)

        assertEquals(ids(6), listOf(plan.section<ShelfPlan.Section.Spotlight>()!!.card.code))
        assertEquals(ids(7, 8, 9), plan.section<ShelfPlan.Section.MostPlayed>()!!.cards.map { it.code })
        assertEquals(ids(0, 1, 2, 3, 4, 5), plan.section<ShelfPlan.Section.Newest>()!!.cards.map { it.code })
        assertTrue(plan.sections.last() is ShelfPlan.Section.MakeOne)
    }

    @Test
    fun `R4 leftovers appended to Most played are only the solved ones`() {
        // Spotlight 14 solves; Most played 6; Newest takes 0..5; leftovers are 6 (never solved) and 7 (solved).
        val loaded = List(15) { puzzle(it, game = order[it % order.size], solves = if (it == 6) 0 else it) }

        val plan = buildShelfPlan(loaded, order, NOW)

        assertEquals(ids(13, 12, 11, 10, 9, 8, 7), plan.section<ShelfPlan.Section.MostPlayed>()!!.cards.map { it.code })
        assertEquals(ids(6), plan.shelfCodes())
    }
}
