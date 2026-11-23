package com.ivandsky.puzzlebox.play.community.shelves.seeall

import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.play.community.ALPHA
import com.ivandsky.puzzlebox.play.community.BETA
import com.ivandsky.puzzlebox.play.community.DifficultyChoice
import com.ivandsky.puzzlebox.play.community.NOW
import com.ivandsky.puzzlebox.play.community.NO_EDITOR
import com.ivandsky.puzzlebox.play.community.shelves.codeOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The See all page's view model over the real use cases and the fakes: scope, search, filters, the newest-first order and the no-match state. */
class SeeAllViewModelTest {

    private val fixture = SeeAllFixture()

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun SeeAllViewModel.codes() = (state.value.phase as SeeAllPhase.Ready).rows.map { it.code.value }

    private fun SeeAllViewModel.opened(scope: SeeAllScope) = apply { open(scope) }

    private fun code(n: Int) = codeOf(n).value

    @Test
    fun `it is Loading until the list answers`() = runTest {
        val viewModel = fixture.viewModel()
        assertEquals(SeeAllPhase.Loading, viewModel.state.value.phase)
        fixture.publish(1)

        viewModel.open(SeeAllScope.Newest)

        assertTrue(viewModel.state.value.phase is SeeAllPhase.Ready)
    }

    @Test
    fun `Newest lists every puzzle of a game with an editor, newest first, and says how many`() = runTest {
        fixture.publish(1, agoMs = 3_000)
        fixture.publish(2, agoMs = 1_000)
        fixture.publish(3, agoMs = 2_000, game = BETA)
        fixture.publish(4, agoMs = 500, game = NO_EDITOR)

        val viewModel = fixture.viewModel().opened(SeeAllScope.Newest)

        assertEquals(listOf(code(2), code(3), code(1)), viewModel.codes())
        assertEquals(3, (viewModel.state.value.phase as SeeAllPhase.Ready).rows.size)
    }

    @Test
    fun `a game's page lists only that game and has no Any game filter`() = runTest {
        fixture.publish(1)
        fixture.publish(2, game = BETA)

        val viewModel = fixture.viewModel().opened(SeeAllScope.Game(BETA))

        assertEquals(listOf(code(2)), viewModel.codes())
        assertFalse(viewModel.state.value.showGameFilter)
    }

    @Test
    fun `every other page has the Any game filter, with a choice per game that has an editor`() = runTest {
        fixture.publish(1)

        listOf(SeeAllScope.Newest, SeeAllScope.MostPlayed, SeeAllScope.Friends, SeeAllScope.Search).forEach { scope ->
            val viewModel = fixture.viewModel().opened(scope)
            assertTrue("$scope", viewModel.state.value.showGameFilter)
            assertEquals(listOf(ALPHA, BETA), viewModel.state.value.games.map { it.id })
        }
    }

    @Test
    fun `Search is every puzzle, and From friends is the puzzles friends sent`() = runTest {
        fixture.publish(1)
        fixture.publish(2)
        fixture.receive(2, title = "Puzzle 2", author = "Bo")
        fixture.receive(9, title = "Old friend", author = "Cy")

        assertEquals(listOf(code(1), code(2)), fixture.viewModel().opened(SeeAllScope.Search).codes())
        assertEquals(setOf(code(2), code(9)), fixture.viewModel().opened(SeeAllScope.Friends).codes().toSet())
    }

    @Test
    fun `From friends still lists the friends' puzzles while the community list is offline`() = runTest {
        fixture.receive(9, title = "Old friend", author = "Cy")
        fixture.shared.online = false

        val viewModel = fixture.viewModel().opened(SeeAllScope.Friends)

        assertEquals(listOf(code(9)), viewModel.codes())
    }

    @Test
    fun `a friend's puzzle in the community list keeps its difficulty and age`() = runTest {
        fixture.publish(2, agoMs = 5 * 60_000L, difficulty = Level.Hard)
        fixture.receive(2)

        val row = (fixture.viewModel().opened(SeeAllScope.Friends).state.value.phase as SeeAllPhase.Ready).rows.single()

        assertEquals(Level.Hard, row.difficulty)
        assertEquals("5 min ago", row.age)
    }

    @Test
    fun `search matches the title, the author and the game name in any case`() = runTest {
        fixture.publish(1, title = "Sunny Zip", author = "Ana")
        fixture.publish(2, title = "Night", author = "Zed")
        fixture.publish(3, title = "Plain", author = "Bo", game = BETA)
        val viewModel = fixture.viewModel().opened(SeeAllScope.Search)

        viewModel.setQuery("zip")
        assertEquals(listOf(code(1)), viewModel.codes())
        viewModel.setQuery("ZED")
        assertEquals(listOf(code(2)), viewModel.codes())
        viewModel.setQuery("  ")
        assertEquals(3, viewModel.codes().size)
        viewModel.setQuery(viewModel.state.value.games.first { it.id == BETA }.name.lowercase())
        assertEquals(listOf(code(3)), viewModel.codes())
    }

