package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.recapCardColors
import com.ivandsky.puzzlebox.insights.GOLDEN_TABLET_WIDE
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.gameName
import com.ivandsky.puzzlebox.insights.share.RecapShareState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The recap on a tablet: the story on the left, every fact as a list on the right that follows and drives the story. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class RecapTabletTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(state: RecapUiState = RecapFixtures.ready, share: RecapShareState? = null, dark: Boolean = false) = rule.setContent {
        PuzzleBoxTheme(darkTheme = dark) {
            RecapScreen(state, "November 2026", gameName, onPage = {}, onWatchReplay = {}, onClose = {}, share = share)
        }
    }

    @Test
    fun `shows the cover beside the fact list with nothing selected, and the first fact once the story leaves the cover`() {
        show()

        rule.onNodeWithTag("recap.twoPane").assertIsDisplayed()
        rule.onNodeWithTag("recap.pager").assertIsDisplayed()
        rule.onNodeWithTag("recap.cover").assertIsDisplayed()
        rule.onNodeWithTag("recap.facts").assertIsDisplayed()
        rule.onNodeWithTag("recap.fact.0").assertIsNotSelected()
        rule.onNodeWithTag("recap.fact.1").assertIsNotSelected()

        rule.tapPastCover()

        rule.onNodeWithTag("recap.fact.0").assertIsSelected()
        rule.onNodeWithTag("recap.fact.1").assertIsNotSelected()
    }

    @Test
    fun `a fact opens its card in the story`() {
        show()

        rule.onNodeWithTag("recap.fact.1").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("recap.progress").assertContentDescriptionEquals("Card 2 of 2")
        rule.onNodeWithTag("recap.fact.1").assertIsSelected()
        rule.onNodeWithTag("recap.fact.0").assertIsNotSelected()
    }

    @Test
    fun `no fact is selected on the Share page, nor after Replay story`() {
        show(RecapFixtures.ready.copy(initialPage = 2), share = RecapShareState("November 2026", "Ivan", restoring = false, sharing = false, failed = false))
        rule.onNodeWithTag("recap.fact.0").assertIsNotSelected()
        rule.onNodeWithTag("recap.fact.1").assertIsNotSelected()

        rule.onNodeWithContentDescription("Replay story").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("recap.fact.0").assertIsNotSelected()
        rule.onNodeWithTag("recap.fact.1").assertIsNotSelected()
        rule.onNodeWithContentDescription("Replay story").assertDoesNotExist()
    }

    @Test
    fun `loading shows the skeleton in the left pane and empty rows in the fact pane`() {
        show(RecapUiState.Loading)

        rule.onNodeWithTag("recap.twoPane.left").assertIsDisplayed()
        rule.onNodeWithTag("recap.loading").assertContentDescriptionEquals("Loading your recap")
        rule.onNodeWithTag("recap.facts").assertDoesNotExist()
    }

    @Config(qualifiers = GOLDEN_TABLET_WIDE)
    @Test
    fun `on a wide tablet the story keeps its shape and the room left over goes to equal margins`() {
        show()

        val screen = rule.onRoot().getBoundsInRoot()
        val story = rule.onNodeWithTag("recap.twoPane.left").getBoundsInRoot()
        val facts = rule.onNodeWithTag("recap.twoPane.right").getBoundsInRoot()
        assertEquals(852f / 768f, story.width.value / story.height.value, 0.01f)
        assertEquals(story.left.value, (screen.right - facts.right).value, 1f)
    }

    @Test
    fun `the selected Fastest fact draws its dates in the card's ink, readable on its field in the dark`() {
        show(RecapFixtures.ready.copy(cards = listOf(RecapFixtures.solved, RecapFixtures.fastest), initialPage = 1), dark = true)
        rule.onNodeWithTag("recap.fact.1").assertIsSelected()

        val date = rule.onNode(
            hasText(RecapFixtures.fastest.rows.first().day.shortLabel(), substring = true) and hasAnyAncestor(hasTestTag("recap.fact.1")),
            useUnmergedTree = true,
        )
        val layout = mutableListOf<TextLayoutResult>()
        date.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layout)
        assertEquals(recapCardColors(PaletteEntry.Fastest, darkTheme = true).ink, layout.single().layoutInput.style.color)
    }
}
