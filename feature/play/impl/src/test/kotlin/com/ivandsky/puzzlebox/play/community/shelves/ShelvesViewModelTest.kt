package com.ivandsky.puzzlebox.play.community.shelves

import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.play.community.ALPHA
import com.ivandsky.puzzlebox.play.community.BETA
import com.ivandsky.puzzlebox.play.community.HOUR
import com.ivandsky.puzzlebox.play.community.NOW
import com.ivandsky.puzzlebox.play.community.NO_EDITOR
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfPlan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The shelves home's view model over the real use cases and the fakes: one test per state, then what each part of a state holds. */
class ShelvesViewModelTest {

    private val fixture = ShelvesFixture()

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun ShelvesViewModel.content() = state.value as ShelvesUiState.Content

    private fun ShelfPlan.codes() = sections.flatMap { section ->
        when (section) {
            is ShelfPlan.Section.Spotlight -> listOf(section.card)
            is ShelfPlan.Section.MostPlayed -> section.cards
            is ShelfPlan.Section.Newest -> section.cards
            is ShelfPlan.Section.GameShelf -> section.cards
            is ShelfPlan.Section.MakeOne -> emptyList()
        }
    }.map { it.code.value }

    @Test
    fun `it is Loading until the list answers`() = runTest {
        fixture.publishMany(3)
        fixture.gated.gate = CompletableDeferred()

        val viewModel = fixture.viewModel()
        assertEquals(ShelvesUiState.Loading, viewModel.state.value)

        fixture.gated.gate?.complete(Unit)
        assertTrue(viewModel.state.value is ShelvesUiState.Content)
    }

    @Test
    fun `a real community of thirty is Content with every puzzle on exactly one card`() = runTest {
        fixture.publishMany(20, ALPHA)
        fixture.publishMany(10, BETA, from = 21)

        val viewModel = fixture.viewModel()

        val codes = viewModel.content().plan.codes()
        assertEquals(30, codes.size)
        assertEquals(30, codes.toSet().size)
    }

    @Test
    fun `a few puzzles of eight are Content too`() = runTest {
        fixture.publishMany(8)

        val codes = fixture.viewModel().content().plan.codes()

        assertEquals(8, codes.toSet().size)
    }

    @Test
    fun `it asks for the newest puzzles of any age, with no paging`() = runTest {
        fixture.publish(1, NOW - 40 * 24 * HOUR)
        fixture.publish(2, NOW - 3 * HOUR)

        val viewModel = fixture.viewModel()

        assertEquals(listOf(CommunityFilter(windowMs = null)), fixture.gated.filters)
        assertEquals(setOf(codeOf(1).value, codeOf(2).value), viewModel.content().plan.codes().toSet())
    }

    @Test
    fun `a community with no public puzzle is Empty`() = runTest {
        assertTrue(fixture.viewModel().state.value is ShelvesUiState.Empty)
    }

    @Test
    fun `a puzzle of a game nobody can make is left out`() = runTest {
        fixture.publish(1, NOW - HOUR)
        fixture.publish(2, NOW - 2 * HOUR, game = NO_EDITOR)

        assertEquals(listOf(codeOf(1).value), fixture.viewModel().content().plan.codes())
    }

    @Test
    fun `offline is Offline and Retry reloads once the network is back`() = runTest {
        fixture.publishMany(3)
        fixture.shared.online = false
        val viewModel = fixture.viewModel()
        assertTrue(viewModel.state.value is ShelvesUiState.Offline)

        fixture.shared.online = true
        viewModel.retry()

        assertTrue(viewModel.state.value is ShelvesUiState.Content)
    }

    @Test
    fun `a refused read is Refused and Retry reloads`() = runTest {
        fixture.publishMany(3)
        fixture.shared.listRefused = true
        val viewModel = fixture.viewModel()
        assertTrue(viewModel.state.value is ShelvesUiState.Refused)

        fixture.shared.listRefused = false
        viewModel.retry()

        assertTrue(viewModel.state.value is ShelvesUiState.Content)
    }

