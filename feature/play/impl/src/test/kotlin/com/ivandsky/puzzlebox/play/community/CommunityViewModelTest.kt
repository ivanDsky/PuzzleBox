package com.ivandsky.puzzlebox.play.community

import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
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

/** New today's view model over the real use cases and the fake repository: what loads, what a chip reloads, what Report does. */
class CommunityViewModelTest {

    private val fixture = CommunityFixture()

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun CommunityViewModel.rows() = (state.value.phase as CommunityPhase.Loaded).rows.map { it.code.value }

    @Test
    fun `it lists the last day's puzzles newest first with age, game and difficulty`() = runTest {
        fixture.publish("AAA111", NOW - 3 * HOUR, difficulty = Level.Hard)
        fixture.publish("BBB222", NOW - 5 * MINUTE, game = BETA)
        fixture.publish("CCC333", NOW - 25 * HOUR)

        val viewModel = fixture.viewModel()

        assertEquals(listOf("BBB222", "AAA111"), viewModel.rows())
        val rows = (viewModel.state.value.phase as CommunityPhase.Loaded).rows
        assertEquals(listOf("5 min ago", "3 h ago"), rows.map { it.age })
        assertEquals(listOf(null, Level.Hard), rows.map { it.difficulty })
    }

    @Test
    fun `the game chips are the games with an editor, and a puzzle of any other game is left out`() = runTest {
        fixture.publish("AAA111", NOW - HOUR)
        fixture.publish("BBB222", NOW - 2 * HOUR, game = NO_EDITOR)

        val viewModel = fixture.viewModel()

        assertEquals(listOf(ALPHA, BETA), viewModel.state.value.games.map { it.id })
        assertEquals(listOf("AAA111"), viewModel.rows())
    }

    @Test
    fun `picking a game reloads with that game and All reloads with none`() = runTest {
        fixture.publish("AAA111", NOW - HOUR)
        fixture.publish("BBB222", NOW - 2 * HOUR, game = BETA)
        val viewModel = fixture.viewModel()

        viewModel.pickGame(BETA)
        assertEquals(listOf("BBB222"), viewModel.rows())
        viewModel.pickGame(null)
        assertEquals(listOf("AAA111", "BBB222"), viewModel.rows())

        assertEquals(
            listOf(CommunityFilter(), CommunityFilter(gameId = BETA.value), CommunityFilter()),
            fixture.recording.filters,
        )
    }

    @Test
    fun `picking a difficulty reloads, and picking the same chip again drops it`() = runTest {
        fixture.publish("AAA111", NOW - HOUR, difficulty = Level.Easy)
        fixture.publish("BBB222", NOW - 2 * HOUR, difficulty = Level.Medium)
        val viewModel = fixture.viewModel()

        viewModel.pickDifficulty(DifficultyChoice.Medium)
        assertEquals(listOf("BBB222"), viewModel.rows())
        viewModel.pickDifficulty(DifficultyChoice.Medium)

        assertNull(viewModel.state.value.difficulty)
        assertEquals(listOf("AAA111", "BBB222"), viewModel.rows())
        assertEquals(setOf(Level.Medium), fixture.recording.filters[1].difficulties)
        assertEquals(emptySet<Level>(), fixture.recording.filters[2].difficulties)
    }

    @Test
    fun `picking no difficulty drops it, and clearing the filters drops the game and the difficulty in one reload`() = runTest {
        fixture.publish("AAA111", NOW - HOUR, difficulty = Level.Easy)
        fixture.publish("BBB222", NOW - 2 * HOUR, game = BETA, difficulty = Level.Easy)
        val viewModel = fixture.viewModel()
        viewModel.pickGame(BETA)
        viewModel.pickDifficulty(DifficultyChoice.Easy)

        viewModel.pickDifficulty(null)
        assertNull(viewModel.state.value.difficulty)
        assertEquals(BETA, viewModel.state.value.game)
        viewModel.pickDifficulty(DifficultyChoice.Easy)
        val before = fixture.recording.filters.size
        viewModel.clearFilters()

        assertNull(viewModel.state.value.game)
        assertNull(viewModel.state.value.difficulty)
        assertEquals(listOf("AAA111", "BBB222"), viewModel.rows())
        assertEquals(before + 1, fixture.recording.filters.size)
        assertEquals(CommunityFilter(), fixture.recording.filters.last())
    }