    @Test
    fun `search only looks inside the page's own scope`() = runTest {
        fixture.publish(1, title = "Sunny")
        fixture.publish(2, title = "Sunny", game = BETA)
        val viewModel = fixture.viewModel().opened(SeeAllScope.Game(BETA))

        viewModel.setQuery("sunny")

        assertEquals(listOf(code(2)), viewModel.codes())
    }

    @Test
    fun `the game filter narrows a page that has it`() = runTest {
        fixture.publish(1)
        fixture.publish(2, game = BETA)
        val viewModel = fixture.viewModel().opened(SeeAllScope.Newest)

        viewModel.setGame(BETA)
        assertEquals(listOf(code(2)), viewModel.codes())
        viewModel.setGame(null)
        assertEquals(2, viewModel.codes().size)
    }

    @Test
    fun `the difficulty filter narrows by level, and Hard also takes Expert`() = runTest {
        fixture.publish(1, difficulty = Level.Easy)
        fixture.publish(2, difficulty = Level.Hard)
        fixture.publish(3, difficulty = Level.Expert)
        fixture.publish(4)
        val viewModel = fixture.viewModel().opened(SeeAllScope.Newest)

        viewModel.setDifficulty(DifficultyChoice.Easy)
        assertEquals(listOf(code(1)), viewModel.codes())
        viewModel.setDifficulty(DifficultyChoice.Hard)
        assertEquals(listOf(code(2), code(3)), viewModel.codes())
        viewModel.setDifficulty(null)
        assertEquals(4, viewModel.codes().size)
    }

    @Test
    fun `search, game and difficulty narrow together`() = runTest {
        fixture.publish(1, title = "Sunny", difficulty = Level.Easy)
        fixture.publish(2, title = "Sunny", difficulty = Level.Hard)
        fixture.publish(3, title = "Sunny", difficulty = Level.Easy, game = BETA)
        val viewModel = fixture.viewModel().opened(SeeAllScope.Search)

        viewModel.setQuery("sunny")
        viewModel.setGame(ALPHA)
        viewModel.setDifficulty(DifficultyChoice.Easy)

        assertEquals(listOf(code(1)), viewModel.codes())
    }

    @Test
    fun `every page lists newest first, even when puzzles carry solve counts`() = runTest {
        fixture.publish(1, agoMs = 1_000, solves = 2)
        fixture.publish(2, agoMs = 2_000, solves = 9)
        fixture.publish(3, agoMs = 3_000)

        listOf(SeeAllScope.MostPlayed, SeeAllScope.Newest, SeeAllScope.Search, SeeAllScope.Game(ALPHA)).forEach { scope ->
            assertEquals(scope.toString(), listOf(code(1), code(2), code(3)), fixture.viewModel().opened(scope).codes())
        }
    }

    @Test
    fun `a query that matches nothing is the no-match state, not an empty page`() = runTest {
        fixture.publish(1, title = "Sunny")
        val viewModel = fixture.viewModel().opened(SeeAllScope.Search)

        viewModel.setQuery("xyz")

        assertTrue(viewModel.state.value.noMatch)
        assertTrue(viewModel.codes().isEmpty())
        assertEquals("xyz", viewModel.state.value.query)
    }

    @Test
    fun `a page with no puzzles and nothing set is empty, not no-match`() = runTest {
        val viewModel = fixture.viewModel().opened(SeeAllScope.Newest)

        assertFalse(viewModel.state.value.noMatch)
        assertTrue(viewModel.codes().isEmpty())
    }

    @Test
    fun `Clear filters drops the query, the game and the difficulty`() = runTest {
        fixture.publish(1, title = "Sunny", solves = 1)
        fixture.publish(2, title = "Night", game = BETA, difficulty = Level.Easy)
        val viewModel = fixture.viewModel().opened(SeeAllScope.Search)
        viewModel.setQuery("zzz")
        viewModel.setGame(BETA)
        viewModel.setDifficulty(DifficultyChoice.Hard)
        assertTrue(viewModel.state.value.noMatch)

        viewModel.clearFilters()

        val state = viewModel.state.value
        assertEquals("", state.query)
        assertNull(state.game)
        assertNull(state.difficulty)
        assertFalse(state.noMatch)
        assertEquals(2, viewModel.codes().size)
    }