    @Test
    fun `Retry shows Loading while the list is asked again`() = runTest {
        fixture.shared.listRefused = true
        val viewModel = fixture.viewModel()
        fixture.shared.listRefused = false
        fixture.gated.gate = CompletableDeferred()

        viewModel.retry()

        assertEquals(ShelvesUiState.Loading, viewModel.state.value)
    }

    @Test
    fun `Offline and Refused still carry the saved friends and the player's own puzzles`() = runTest {
        fixture.receive(1, author = "Bo")
        fixture.myPuzzle(2)
        fixture.draft("d1")
        fixture.shared.online = false

        val offline = fixture.viewModel().state.value as ShelvesUiState.Offline

        assertEquals(ShelvesEntries(friendPuzzles = 1, friendInitials = listOf('B'), published = 1, drafts = 1), offline.entries)
    }

    @Test
    fun `the entry tiles count friends' puzzles, published puzzles and drafts still being made`() = runTest {
        fixture.publishMany(3)
        fixture.receive(101, author = "Bo")
        fixture.receive(102, author = "Cy")
        fixture.receive(103, author = "Bo")
        fixture.myPuzzle(201)
        fixture.myPuzzle(202, unpublished = true)
        fixture.draft("open")
        fixture.draft("kept-as-published", reservedCode = codeOf(201))

        val entries = fixture.viewModel().content().entries

        assertEquals(3, entries.friendPuzzles)
        assertEquals(listOf('B', 'C'), entries.friendInitials)
        assertEquals(1, entries.published)
        assertEquals(1, entries.drafts)
    }

    @Test
    fun `a community puzzle the player received from a friend wears the Friend origin`() = runTest {
        fixture.publishMany(3)
        fixture.receive(2)

        val cards = fixture.viewModel().content().plan.sections.filterIsInstance<ShelfPlan.Section.Newest>().single().cards

        assertEquals(
            mapOf(codeOf(1) to ShelfPlan.Card.Origin.Community, codeOf(2) to ShelfPlan.Card.Origin.Friend, codeOf(3) to ShelfPlan.Card.Origin.Community),
            cards.associate { it.code to it.origin },
        )
    }

    @Test
    fun `a card finds its title, author, difficulty and game by code`() = runTest {
        fixture.publish(1, NOW - HOUR, game = BETA, difficulty = Level.Hard, title = "Castle")

        val content = fixture.viewModel().content()

        val puzzle = content.puzzles.getValue(codeOf(1))
        assertEquals("Castle", puzzle.title)
        assertEquals("Other", puzzle.author)
        assertEquals(Level.Hard, puzzle.difficulty)
        assertEquals("Beta", content.games.getValue(BETA).name)
    }

    @Test
    fun `without a solve counter there is no Spotlight and no Most played`() = runTest {
        fixture.publishMany(10)

        val sections = fixture.viewModel().content().plan.sections

        assertFalse(sections.any { it is ShelfPlan.Section.Spotlight || it is ShelfPlan.Section.MostPlayed })
        assertTrue(sections.any { it is ShelfPlan.Section.Newest })
    }

    @Test
    fun `the game shelves start with the game the player solved most`() = runTest {
        fixture.publish(1, NOW - HOUR, game = ALPHA)
        fixture.publish(2, NOW - 2 * HOUR, game = BETA)
        fixture.solved(BETA, 1)
        fixture.solved(BETA, 2)
        fixture.solved(ALPHA, 3)

        val shelves = fixture.viewModel().content().plan.sections.filterIsInstance<ShelfPlan.Section.GameShelf>()

        assertEquals(listOf(BETA, ALPHA), shelves.map { it.game })
    }

    @Test
    fun `a player with no history gets the games in the registry's order`() = runTest {
        fixture.publish(1, NOW - HOUR, game = BETA)
        fixture.publish(2, NOW - 2 * HOUR, game = ALPHA)

        val shelves = fixture.viewModel().content().plan.sections.filterIsInstance<ShelfPlan.Section.GameShelf>()

        assertEquals(listOf(ALPHA, BETA), shelves.map { it.game })
    }
}