    @Test
    fun `Hard includes Expert`() = runTest {
        fixture.publish("AAA111", NOW - HOUR, difficulty = Level.Hard)
        fixture.publish("BBB222", NOW - 2 * HOUR, difficulty = Level.Expert)
        fixture.publish("CCC333", NOW - 3 * HOUR, difficulty = Level.Medium)
        val viewModel = fixture.viewModel()

        viewModel.pickDifficulty(DifficultyChoice.Hard)

        assertEquals(listOf("AAA111", "BBB222"), viewModel.rows())
        assertEquals(setOf(Level.Hard, Level.Expert), fixture.recording.filters.last().difficulties)
    }

    @Test
    fun `a game and a difficulty together reload with both`() = runTest {
        fixture.publish("AAA111", NOW - HOUR, difficulty = Level.Easy)
        fixture.publish("BBB222", NOW - 2 * HOUR, game = BETA, difficulty = Level.Easy)
        val viewModel = fixture.viewModel()

        viewModel.pickGame(BETA)
        viewModel.pickDifficulty(DifficultyChoice.Easy)

        assertEquals(listOf("BBB222"), viewModel.rows())
        assertEquals(CommunityFilter(BETA.value, setOf(Level.Easy)), fixture.recording.filters.last())
    }

    @Test
    fun `a reported row drops at once, stays hidden on this device and says so`() = runTest {
        fixture.publish("AAA111", NOW - HOUR)
        fixture.publish("BBB222", NOW - 2 * HOUR)
        val viewModel = fixture.viewModel()

        viewModel.report(code("AAA111"))

        assertEquals(listOf("BBB222"), viewModel.rows())
        assertEquals(setOf(code("AAA111")), fixture.hidden.hidden)
        assertEquals("Reported. It's hidden on this device.", viewModel.state.value.notice?.message)
    }

    @Test
    fun `a signed-out report asks to sign in and hides nothing`() = runTest {
        val signedOut = CommunityFixture(signedIn = false)
        signedOut.publish("AAA111", NOW - HOUR)
        val viewModel = signedOut.viewModel()

        viewModel.report(code("AAA111"))

        assertTrue(viewModel.state.value.signInPrompt)
        assertEquals(listOf("AAA111"), viewModel.rows())
        assertTrue(signedOut.hidden.hidden.isEmpty())
        viewModel.dismissSignIn()
        assertFalse(viewModel.state.value.signInPrompt)
    }

    @Test
    fun `a report while offline keeps the row and says to try again later`() = runTest {
        fixture.publish("AAA111", NOW - HOUR)
        val viewModel = fixture.viewModel()
        fixture.shared.online = false

        viewModel.report(code("AAA111"))

        assertEquals(listOf("AAA111"), viewModel.rows())
        assertTrue(fixture.hidden.hidden.isEmpty())
        assertEquals("You're offline. Report it when you're back.", viewModel.state.value.notice?.message)
    }

    @Test
    fun `offline shows the offline state and Retry loads once the connection is back`() = runTest {
        fixture.publish("AAA111", NOW - HOUR)
        fixture.shared.online = false
        val viewModel = fixture.viewModel()
        assertEquals(CommunityPhase.Offline, viewModel.state.value.phase)

        fixture.shared.online = true
        viewModel.retry()

        assertEquals(listOf("AAA111"), viewModel.rows())
    }

    @Test
    fun `a refused list shows its own state and Retry asks again`() = runTest {
        fixture.shared.listRefused = true
        val viewModel = fixture.viewModel()
        assertEquals(CommunityPhase.Refused, viewModel.state.value.phase)

        fixture.shared.listRefused = false
        viewModel.retry()

        assertEquals(CommunityPhase.Loaded(emptyList()), viewModel.state.value.phase)
    }

    @Test
    fun `the checked game again does not reload, and the checked difficulty again drops it`() = runTest {
        fixture.publish("AAA111", NOW - HOUR, difficulty = Level.Hard)
        fixture.publish("BBB222", NOW - 2 * HOUR, game = BETA, difficulty = Level.Easy)
        val viewModel = fixture.viewModel()

        viewModel.pickGame(BETA)
        viewModel.pickGame(BETA)
        assertEquals(listOf("BBB222"), viewModel.rows())
        assertEquals("a re-tap of the checked game is no reload", 2, fixture.recording.filters.size)

        viewModel.pickDifficulty(DifficultyChoice.Easy)
        viewModel.pickDifficulty(DifficultyChoice.Easy)
        assertNull("a re-tap of the checked difficulty drops it", viewModel.state.value.difficulty)
        assertEquals(BETA, viewModel.state.value.game)
    }
}
