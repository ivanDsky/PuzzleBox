package com.ivandsky.puzzlebox.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tablet day pane (variant A, Rows): the header line, each game's row in its three outcomes, the
 * Finish / Play / Sign in / See this day in Ranks taps, and the layout rules (one right edge, one baseline
 * offset, 48 dp targets, nothing clipped at the largest system font).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class HomeDayDetailTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val opened = mutableListOf<Pair<GameId, String>>()
    private var signIns = 0
    private val rankDays = mutableListOf<String>()

    private fun setContent(day: HistoryDay, signedIn: Boolean = true, fontScale: Float = 1f) {
        composeRule.setContent {
            PuzzleBoxTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Pane(day, signedIn)
                }
            }
        }
    }

    @Composable
    private fun Pane(day: HistoryDay, signedIn: Boolean) {
        HomeDayDetail(
            day = day,
            signedIn = signedIn,
            onOpenGame = { game, iso -> opened += game to iso },
            onSignIn = { signIns++ },
            onOpenDay = { rankDays += it },
            modifier = Modifier,
        )
    }

    private fun tag(tag: String) = composeRule.onNodeWithTag(tag)

    @Test
    fun `the header names the date, then weekday, games solved, total time and hints`() {
        setContent(AllPlayedDay)

        tag("home.day_headline").assertTextEquals("November 16")
        tag("home.day_summary").assertTextEquals("Monday · 5 of 5 · 9:11 · 3 hints")
    }

    @Test
    fun `a day with no hints says no hints`() {
        setContent(AllPlayedDay.copy(games = AllPlayedDay.games.map { it.copy(outcome = (it.outcome as HistoryDay.Outcome.Solved).copy(hints = 0)) }))

        tag("home.day_summary").assertTextEquals("Monday · 5 of 5 · 9:11 · no hints")
    }

    @Test
    fun `a solved row shows its time, hints and place among friends`() {
        setContent(AllPlayedDay)

        tag("home.day_time_tango").assertTextEquals("1:12")
        tag("home.day_subline_tango").assertTextEquals("1 hint · #3 of 12 friends")
        tag("home.day_subline_zip").assertTextEquals("No hints · #1 of 12 friends")
    }

    @Test
    fun `signed out the rows carry no place and offer sign in, which reports once`() {
        setContent(SignedOutDay, signedIn = false)

        tag("home.day_subline_tango").assertTextEquals("1 hint")
        tag("home.day_sign_in").performScrollTo().assertIsDisplayed().performClick()

        assertEquals(1, signIns)
    }

    @Test
    fun `signed in there is no sign in link`() {
        setContent(AllPlayedDay)

        tag("home.day_sign_in").assertDoesNotExist()
    }

    @Test
    fun `an opened game says so and Finish opens that game on that day`() {
        setContent(MissedSomeDay)

        tag("home.day_subline_queens").assertTextEquals("Opened, not solved")
        tag("home.day_finish_queens").assertHeightIsAtLeast(48.dp).performScrollTo().performClick()

        assertEquals(listOf(GameId("queens") to "2026-11-15"), opened)
    }

    @Test
    fun `a game not played says so and Play opens that game on that day`() {
        setContent(MissedSomeDay)

        tag("home.day_subline_strands").assertTextEquals("Not played")
        tag("home.day_play_strands").assertHeightIsAtLeast(48.dp).performScrollTo().performClick()

        assertEquals(listOf(GameId("strands") to "2026-11-15"), opened)
    }

    @Test
    fun `solved rows have no Finish or Play`() {
        setContent(MissedSomeDay)

        tag("home.day_finish_zip").assertDoesNotExist()
        tag("home.day_play_zip").assertDoesNotExist()
    }

    @Test
    fun `See this day in Ranks opens the day`() {
        setContent(AllPlayedDay)

        tag("home.day_open_ranks").assertHeightIsAtLeast(48.dp).performScrollTo().performClick()

        assertEquals(listOf("2026-11-16"), rankDays)
    }

    @Test
    fun `times, Finish and Play share one right edge and times one baseline offset in their rows`() {
        setContent(MissedSomeDay)

        val edges = listOf("home.day_time_zip", "home.day_time_tango", "home.day_finish_queens", "home.day_play_strands", "home.day_time_sudoku")
            .map { tag(it).getUnclippedBoundsInRoot().right.value }
        assertEquals("right edges $edges", 1, edges.map { it.toInt() }.toSet().size)

        val offsets = listOf("zip", "tango", "sudoku").map { game ->
            val row = tag("home.day_row_$game").getUnclippedBoundsInRoot()
            val time = tag("home.day_time_$game").getUnclippedBoundsInRoot()
            time.top.value - row.top.value
        }
        assertEquals("baseline offsets $offsets", 1, offsets.map { it.toInt() }.toSet().size)
    }

    @Test
    fun `the longest values fit unclipped at the largest system font`() {
        setContent(LongestValuesDay, fontScale = 2f)

        listOf("home.day_headline", "home.day_summary", "home.day_time_strands", "home.day_subline_strands").forEach { id ->
            val layout = layoutOf(tag(id))
            assertFalse("$id overflowed height", layout.didOverflowHeight)
            assertFalse("$id ellipsized", (0 until layout.lineCount).any(layout::isLineEllipsized))
            assertTrue("$id sticks out of the pane", tag(id).getUnclippedBoundsInRoot().right <= tag("home.day_games").getUnclippedBoundsInRoot().right)
        }
        tag("home.day_headline").assertTextEquals("September 30")
        tag("home.day_time_strands").assertTextEquals("12:47")
        tag("home.day_subline_strands").assertTextEquals("4 hints · #128 of 1,204 friends")
        assertEquals(1, layoutOf(tag("home.day_time_strands")).lineCount)
    }

    private fun layoutOf(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        checkNotNull(node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action).invoke(results)
        return results.single()
    }
}