    @Test
    fun `opening a page starts it clean`() = runTest {
        fixture.publish(1, title = "Sunny")
        val viewModel = fixture.viewModel().opened(SeeAllScope.Search)
        viewModel.setQuery("sun")
        viewModel.setDifficulty(DifficultyChoice.Easy)

        viewModel.open(SeeAllScope.Newest)

        assertEquals("", viewModel.state.value.query)
        assertNull(viewModel.state.value.difficulty)
        assertEquals(SeeAllScope.Newest, viewModel.state.value.scope)
    }

    @Test
    fun `opening the same page keeps its filters`() = runTest {
        fixture.publish(1, title = "Sunny")
        val viewModel = fixture.viewModel().opened(SeeAllScope.Search)
        viewModel.setQuery("sun")
        viewModel.setDifficulty(DifficultyChoice.Easy)

        viewModel.open(SeeAllScope.Search)

        assertEquals("sun", viewModel.state.value.query)
        assertEquals(DifficultyChoice.Easy, viewModel.state.value.difficulty)
    }

    @Test
    fun `opening a different scope resets what was typed`() = runTest {
        fixture.publish(1, title = "Sunny")
        val viewModel = fixture.viewModel().opened(SeeAllScope.Search)
        viewModel.setQuery("sun")

        viewModel.open(SeeAllScope.Game(ALPHA))

        assertEquals("", viewModel.state.value.query)
    }

    @Test
    fun `opening the same scope on a new visit, after the page closed, starts clean`() = runTest {
        fixture.publish(1, title = "Sunny")
        val viewModel = fixture.viewModel()
        viewModel.open(SeeAllScope.Search, visit = 0)
        viewModel.setQuery("sun")

        viewModel.open(SeeAllScope.Search, visit = 1)

        assertEquals("", viewModel.state.value.query)
    }

    @Test
    fun `From friends keeps the friends' own order`() = runTest {
        // Friend 2 is also in the community list and newest there, yet the friends' order (newest opened first) has 9 first.
        fixture.publish(2, agoMs = 1_000)
        fixture.receive(9, title = "Old friend", openedAtMs = NOW)
        fixture.receive(2, title = "Puzzle 2", openedAtMs = NOW - 60_000)

        val viewModel = fixture.viewModel().opened(SeeAllScope.Friends)

        assertEquals(listOf(code(9), code(2)), viewModel.codes())
    }

    @Test
    fun `it never shows more than thirty`() = runTest {
        repeat(35) { fixture.publish(it + 1) }

        assertEquals(30, fixture.viewModel().opened(SeeAllScope.Search).codes().size)
    }

    @Test
    fun `an offline or refused list is that state, and Retry reads again`() = runTest {
        fixture.publish(1)
        fixture.shared.online = false
        val viewModel = fixture.viewModel().opened(SeeAllScope.Newest)
        assertEquals(SeeAllPhase.Offline, viewModel.state.value.phase)

        fixture.shared.online = true
        fixture.shared.listRefused = true
        viewModel.retry()
        assertEquals(SeeAllPhase.Refused, viewModel.state.value.phase)

        fixture.shared.listRefused = false
        viewModel.retry()
        assertEquals(listOf(code(1)), viewModel.codes())
    }

    @Test
    fun `a reported row drops at once, stays hidden and says so`() = runTest {
        fixture.publish(1)
        fixture.publish(2)
        val viewModel = fixture.viewModel().opened(SeeAllScope.Newest)

        viewModel.report(codeOf(1))

        assertEquals(listOf(code(2)), viewModel.codes())
        assertEquals("Reported. It's hidden on this device.", viewModel.state.value.notice?.message)
    }

    @Test
    fun `a signed-out report asks to sign in and hides nothing`() = runTest {
        val signedOut = SeeAllFixture(signedIn = false)
        signedOut.publish(1)
        val viewModel = signedOut.viewModel().opened(SeeAllScope.Newest)

        viewModel.report(codeOf(1))

        assertTrue(viewModel.state.value.signInPrompt)
        assertEquals(listOf(code(1)), viewModel.codes())
    }

    @Test
    fun `a puzzle reported on the home leaves a page that already read the list`() = runTest {
        fixture.publish(1)
        fixture.publish(2)
        val viewModel = fixture.viewModel().opened(SeeAllScope.Newest)

        fixture.hidden.add(codeOf(1))

        assertEquals(listOf(code(2)), viewModel.codes())
    }

    @Test
    fun `a friend being removed leaves From friends at once, and Undo brings it back`() = runTest {
        fixture.receive(8, author = "Bo")
        fixture.receive(9, author = "Cy")
        val viewModel = fixture.viewModel().opened(SeeAllScope.Friends)

        viewModel.friendsRemoved(setOf(codeOf(9)))
        assertEquals(listOf(code(8)), viewModel.codes())

        viewModel.friendsRemoved(emptySet())
        assertEquals(setOf(code(8), code(9)), viewModel.codes().toSet())
    }
}
