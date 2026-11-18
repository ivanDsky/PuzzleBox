package com.ivandsky.puzzlebox.play.random

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.repository.DailyDay
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.random.LoadRandomDay
import com.ivandsky.puzzlebox.domain.random.LoadRandomPuzzle
import com.ivandsky.puzzlebox.domain.random.LoadRandomShared
import com.ivandsky.puzzlebox.domain.random.RandomPick
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.testDispatchers
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeHiddenPuzzles
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The random sheet over the real view model, pick use cases and domain fakes: alpha and beta each have past days to 11-18,
 * so a game chip decides which game a pick comes from. The stateless body is driven with fixed states for the busy and
 * tablet cases.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RandomPuzzleSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 18)
    private val days = (0L until 6).map { LocalDate.of(2026, 11, 10).plusDays(it) }
    private val daily = FakeDailyRepository().apply {
        listOf(alpha, beta).forEach { game ->
            setArchiveRange(game, days.first()..today)
            days.forEach { setDay(game, it, DailyDay.Ready(TestGames.puzzle(game), 100)) }
        }
    }
    private val games = listOf(alpha, beta).map { RandomGame(it, it.value.replaceFirstChar(Char::uppercase), GameBrand.Neutral) }
    private val opened = mutableListOf<RandomPick>()
    private var dismissed = 0

    private fun viewModel(preset: GameId = alpha): RandomPuzzleViewModel {
        val registry = TestGames.registry(listOf(alpha, beta))
        val random = Random(1)
        val load = LoadRandomPuzzle(
            LoadRandomDay(daily, FakeCompletionRepository(), LoadPastDay(daily, registry), registry, random),
            LoadRandomShared(ListCommunityPuzzles(FakeSharedPuzzleRepository(), FakeHiddenPuzzles(), FakeIdentityService(Identity("me", "Me", null))) { 0L }, random),
        )
        return RandomPuzzleViewModel(preset, load, RandomPickMemory(), testDispatchers())
    }

    private fun mountSheet(layout: PlayLayout = PlayLayout.SinglePane, preset: GameId = alpha) {
        val viewModel = viewModel(preset)
        composeRule.setContent {
            PuzzleBoxTheme {
                RandomPuzzleSheet(preset, games, games, layout, onOpen = { opened += it }, onDismiss = { dismissed++ }, viewModel = viewModel)
            }
        }
    }

    private fun node(tag: String) = composeRule.onNodeWithTag(tag)

    @Test
    fun `Not solved yet is on and the preset game is chosen`() {
        mountSheet(preset = beta)

        node("random.unsolved").assertIsOn()
        node("random.game.beta").assertIsSelected()
    }

    private fun pickWithChip(game: GameId) {
        // The preset is the other game, so a chip the pick ignores keeps drawing that one.
        mountSheet(preset = if (game == alpha) beta else alpha)

        node("random.game.${game.value}").performClick()
        node("random.game.${game.value}").assertIsSelected()
        node("random.pick").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { opened.isNotEmpty() }

        assertEquals(game, (opened.single() as RandomPick.Day).game)
    }

    @Test
    fun `the alpha chip narrows the next pick to alpha`() = pickWithChip(alpha)

    @Test
    fun `the beta chip narrows the next pick to beta`() = pickWithChip(beta)

    @Test
    fun `a hide cut short by the host leaving hands nothing on and leaves the view model idle`() {
        val show = mutableStateOf(true)
        val viewModel = viewModel()
        composeRule.setContent {
            PuzzleBoxTheme {
                if (show.value) {
                    RandomPuzzleSheet(alpha, games, games, PlayLayout.SinglePane, onOpen = { opened += it }, onDismiss = {}, viewModel = viewModel)
                }
            }
        }
        composeRule.mainClock.autoAdvance = false
        node("random.pick").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        assertTrue(viewModel.state.value.phase is RandomPhase.Opened)

        show.value = false
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertTrue(opened.isEmpty())
        assertEquals(RandomPhase.Idle, viewModel.state.value.phase)
    }

    @Test
    fun `one tap on Pick opens one pick, after the sheet hid`() {
        mountSheet()

        node("random.pick").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { opened.isNotEmpty() }
        composeRule.waitForIdle()

        assertEquals(1, opened.size)
        assertTrue((opened.single() as RandomPick.Day).day in days)
        assertEquals(0, dismissed)
    }

    @Test
    fun `after a pick is handed on the sheet is idle again, ready for another`() {
        mountSheet()
        node("random.pick").performClick()
        composeRule.waitUntil(TIMEOUT_MS) { opened.isNotEmpty() }
        composeRule.waitForIdle()

        node("random.game.any").assertIsEnabled()
        node("random.pick").assertIsEnabled()
    }

    @Test
    fun `offline says so, keeps the sheet and leaves the filters unchanged`() {
        days.forEach { daily.setDay(alpha, it, DailyDay.Failed(FetchError.Unavailable())) }
        mountSheet()
        node("random.game.alpha").performClick()
        node("random.unsolved").performClick()

        node("random.pick").performClick()
        composeRule.waitForIdle()

        node("random.status").assertTextContains("You're offline. Try again when you're back.")
        node("random.sheet").assertExists()
        node("random.game.alpha").assertIsSelected()
        node("random.unsolved").assertIsOff()
        node("random.pick").assertIsEnabled()
        assertTrue(opened.isEmpty())
    }

    private fun show(state: RandomPuzzleState, layout: PlayLayout, onPick: () -> Unit = {}) {
        composeRule.setContent {
            PuzzleBoxTheme {
                RandomPuzzleContent(state, games, layout, RandomPuzzleActions({}, {}, {}, {}, onPick))
            }
        }
    }

    private fun stateOf(phase: RandomPhase) =
        RandomPuzzleState(RandomSource.Past, game = null, unsolvedOnly = true, difficulty = null, phase = phase)

    @Test
    fun `while picking the chips and the switch are off and the button takes no tap`() {
        var picks = 0
        show(stateOf(RandomPhase.Picking), PlayLayout.SinglePane) { picks++ }

        node("random.game.any").assertIsNotEnabled()
        node("random.game.alpha").assertIsNotEnabled()
        node("random.unsolved").assertIsNotEnabled()
        node("random.pick").performClick()

        assertEquals(0, picks)
    }

    @Test
    fun `on a tablet the chips are left and Not solved yet, Pick and status are right`() {
        show(stateOf(RandomPhase.Said(RandomMessage.Throttled)), PlayLayout.TwoPane)

        node("random.game.any").assertExists()
        composeRule.onNodeWithTag("random.game.any", useUnmergedTree = true).assertExists()
        listOf("random.unsolved", "random.pick", "random.status").forEach {
            composeRule.onNode(hasTestTag(it) and hasAnyAncestor(hasTestTag("random.right"))).assertExists()
        }
        composeRule.onNode(hasTestTag("random.game.any") and hasAnyAncestor(hasTestTag("random.left"))).assertExists()
        composeRule.onNode(hasTestTag("random.pick") and hasAnyAncestor(hasTestTag("random.left"))).assertDoesNotExist()
    }

    @Test
    fun `a phone has no two columns`() {
        show(stateOf(RandomPhase.Idle), PlayLayout.SinglePane)

        node("random.left").assertDoesNotExist()
        node("random.right").assertDoesNotExist()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
