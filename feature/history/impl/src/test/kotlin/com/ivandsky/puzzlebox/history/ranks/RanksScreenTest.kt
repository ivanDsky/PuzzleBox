package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ComputeMatchLegs
import com.ivandsky.puzzlebox.domain.CreateMatch
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ranks.GetPlayerActions
import com.ivandsky.puzzlebox.domain.ranks.OverallStandings
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.history.HistoryDayRow
import com.ivandsky.puzzlebox.history.HistoryReplays
import com.ivandsky.puzzlebox.history.HistoryTestSupport
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeVisibilityRepository
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The hub's board slot as drawn: Overall's points, the partial note, and the in-place sign-in (A3, A11, A12); and the
 * swipe as the approved build has it (user-check F3, F9, F15, F17): where a drag commits, what a flick is, what a pull
 * past the ends moves, and how long a picked day keeps the board.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RanksScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val standings = OverallStandings(
        listOf(
            OverallStandings.Row("maya", "Maya", 1, 200.0, 2, truncated = false),
            OverallStandings.Row("u1", "Ann", 2, 150.0, 2, truncated = true),
        ),
    )

    private fun show(state: OverallState, signIn: RanksSignIn = RanksSignIn.Idle, onSignIn: () -> Unit = {}) {
        val slice = RanksBoardSlice("Overall · today", RankOrder.TimeThenHints, state.toDailyBoardState("u1"), "Points", state.boardNote())
        composeRule.setContent { PuzzleBoxTheme { RanksScreen(RanksUiState(slice, signIn), onSignIn, onSwipe = {}) } }
    }

    @Test
    fun `a partial Overall shows its rows and the k of n note`() {
        show(OverallState.Partial(standings, loaded = 2, total = 5))

        composeRule.onNodeWithText("Maya").assertIsDisplayed()
        composeRule.onNodeWithTag("ranks.board.note").assertIsDisplayed()
        composeRule.onNodeWithText("2 of 5 boards").assertIsDisplayed()
    }

    @Test
    fun `a complete Overall has no note`() {
        show(OverallState.Complete(standings))

        composeRule.onNodeWithText("Ann").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTagCount("ranks.board.note"))
    }

    @Test
    fun `signed out shows the sign-in prompt and its button signs in`() {
        var taps = 0
        show(OverallState.SignedOut, onSignIn = { taps++ })

        composeRule.onNodeWithTag("ranks.signIn").assertIsDisplayed()
        composeRule.onNodeWithTag("ranks.signIn.button").performClick()

        assertEquals(1, taps)
    }

    @Test
    fun `while signing in the button is off`() {
        show(OverallState.SignedOut, signIn = RanksSignIn.SigningIn)

        composeRule.onNodeWithTag("ranks.signIn.button").assertIsNotEnabled()
    }

    @Test
    fun `a failed sign-in says so`() {
        show(OverallState.SignedOut, signIn = RanksSignIn.Failed)

        composeRule.onNodeWithTag("ranks.signIn.failed").assertIsDisplayed()
    }

    private val maya = DailyBoardState.Ranked(listOf(DailyBoardRowModel(1, "Maya", 62_000L, 0, false, playerId = "maya")))

    // The hub goes to the board a swipe asks for, as the view model has it: the next swipe starts from there.
    private fun showSwiping(position: Int = 1, onRefresh: (() -> Unit)? = null, yourSolve: YourSolve? = null, onSwipe: (Int) -> Unit) {
        val slice = RanksBoardSlice("Zip · today", RankOrder.TimeThenHints, maya)
        var at by mutableIntStateOf(position)
        composeRule.setContent {
            PuzzleBoxTheme {
                RanksScreen(
                    RanksUiState(slice, position = at, boardCount = 4, yourSolve = yourSolve),
                    {},
                    onSwipe = {
                        at += it
                        onSwipe(it)
                    },
                    onRefresh = onRefresh,
                )
            }
        }
    }

    @Test
    fun `swiping the board left asks for the next board and right for the previous`() {
        val steps = mutableListOf<Int>()
        showSwiping { steps += it }

        composeRule.onNodeWithTag("ranks.board").performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag("ranks.board").performTouchInput { swipeRight() }

        assertEquals(listOf(1, -1), steps)
    }

    @Test
    fun `a short slow drag leaves the board where it is`() {
        val steps = mutableListOf<Int>()
        showSwiping { steps += it }

        composeRule.onNodeWithTag("ranks.board").performTouchInput {
            swipe(Offset(centerX, centerY), Offset(centerX - width * 0.1f, centerY), durationMillis = 1_500)
        }

        assertEquals(emptyList<Int>(), steps)
    }

    @Test
    fun `a swipe in the empty space under a short board changes the board too`() {
        val steps = mutableListOf<Int>()
        showSwiping { steps += it }

        composeRule.onNodeWithTag("route.ranks").performTouchInput {
            swipe(Offset(right - 10f, bottom - 10f), Offset(left + 10f, bottom - 10f))
        }

        assertEquals(listOf(1), steps)
    }

    @Test
    fun `a slanted sideways swipe changes the board and pulls no refresh`() {
        val steps = mutableListOf<Int>()
        var refreshes = 0
        showSwiping(onRefresh = { refreshes++ }) { steps += it }

        // 35° off the horizontal, and 300 px down: past the pull's threshold had the page taken it.
        composeRule.onNodeWithTag("ranks.board").performTouchInput {
            down(center)
            repeat(15) { moveBy(Offset(-30f, 21f)) }
            up()
        }
        composeRule.waitForIdle()

        assertEquals(listOf(1), steps)
        assertEquals(0, refreshes)
    }

    @Test
    fun `a pull down that drifts sideways refreshes and leaves the board where it is`() {
        val steps = mutableListOf<Int>()
        var refreshes = 0
        showSwiping(onRefresh = { refreshes++ }) { steps += it }

        composeRule.onNodeWithTag("ranks.board").performTouchInput {
            down(center)
            repeat(8) { moveBy(Offset(2f, 30f)) }
            repeat(10) { moveBy(Offset(40f, 0f)) }
            up()
        }
        composeRule.waitForIdle()

        assertEquals(emptyList<Int>(), steps)
        assertEquals(1, refreshes)
    }

    @Test
    fun `the dots say which board of how many`() {
        showSwiping(position = 1) {}

        composeRule.onNodeWithTag("ranks.dots").assertContentDescriptionEquals("Board 2 of 4")
    }

    @Test
    fun `a single board shows no dots`() {
        composeRule.setContent {
            PuzzleBoxTheme { RanksScreen(RanksUiState(RanksBoardSlice("Zip · today", RankOrder.TimeThenHints, DailyBoardState.Loading)), {}, {}) }
        }

        assertEquals(0, composeRule.onAllNodesWithTagCount("ranks.dots"))
    }

    // One finger on the board, moved by each of [movesPx] in turn, [gapMillis] apart, then lifted.
    private fun drag(vararg movesPx: Float, gapMillis: Long) {
        composeRule.onNodeWithTag("ranks.board").performTouchInput {
            down(center)
            movesPx.forEach { moveBy(Offset(it, 0f), delayMillis = gapMillis) }
            up()
        }
        composeRule.waitForIdle()
    }

    // The screen is 320 px wide: a quarter of it is 80 px.
    @Test
    fun `a slow drag of a quarter of the width commits`() {
        val steps = mutableListOf<Int>()
        showSwiping { steps += it }

        drag(-20f, -20f, -20f, -20f, gapMillis = SLOW_MS)

        assertEquals(listOf(1), steps)
    }

    @Test
    fun `a slow drag a pixel short of a quarter of the width returns`() {
        val steps = mutableListOf<Int>()
        showSwiping { steps += it }

        drag(-20f, -20f, -20f, -19f, gapMillis = SLOW_MS)

        assertEquals(emptyList<Int>(), steps)
    }

    // 1600 px wide: a quarter is 400 px, the 120 dp cap is 240 px.
    @Test
    @Config(qualifiers = WIDE)
    fun `on a wide screen a slow drag past the cap commits, well short of a quarter`() {
        val steps = mutableListOf<Int>()
        showSwiping { steps += it }

        drag(-50f, -50f, -50f, -50f, -50f, gapMillis = SLOW_MS)

        assertEquals(listOf(1), steps)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun `on a wide screen a slow drag short of the cap returns`() {
        val steps = mutableListOf<Int>()
        showSwiping { steps += it }

        drag(-46f, -46f, -46f, -46f, -46f, gapMillis = SLOW_MS)

        assertEquals(emptyList<Int>(), steps)
    }

    // 8 px every 20 ms is 400 px a second, over the 300 a flick takes; the 40 px it covers is half the commit distance.
    @Test
    fun `a flick commits however short it is`() {
        val steps = mutableListOf<Int>()
        showSwiping { steps += it }

        drag(-8f, -8f, -8f, -8f, -8f, gapMillis = 20)

        assertEquals(listOf(1), steps)
    }

    // 5 px every 20 ms is 250 px a second.
    @Test
    fun `a short drag slower than a flick returns`() {
        val steps = mutableListOf<Int>()
        showSwiping { steps += it }

        drag(-5f, -5f, -5f, -5f, -5f, -5f, -5f, -5f, gapMillis = 20)

        assertEquals(emptyList<Int>(), steps)
    }

    private val solve = YourSolve(
        row = HistoryDayRow(
            day = today,
            puzzleNumber = 594,
            gameId = GameId("zip"),
            gameName = "Zip",
            record = CompletionRecord(
                puzzleRef = object : PuzzleRef {
                    override val gameId = GameId("zip")
                    override val key = PuzzleKey.Daily(today, 594)
                },
                time = 95_000L,
                hints = 1,
                stats = RecordStats(1, "{}"),
            ),
        ),
        replays = HistoryReplays.Hidden,
        playUi = null,
    )

    // Where your solve, the dots and the board start, in px from the left edge; not clipped, so a lean off the screen reads too.
    private fun lefts() = listOf("ranks.yoursolve", "ranks.dots", "ranks.board")
        .map { composeRule.onNodeWithTag(it).fetchSemanticsNode().positionInRoot.x }

    private fun assertLefts(expected: List<Float>, actual: List<Float>) =
        expected.zip(actual).forEach { (wanted, left) -> assertEquals("$expected, $actual", wanted, left, 0.5f) }

    // The finger stays down after [pullPx], so the lean can be read before anything settles.
    private fun pull(pullPx: Float) {
        composeRule.onNodeWithTag("ranks.board").performTouchInput {
            down(center)
            repeat(2) { moveBy(Offset(pullPx / 2, 0f)) }
        }
        composeRule.waitForIdle()
    }

    private fun letGo() {
        composeRule.onNodeWithTag("ranks.board").performTouchInput { up() }
        composeRule.waitForIdle()
    }

    @Test
    fun `a pull past the first board leans your solve, the dots and the board a fifth of the way, then lets them back`() {
        val steps = mutableListOf<Int>()
        showSwiping(position = 0, yourSolve = solve) { steps += it }
        val rest = lefts()

        pull(100f)
        assertLefts(rest.map { it + 20f }, lefts())

        letGo()
        assertLefts(rest, lefts())
        assertEquals(emptyList<Int>(), steps)
    }

    @Test
    fun `a pull past the last board leans them the other way`() {
        val steps = mutableListOf<Int>()
        showSwiping(position = 3, yourSolve = solve) { steps += it }
        val rest = lefts()

        pull(-100f)
        assertLefts(rest.map { it - 20f }, lefts())

        letGo()
        assertLefts(rest, lefts())
        assertEquals(emptyList<Int>(), steps)
    }

    private val drawn = RanksUiState(RanksBoardSlice("Zip · today", RankOrder.TimeThenHints, maya), position = 1, boardCount = 4)
    private val loading = drawn.copy(board = RanksBoardSlice("Zip · Thu, Nov 12", RankOrder.TimeThenHints, DailyBoardState.Loading))
    private val answered = drawn.copy(
        board = RanksBoardSlice("Zip · Thu, Nov 12", RankOrder.TimeThenHints, DailyBoardState.Ranked(listOf(DailyBoardRowModel(1, "Ann", 71_000L, 0, false, playerId = "ann")))),
    )

    @Test
    fun `a picked day that does not answer keeps the drawn board for a second and a half, then says Loading`() {
        var state by mutableStateOf(drawn)
        composeRule.setContent { PuzzleBoxTheme { RanksScreen(state, {}, {}) } }

        state = loading
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(1_450)

        composeRule.onNodeWithText("Maya").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTagCount("daily_board.loading"))

        composeRule.mainClock.advanceTimeBy(100)

        composeRule.onNodeWithTag("daily_board.loading").assertIsDisplayed()
    }

    @Test
    fun `a row of the board kept on screen takes no tap until the picked day answers`() {
        var state by mutableStateOf(drawn)
        val sheet = playerSheet()
        composeRule.setContent { PuzzleBoxTheme { RanksScreen(state, {}, {}, playerSheet = sheet) } }
        composeRule.onNodeWithTag("daily_board.row").assertHasClickAction()

        state = loading
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Maya").assertIsDisplayed()
        composeRule.onNodeWithTag("daily_board.row").assertHasNoClickAction()

        state = answered
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Ann").assertIsDisplayed()
        composeRule.onNodeWithTag("daily_board.row").assertHasClickAction()
    }

    private fun playerSheet(): PlayerSheetViewModel {
        val registry = HistoryTestSupport.registry
        val daily = FakeDailyRepository()
        val completions = FakeCompletionRepository()
        val createMatch = CreateMatch(
            FakeMatchRepository(),
            FakeMatchLocalRepository(),
            ComputeMatchLegs(registry, daily, FakeGameCalendar(today), ObserveGameHistory(registry, daily, completions), FakeReceivedPuzzleRepository(), Random(0)),
            FakeIdentityService(Identity(uid = "u1", displayName = "Ann", photoUrl = null)),
            Random(0),
        )
        return PlayerSheetViewModel(GetPlayerActions(FakeVisibilityRepository(), completions), createMatch, registry, GamePlayRegistry(emptyList()))
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private companion object {
        const val SLOW_MS = 200L
        const val WIDE = "w800dp-h600dp-xhdpi"
        val today: LocalDate = LocalDate.of(2026, 11, 13)
    }
}
