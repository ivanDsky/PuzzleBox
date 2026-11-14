package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.colors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tablet's hero card as approved on build .5: one compact line (F13), whose place and score go from the board the
 * hub leaves to the one it arrives at as far as the scrub is (F16).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class RanksHeroCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var face by mutableStateOf(HeroFace("Zip", Standing.Place(5), "120 pts"))
    private var was by mutableStateOf<HeroFace?>(null)
    private var progress by mutableFloatStateOf(1f)

    private fun show() = composeRule.setContent {
        PuzzleBoxTheme(darkTheme = false) {
            val accent = AccentSlot.Blue.colors()
            RanksHeroCard(face, { accent.container }, { accent.onContainer }, was = was, progress = { progress })
        }
    }

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag).getBoundsInRoot()

    private fun scoreWidth() = bounds("ranks.hero.score").width

    // The place's own top, wherever the roll has it: the roll clips it while it is on its way.
    private fun placeTop() = composeRule.onNodeWithTag("ranks.hero.place").getUnclippedBoundsInRoot().top

    @Test
    fun `the card is one compact line, the place beside the board's name over the score`() {
        show()

        val card = bounds("ranks.hero")
        val place = bounds("ranks.hero.place")
        val name = composeRule.onNodeWithText("Zip").getBoundsInRoot()
        val score = bounds("ranks.hero.score")
        // 8 dp over and under the place: one 40 sp line of Baloo 2, which is 1.6 em tall.
        assertEquals(80.dp, card.height)
        assertEquals(card.top + 8.dp, place.top)
        assertEquals(card.bottom - 8.dp, place.bottom)
        assertEquals(16.dp, name.left - place.right)
        assertEquals(name.left, score.left)
        assertEquals(name.bottom, score.top)
        composeRule.onAllNodesWithText("Your place").assertCountEquals(0)
    }

    @Test
    fun `between two boards the score is as wide as the scrub is far along`() {
        show()
        val arrived = scoreWidth()
        face = face.copy(score = "8 pts")
        val left = scoreWidth()
        face = face.copy(score = "120 pts")
        was = HeroFace("Overall", Standing.Place(5), "8 pts")

        progress = 0f
        val atStart = scoreWidth()
        progress = 0.5f
        val halfway = scoreWidth()
        progress = 1f
        val atEnd = scoreWidth()

        assertTrue("120 pts is wider than 8 pts: $arrived > $left", arrived > left)
        assertEquals(left, atStart)
        // Halfway the roll is between 56 and 72: two digits, between the one of 8 and the three of 120.
        assertTrue("halfway is between the two: $left < $halfway < $arrived", halfway > left && halfway < arrived)
        assertEquals(arrived, atEnd)
    }

    @Test
    fun `between two places the place rolls up into view as far as the scrub is`() {
        show()
        val rest = placeTop()
        was = face.copy(standing = Standing.Place(3))

        progress = 0f
        val atStart = placeTop()
        progress = 0.5f
        val halfway = placeTop()
        progress = 1f
        val atEnd = placeTop()

        // The roll is #3, #4, #5 in cells one 64 dp line tall: #5 starts two cells under the card's line and ends on it.
        assertEquals(rest + 128.dp, atStart)
        assertEquals(rest + 64.dp, halfway)
        assertEquals(rest, atEnd)
    }
}
