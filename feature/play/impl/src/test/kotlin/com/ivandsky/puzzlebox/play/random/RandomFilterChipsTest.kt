package com.ivandsky.puzzlebox.play.random

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.play.domain.random.RandomPick
import com.ivandsky.puzzlebox.play.community.BETA
import com.ivandsky.puzzlebox.play.community.DifficultyChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Random sheet's filters as two chips with the option page swapped in place (epic picker-filters, ticket 06), through the real
 * sheet and view model with the flag on ([RandomFilterSheetFixture]); the busy and tablet cases are in [RandomFilterBodyTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class RandomFilterChipsTest {
    @get:Rule
    val rule = createComposeRule()

    private val sheet = RandomFilterSheetFixture(rule)
    private val viewModel get() = sheet.viewModel
    private val opened get() = sheet.opened

    private fun mountSheet(fontScale: Float = 1f) = sheet.mountSheet(fontScale)

    private fun node(tag: String) = sheet.node(tag)

    private fun count(tag: String) = sheet.count(tag)

    private fun tap(tag: String) = sheet.tap(tag)

    private fun onNewToday() = sheet.onNewToday()

    @Test
    fun `New today shows the Game and Difficulty chips and no label rows`() {
        onNewToday()
        tap("random.filter.game")
        tap("random.game.any")

        node("random.filter.game").assertIsDisplayed().assertTextEquals("Game")
        node("random.filter.difficulty").assertIsDisplayed().assertTextEquals("Difficulty")
        // Each name is the chip's own text; a label row above the chips would be a second node.
        assertEquals(1, rule.onAllNodesWithText("Game").fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText("Difficulty").fetchSemanticsNodes().size)
        node("random.game.any").assertDoesNotExist()
    }

    @Test
    fun `Past puzzles has the Game chip and Not solved yet but no Difficulty chip`() {
        mountSheet()

        node("random.filter.game").assertIsDisplayed()
        node("random.filter.difficulty").assertDoesNotExist()
        node("random.unsolved").assertIsDisplayed()
    }

    @Test
    fun `opening Game opens no second sheet, shows the page with its title and Back, and hides the Random content`() {
        mountSheet()

        tap("random.filter.game")

        assertEquals(0, count("random.filter.game.sheet"))
        assertEquals(1, count("random.sheet"))
        node("random.filter.game.page").assertIsDisplayed()
        rule.onNode(hasText("Game") and hasAnyAncestor(hasTestTag("random.filter.game.page"))).assertIsDisplayed()
        rule.onNode(hasContentDescription("Back") and hasAnyAncestor(hasTestTag("random.filter.game.page"))).assertIsDisplayed()
        node("random.filter.game.page.back").assertIsDisplayed()
        node("random.pick").assertDoesNotExist()
        node("random.source.past").assertDoesNotExist()
    }

    @Test
    fun `the Game page lists Any game then the offered games in order with the chosen one selected`() {
        mountSheet()

        tap("random.filter.game")

        val top = { tag: String -> node(tag).getBoundsInRoot().top }
        assertTrue(top("random.game.any") < top("random.game.alpha"))
        assertTrue(top("random.game.alpha") < top("random.game.beta"))
        assertTrue(top("random.game.beta") < top("random.game.gamma"))
        node("random.game.alpha").assertIsSelected()
        node("random.game.any").assertIsNotSelected()
        node("random.game.any").assert(hasText("Any game"))
    }

    @Test
    fun `a game pick highlights at once, returns to the Random content after 150 ms and reads on the chip`() {
        mountSheet()
        tap("random.filter.game")
        rule.mainClock.autoAdvance = false

        node("random.game.beta").performClick()
        rule.mainClock.advanceTimeByFrame()
        node("random.game.beta").assertIsSelected()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(1, count("random.filter.game.page"))
        rule.mainClock.advanceTimeBy(RandomFilterSheetFixture.SETTLE_MS)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertEquals(0, count("random.filter.game.page"))
        assertEquals(BETA, viewModel.state.value.game)
        node("random.filter.game").assertTextEquals("Beta")
        node("random.source.past").assertIsDisplayed()
    }

    @Test
    fun `Any game gives null and the chip reads Game`() {
        mountSheet()

        tap("random.filter.game")
        tap("random.game.any")

        assertNull(viewModel.state.value.game)
        node("random.filter.game").assertTextEquals("Game")
    }

    @Test
    fun `the Difficulty page lists Any difficulty, Easy, Medium and Hard with marks, and Hard gives the Hard choice`() {
        onNewToday()

        tap("random.filter.difficulty")

        node("random.filter.difficulty.page").assertIsDisplayed()
        node("random.difficulty.Any").assertIsSelected().assert(hasText("Any difficulty"))
        listOf("easy", "medium", "hard").forEach { level ->
            rule.onNode(hasTestTag("difficulty.$level") and hasAnyAncestor(hasTestTag("random.difficulty.${level.replaceFirstChar(Char::uppercase)}")), useUnmergedTree = true)
                .assertExists()
        }
        tap("random.difficulty.Hard")

        assertEquals(DifficultyChoice.Hard, viewModel.state.value.difficulty)
        node("random.filter.difficulty").assertTextEquals("Hard")
    }

    @Test
    fun `the Back arrow returns to the Random content and leaves the sheet open`() {
        mountSheet()
        tap("random.filter.game")

        tap("random.filter.game.page.back")

        assertEquals(0, count("random.filter.game.page"))
        assertEquals(1, count("random.sheet"))
        node("random.source.past").assertIsDisplayed()
        node("random.filter.game").assertIsDisplayed()
    }

    @Test
    fun `a pick uses the game and difficulty chosen in the chips`() {
        onNewToday()
        tap("random.filter.game")
        tap("random.game.alpha")
        tap("random.filter.difficulty")
        tap("random.difficulty.Hard")

        node("random.pick").performClick()
        rule.waitUntil(TIMEOUT_MS) { opened.isNotEmpty() }

        assertEquals("BBB222", (opened.single() as RandomPick.Shared).code.value)
    }

    @Test
    fun `an empty pick says so in the real status wording`() {
        onNewToday()
        tap("random.filter.game")
        tap("random.game.beta")
        tap("random.filter.difficulty")
        tap("random.difficulty.Hard")

        node("random.pick").performClick()
        rule.waitForIdle()

        node("random.status").assertTextEquals("No new community puzzles right now.")
    }

    @Test
    fun `switching to Past hides Difficulty and keeps a game Past offers`() {
        onNewToday()
        tap("random.filter.game")
        tap("random.game.beta")

        tap("random.source.past")

        node("random.filter.difficulty").assertDoesNotExist()
        node("random.unsolved").assertIsDisplayed()
        node("random.filter.game").assertTextEquals("Beta")
    }

    @Test
    fun `a game Past offers and New today does not falls back to Game on switching`() {
        mountSheet()
        tap("random.filter.game")
        tap("random.game.gamma")
        node("random.filter.game").assertTextEquals("Gamma")

        tap("random.source.community")

        node("random.filter.game").assertTextEquals("Game")
    }

    @Test
    fun `the chips and the page's rows and Back arrow are at least 48dp`() {
        onNewToday()
        node("random.filter.game").assertTouchHeightIsEqualTo(48.dp)
        node("random.filter.difficulty").assertTouchHeightIsEqualTo(48.dp)

        tap("random.filter.game")

        node("random.game.any").assertHeightIsAtLeast(48.dp)
        node("random.game.alpha").assertHeightIsAtLeast(48.dp)
        node("random.filter.game.page.back").assertHeightIsAtLeast(48.dp)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
    fun `at font scale 1 point 3 on a small phone the chip row and the Pick button are reachable by scrolling`() {
        mountSheet(fontScale = 1.3f)
        tap("random.source.community")

        node("random.filter.difficulty").performScrollTo().assertIsDisplayed()
        node("random.pick").performScrollTo().assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
