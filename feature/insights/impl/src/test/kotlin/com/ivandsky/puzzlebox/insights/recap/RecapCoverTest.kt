package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.gameName
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The recap's cover: what it says for the period and the player, and that it opens the story. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class RecapCoverTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(name: String?) = rule.setContent {
        PuzzleBoxTheme {
            RecapScreen(RecapFixtures.ready, "November 2026", gameName, onPage = {}, onWatchReplay = {}, onClose = {}, displayName = name)
        }
    }

    @Test
    fun `a fresh open shows the cover with its copy, the player's name and no replay button`() {
        show("Ivan")

        rule.onNodeWithTag("recap.cover").assertIsDisplayed()
        rule.onNodeWithText("PuzzleBox Insights").assertIsDisplayed()
        rule.onNodeWithText("Your November").assertIsDisplayed()
        rule.onNodeWithText("Three games. One month. Let's look back.").assertIsDisplayed()
        rule.onNodeWithText("Recap for Ivan").assertIsDisplayed()
        rule.onNodeWithText("Tap to begin ›").assertIsDisplayed()
        rule.onNodeWithTag("recap.replay").assertDoesNotExist()
    }

    @Test
    fun `no name leaves the Recap for line out`() {
        show(null)

        rule.onNodeWithText("Tap to begin ›").assertIsDisplayed()
        rule.onNodeWithText("Recap for", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a long name wraps by word inside the cover and stays clear of the hook`() {
        show("Maximiliana Oyelaran-Featherstonehaugh")

        val name = rule.onNodeWithText("Recap for", substring = true).getBoundsInRoot()
        val hook = rule.onNodeWithText("Let's look back.", substring = true).getBoundsInRoot()
        val cover = rule.onNodeWithTag("recap.cover").getBoundsInRoot()
        assertTrue("the name stays inside the cover", name.right <= cover.right && name.left >= cover.left)
        assertTrue("the name is below the hook", name.top >= hook.bottom)
        assertTrue("the name wrapped onto a second line", name.bottom - name.top > 24.dp)
    }

    @Test
    fun `system back on the cover is left to the screen so it leaves the recap`() {
        show("Ivan")

        assertFalse(rule.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test
    fun `the hook counts the games, spells the number up to ten and says one year for a year`() {
        val month = RecapPeriod.Month(YearMonth.of(2026, 10))
        val year = RecapPeriod.Year(2025)

        assertEquals("Five games. One month. Let's look back.", coverHook(month, solved(5)))
        assertEquals("Ten games. One year. Let's look back.", coverHook(year, solved(10)))
        assertEquals("11 games. One month. Let's look back.", coverHook(month, solved(11)))
        assertEquals("One game. One month. Let's look back.", coverHook(month, solved(1, total = 3)))
        assertEquals("One month. Let's look back.", coverHook(month, null))
    }

    @Test
    fun `a month with one solve says so in place of the hook`() {
        val month = RecapPeriod.Month(YearMonth.of(2026, 10))

        assertEquals("One puzzle so far. Every streak starts with one.", coverHook(month, solved(1, total = 1)))
    }

    @Test
    fun `the title is Your and the month, or the year's own title`() {
        assertEquals("Your October", coverTitle(RecapPeriod.Month(YearMonth.of(2026, 10)), "October 2026", java.util.Locale.US))
        assertEquals("2026 so far", coverTitle(RecapPeriod.Year(2026), "2026 so far"))
    }

    private fun solved(games: Int, total: Int = 40) = RecapCard.Solved(
        total,
        List(games) { RecapCard.Solved.PerGame(com.ivandsky.puzzlebox.model.GameId("game$it"), 1) },
    )
}
