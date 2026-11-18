package com.ivandsky.puzzlebox.play.random

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.random.LoadRandomDay
import com.ivandsky.puzzlebox.domain.random.LoadRandomPuzzle
import com.ivandsky.puzzlebox.domain.random.LoadRandomShared
import com.ivandsky.puzzlebox.domain.random.RandomPick
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.community.ALPHA
import com.ivandsky.puzzlebox.play.community.BETA
import com.ivandsky.puzzlebox.play.community.CommunityFixture
import com.ivandsky.puzzlebox.play.community.HOUR
import com.ivandsky.puzzlebox.play.community.NOW
import com.ivandsky.puzzlebox.play.community.code
import com.ivandsky.puzzlebox.play.testDispatchers
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The random sheet's New today source over the real view model and the fake community list: alpha has an Easy (AAA111)
 * and a Hard (BBB222) puzzle, beta an Easy one (CCC333). Past puzzles offer alpha, beta and gamma; New today only
 * alpha and beta, so gamma is the game the second source lacks.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RandomNewTodaySheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val fixture = CommunityFixture().also {
        runBlocking {
            it.publish("AAA111", NOW - HOUR, ALPHA, Level.Easy)
            it.publish("BBB222", NOW - 2 * HOUR, ALPHA, Level.Hard)
            it.publish("CCC333", NOW - 3 * HOUR, BETA, Level.Easy)
        }
    }
    private val gamma = GameId("gamma")
    private fun gameOf(id: GameId) = RandomGame(id, id.value.replaceFirstChar(Char::uppercase), GameBrand.Neutral)
    private val communityGames = listOf(ALPHA, BETA).map(::gameOf)
    private val past = mutableStateOf(communityGames + gameOf(gamma))
    private val newToday = mutableStateOf(communityGames)
    private val opened = mutableListOf<RandomPick>()

    private fun mountSheet(preset: GameId? = null) {
        val daily = FakeDailyRepository()
        val registry = TestGames.registry(listOf(ALPHA, BETA))
        val random = Random(1)
        val identity = FakeIdentityService(Identity("me", "Me", null))
        val load = LoadRandomPuzzle(
            LoadRandomDay(daily, FakeCompletionRepository(), LoadPastDay(daily, registry), registry, random),
            LoadRandomShared(ListCommunityPuzzles(fixture.shared, fixture.hidden, identity) { NOW }, random),
        )
        val viewModel = RandomPuzzleViewModel(preset, load, RandomPickMemory(), testDispatchers())
        composeRule.setContent {
            PuzzleBoxTheme {
                RandomPuzzleSheet(preset, past.value, newToday.value, PlayLayout.SinglePane, onOpen = { opened += it }, onDismiss = {}, viewModel = viewModel)
            }
        }
    }

    private fun node(tag: String) = composeRule.onNodeWithTag(tag)

    private fun pickOnNewToday(vararg taps: String): String {
        mountSheet()
        node("random.source.community").performClick()
        taps.forEach { node(it).performClick() }
        node("random.pick").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { opened.isNotEmpty() }
        return (opened.single() as RandomPick.Shared).code.value
    }

    @Test
    fun `New today trades Not solved yet for the difficulty chips and the past games for the community games`() {
        mountSheet()
        node("random.unsolved").assertExists()
        node("random.game.gamma").assertExists()
        node("random.difficulty.Any").assertDoesNotExist()

        node("random.source.community").performClick()

        node("random.source.community").assertIsSelected()
        node("random.unsolved").assertDoesNotExist()
        listOf("Any", "Easy", "Medium", "Hard").forEach { node("random.difficulty.$it").assertExists() }
        node("random.difficulty.Any").assertIsSelected()
        node("random.game.gamma").assertDoesNotExist()
        node("random.game.alpha").assertExists()
    }

    @Test
    fun `going back to Past puzzles brings Not solved yet and the past games back`() {
        mountSheet()
        node("random.source.community").performClick()

        node("random.source.past").performClick()

        node("random.unsolved").assertExists()
        node("random.difficulty.Any").assertDoesNotExist()
        node("random.game.gamma").assertExists()
    }

    // Each chip gets its own test: a chip the pick ignores returns one fixed code under Random(1), failing at least one of them.
    @Test
    fun `the alpha chip narrows a New today pick to an alpha puzzle`() {
        assertTrue(pickOnNewToday("random.game.alpha") in setOf("AAA111", "BBB222"))
    }

    @Test
    fun `the beta chip narrows a New today pick to the beta puzzle`() {
        assertEquals("CCC333", pickOnNewToday("random.game.beta"))
    }

    @Test
    fun `a chosen game that drops out of the games on offer falls back to Any`() {
        mountSheet()
        node("random.game.gamma").performClick()
        node("random.game.gamma").assertIsSelected()

        past.value = communityGames
        composeRule.waitForIdle()

        node("random.game.gamma").assertDoesNotExist()
        node("random.game.any").assertIsSelected()
    }

    @Test
    fun `a restored game stays chosen while the lists are still empty and is selected once they arrive`() {
        past.value = emptyList()
        newToday.value = emptyList()
        mountSheet(preset = BETA)

        past.value = communityGames + gameOf(gamma)
        newToday.value = communityGames
        composeRule.waitForIdle()

        node("random.game.beta").assertIsSelected()
    }

    @Test
    fun `a preset game the first list lacks stays chosen until its own list arrives`() {
        past.value = emptyList()
        newToday.value = emptyList()
        mountSheet(preset = gamma)

        newToday.value = communityGames
        composeRule.waitForIdle()
        past.value = communityGames + gameOf(gamma)
        composeRule.waitForIdle()

        node("random.game.gamma").assertIsSelected()
    }

    @Test
    fun `the Hard chip narrows a New today pick to the Hard puzzle`() {
        assertEquals("BBB222", pickOnNewToday("random.difficulty.Hard"))
    }

    @Test
    fun `the Easy chip narrows a New today pick to an Easy puzzle`() {
        assertTrue(pickOnNewToday("random.difficulty.Easy") in setOf("AAA111", "CCC333"))
    }

    @Test
    fun `a New today pick hands the puzzle's code on to open`() {
        assertEquals(code("CCC333").value, pickOnNewToday("random.game.beta", "random.difficulty.Easy"))
    }

    @Test
    fun `a game the other source lacks falls back to Any on switching`() {
        mountSheet()
        node("random.game.gamma").performClick()

        node("random.source.community").performClick()

        node("random.game.any").assertIsSelected()
    }

    @Test
    fun `a game both sources offer stays chosen on switching`() {
        mountSheet()
        node("random.game.beta").performClick()

        node("random.source.community").performClick()
        node("random.game.beta").assertIsSelected()
        node("random.source.past").performClick()

        node("random.game.beta").assertIsSelected()
    }

    @Test
    fun `on a tablet the toggle is above the games on the left and the difficulty is above Pick on the right`() {
        val state = RandomPuzzleState(RandomSource.Community, game = null, unsolvedOnly = true, difficulty = null, phase = RandomPhase.Idle)
        composeRule.setContent {
            PuzzleBoxTheme { RandomPuzzleContent(state, communityGames, PlayLayout.TwoPane, RandomPuzzleActions({}, {}, {}, {}, {})) }
        }

        fun inside(tag: String, column: String) =
            composeRule.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag(column))).assertExists()
        listOf("random.source.past", "random.source.community", "random.game.any").forEach { inside(it, "random.left") }
        listOf("random.difficulty.Any", "random.pick").forEach { inside(it, "random.right") }
        val top = { tag: String -> node(tag).getBoundsInRoot().top }
        assertTrue(top("random.source.past") < top("random.game.any"))
        assertTrue(top("random.difficulty.Any") < top("random.pick"))
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
