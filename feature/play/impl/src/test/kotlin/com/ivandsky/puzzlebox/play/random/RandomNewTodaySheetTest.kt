package com.ivandsky.puzzlebox.play.random

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
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
import com.ivandsky.puzzlebox.play.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.play.domain.LoadPastDay
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomDay
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomPuzzle
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomShared
import com.ivandsky.puzzlebox.play.domain.random.RandomPick
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

    private fun mountSheet(preset: GameId = gamma) {
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

    /** A tap, then a second of clock for the option page's swap and its return. */
    private fun tap(tag: String) {
        node(tag).performClick()
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.waitForIdle()
    }

    /** Opens the [filter] chip's option page and picks the row for [option]. */
    private fun choose(filter: String, option: String) {
        tap("random.filter.$filter")
        tap("random.$filter.$option")
    }

    private fun pickOnNewToday(vararg picks: Pair<String, String>): String {
        mountSheet()
        tap("random.source.community")
        picks.forEach { (filter, option) -> choose(filter, option) }
        node("random.pick").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { opened.isNotEmpty() }
        return (opened.single() as RandomPick.Shared).code.value
    }

    @Test
    fun `New today trades Not solved yet for the Difficulty chip and the past games for the community games`() {
        mountSheet()
        node("random.unsolved").assertExists()
        node("random.filter.difficulty").assertDoesNotExist()
        tap("random.filter.game")
        node("random.game.gamma").assertExists()
        tap("random.filter.game.page.back")

        tap("random.source.community")

        node("random.source.community").assertIsSelected()
        node("random.unsolved").assertDoesNotExist()
        tap("random.filter.difficulty")
        listOf("Any", "Easy", "Medium", "Hard").forEach { node("random.difficulty.$it").assertExists() }
        node("random.difficulty.Any").assertIsSelected()
        tap("random.filter.difficulty.page.back")
        tap("random.filter.game")
        node("random.game.gamma").assertDoesNotExist()
        node("random.game.alpha").assertExists()
    }

    @Test
    fun `each New today difficulty row carries its level's mark and Any carries none`() {
        mountSheet()
        tap("random.source.community")
        tap("random.filter.difficulty")

        listOf("Easy", "Medium", "Hard").forEach {
            composeRule.onNode(hasTestTag("difficulty.${it.lowercase()}") and hasAnyAncestor(hasTestTag("random.difficulty.$it")), useUnmergedTree = true)
                .assertExists()
        }
        composeRule.onNode(hasTestTag("difficulty.easy") and hasAnyAncestor(hasTestTag("random.difficulty.Any")), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun `going back to Past puzzles brings Not solved yet and the past games back`() {
        mountSheet()
        tap("random.source.community")

        tap("random.source.past")

        node("random.unsolved").assertExists()
        node("random.filter.difficulty").assertDoesNotExist()
        tap("random.filter.game")
        node("random.game.gamma").assertExists()
    }

    // Each option gets its own test: a pick that ignores it returns one fixed code under Random(1), failing at least one of them.
    @Test
    fun `the alpha option narrows a New today pick to an alpha puzzle`() {
        assertTrue(pickOnNewToday("game" to "alpha") in setOf("AAA111", "BBB222"))
    }

    @Test
    fun `the beta option narrows a New today pick to the beta puzzle`() {
        assertEquals("CCC333", pickOnNewToday("game" to "beta"))
    }

    @Test
    fun `a chosen game that drops out of the games on offer falls back to Any`() {
        mountSheet()
        node("random.filter.game").assertTextEquals("Gamma")

        past.value = communityGames
        composeRule.waitForIdle()

        node("random.filter.game").assertTextEquals("Game")
        tap("random.filter.game")
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

        tap("random.filter.game")
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

        tap("random.filter.game")
        node("random.game.gamma").assertIsSelected()
    }

    @Test
    fun `the Hard option narrows a New today pick to the Hard puzzle`() {
        assertEquals("BBB222", pickOnNewToday("difficulty" to "Hard"))
    }

    @Test
    fun `the Easy option narrows a New today pick to an Easy puzzle`() {
        assertTrue(pickOnNewToday("difficulty" to "Easy") in setOf("AAA111", "CCC333"))
    }

    @Test
    fun `a New today pick hands the puzzle's code on to open`() {
        assertEquals(code("CCC333").value, pickOnNewToday("game" to "beta", "difficulty" to "Easy"))
    }

    @Test
    fun `a game the other source lacks falls back to Any on switching`() {
        mountSheet()
        choose("game", "gamma")

        tap("random.source.community")

        tap("random.filter.game")
        node("random.game.any").assertIsSelected()
    }

    @Test
    fun `a game both sources offer stays chosen on switching`() {
        mountSheet()
        choose("game", "beta")

        tap("random.source.community")
        tap("random.filter.game")
        node("random.game.beta").assertIsSelected()
        tap("random.filter.game.page.back")
        tap("random.source.past")

        tap("random.filter.game")
        node("random.game.beta").assertIsSelected()
    }

    @Test
    @Config(qualifiers = "w900dp-h900dp")
    fun `on a tablet the toggle is above the chips on the left and Pick is on the right`() {
        val state = RandomPuzzleState(RandomSource.Community, game = null, unsolvedOnly = true, difficulty = null, phase = RandomPhase.Idle)
        composeRule.setContent {
            PuzzleBoxTheme { RandomPuzzleContent(state, communityGames, PlayLayout.TwoPane, RandomPuzzleActions({}, {}, {}, {}, {})) }
        }

        fun inside(tag: String, column: String) =
            composeRule.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag(column))).assertExists()
        listOf("random.source.past", "random.source.community", "random.filter.game", "random.filter.difficulty").forEach { inside(it, "random.left") }
        inside("random.pick", "random.right")
        val top = { tag: String -> node(tag).getBoundsInRoot().top }
        assertTrue(top("random.source.past") < top("random.filter.game"))
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val SETTLE_MS = 1000L
    }
}
