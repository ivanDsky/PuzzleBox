package com.ivandsky.puzzlebox.designsystem.community

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Semantics and sizes of the community cards: one button per card that reads its own copy, a
// touch target of at least 48dp, and the handoff widths.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class CommunityCardSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private companion object {
        const val CARD = "card"
    }

    private fun show(widthDp: Int = 360, content: @Composable (Modifier) -> Unit) {
        composeRule.setContent {
            PuzzleBoxTheme { Box(Modifier.width(widthDp.dp)) { content(Modifier.testTag(CARD)) } }
        }
    }

    private fun isButton() = SemanticsMatcher("has the button role") {
        it.config.getOrNull(SemanticsProperties.Role) == Role.Button
    }

    private fun shelf(
        modifier: Modifier,
        badges: List<ShelfBadge> = emptyList(),
        selected: Boolean = false,
        onClick: () -> Unit = {},
        onLongClick: (() -> Unit)? = null,
        title: String = "Spiral staircase",
        author: String = "Mia",
        solves: Int? = 132,
    ) = @Composable {
        ShelfCard(
            title = title,
            author = author,
            solves = solves,
            accent = AccentSlot.Blue,
            glyph = GameGlyphs.Zip,
            onClick = onClick,
            modifier = modifier,
            difficulty = "Hard",
            badges = badges,
            selected = selected,
            onLongClick = onLongClick,
            onLongClickLabel = "Report",
        )
    }

    @Test
    fun `a shelf card is one button 156dp wide and at least 48dp tall`() {
        show { shelf(it)() }
        composeRule.onNodeWithTag(CARD)
            .assert(isButton())
            .assertHasClickAction()
            .assertWidthIsEqualTo(156.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `a shelf card is 120dp of art above its two text lines`() {
        show { shelf(it)() }
        val height = composeRule.onNodeWithTag(CARD).getUnclippedBoundsInRoot().height
        assertTrue("art 120dp plus text, got $height", height > 120.dp)
    }

    @Test
    fun `tapping a shelf card clicks once and a long press fires the long click`() {
        var clicks = 0
        var longs = 0
        show { shelf(it, onClick = { clicks++ }, onLongClick = { longs++ })() }
        composeRule.onNodeWithTag(CARD).performClick()
        assertEquals(1, clicks)
        composeRule.onNodeWithTag(CARD).performTouchInput { longClick() }
        assertEquals(1, longs)
    }

    @Test
    fun `a shelf card reads its title, byline, solves and both badges as one node`() {
        show { shelf(it, badges = listOf(ShelfBadge.New, ShelfBadge.Friend))() }
        val node = composeRule.onNodeWithTag(CARD)
        node.assert(hasText("Spiral staircase", substring = true))
        node.assert(hasText("Mia · Hard", substring = true))
        node.assert(hasText("NEW", substring = true))
        node.assert(hasText("Friend", substring = true))
        node.assert(hasContentDescription("132 solves", substring = true))
    }

    @Test
    fun `a shelf card with no solve count draws no solves chip`() {
        show { shelf(it, solves = null)() }
        composeRule.onNodeWithTag(CARD).assert(hasText("Spiral staircase", substring = true))
        composeRule.onAllNodes(hasContentDescription("solves", substring = true), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `a shelf card with a solve count draws the chip`() {
        show { shelf(it, solves = 0)() }
        composeRule.onNodeWithTag(CARD).assert(hasContentDescription("0 solves", substring = true))
    }

    @Test
    fun `a selected shelf card is selected for accessibility`() {
        show { shelf(it, selected = true)() }
        composeRule.onNodeWithTag(CARD).assertIsSelected()
    }

    @Test
    fun `the longest copy keeps the card at 156dp wide`() {
        show {
            shelf(
                it,
                title = "The very long name of a puzzle that wraps over two lines and then keeps going",
                author = "An author with an extremely long display name",
                solves = 9876,
            )()
        }
        composeRule.onNodeWithTag(CARD).assertWidthIsEqualTo(156.dp)
    }

    @Test
    fun `the spotlight is one button at least 48dp tall with its kicker`() {
        show {
            SpotlightCard(
                title = "Spiral staircase",
                gameName = "Zip",
                author = "Mia",
                solves = 4120,
                accent = AccentSlot.Blue,
                glyph = GameGlyphs.Zip,
                onClick = {},
                modifier = it,
                difficulty = "Hard",
            )
        }
        composeRule.onNodeWithTag(CARD)
            .assert(isButton())
            .assertHasClickAction()
            .assertHeightIsAtLeast(112.dp)
            .assert(hasText("#1 MOST PLAYED", substring = true))
            .assert(hasText("Zip · Hard · by Mia", substring = true))
    }

    @Test
    fun `an entry tile is one button at least 64dp tall`() {
        var clicks = 0
        show {
            EntryTile(title = "Your puzzles", subtitle = "4 published", onClick = { clicks++ }, modifier = it, leading = {})
        }
        composeRule.onNodeWithTag(CARD)
            .assert(isButton())
            .assertHeightIsAtLeast(64.dp)
            .assert(hasText("Your puzzles", substring = true))
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `an entry tile subtitle stays on one line in a 159dp tile`() {
        show(widthDp = 159) {
            EntryTile(
                title = "Your puzzles",
                subtitle = "4 published \u00b7 2 drafts",
                onClick = {},
                modifier = it,
                leading = { Box(Modifier.size(40.dp)) },
            )
        }
        // One line measures about 20dp here (16sp line height); a wrapped subtitle is about 40dp.
        val height = composeRule.onNodeWithText("4 published", substring = true, useUnmergedTree = true).getUnclippedBoundsInRoot().height
        assertTrue("one line is about 20dp, got $height", height < 30.dp)
    }

    @Test
    fun `the spotlight play glyph sits flush with the text column`() {
        show {
            SpotlightCard(
                title = "Spiral staircase",
                gameName = "Zip",
                author = "Mia",
                solves = 4120,
                accent = AccentSlot.Blue,
                glyph = GameGlyphs.Zip,
                onClick = {},
                modifier = it,
                difficulty = "Hard",
            )
        }
        val column = composeRule.onNodeWithText("Spiral staircase", useUnmergedTree = true).getBoundsInRoot().left
        val row = composeRule.onNode(hasContentDescription("4,120 solves"), useUnmergedTree = true).getBoundsInRoot().left
        // The Rounded play triangle starts 8 of 24 units into its 16dp box, so the box sits 5.33dp left of the column.
        val glyphLeft = row + ShelfCardDefaults.PlayGlyphInset
        assertEquals("glyph left ${glyphLeft.value} vs column ${column.value}", column.value, glyphLeft.value, 0.5f)
    }

    @Test
    fun `a make card is a 132dp wide button by default and 200dp wide in the make row`() {
        show { MakePuzzleCard(gameName = "Zip", onClick = {}, modifier = it) }
        composeRule.onNodeWithTag(CARD)
            .assert(isButton())
            .assertWidthIsEqualTo(132.dp)
            .assertHeightIsAtLeast(120.dp)
            .assert(hasText("Make a Zip puzzle", substring = true))
    }

    @Test
    fun `a make card takes the wide width for an empty shelf`() {
        show { MakePuzzleCard(gameName = "Mini Sudoku", onClick = {}, modifier = it.width(ShelfCardDefaults.MakeRowCardWidth)) }
        composeRule.onNodeWithTag(CARD).assertWidthIsEqualTo(200.dp)
    }

    @Test
    fun `author avatars overlap and stop at three discs`() {
        show { AuthorAvatars(initials = listOf('M', 'T', 'A', 'B'), modifier = it) }
        // Three 28dp discs overlapping by 8dp: 3 * 28 - 2 * 8.
        val width = composeRule.onNodeWithTag(CARD).getUnclippedBoundsInRoot().width
        assertTrue("68dp (three discs), got $width", width > 67.dp && width < 70.dp)
    }
}
