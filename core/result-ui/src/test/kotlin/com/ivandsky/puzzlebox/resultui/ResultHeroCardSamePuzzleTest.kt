package com.ivandsky.puzzlebox.resultui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.gameui.GameBrand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The hero card with the same-puzzle comparison: the compare columns, and the line's type at about half the time's. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ResultHeroCardSamePuzzleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(samePuzzle: SamePuzzleSolves?, wide: Boolean = false): ResultHeroModel {
        val model = resultHeroModel(heroSampleFacts(timeMs = 102_000L).copy(samePuzzle = samePuzzle))
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ResultHeroCard(model = model, brand = GameBrand(AccentSlot.Violet, GameGlyphs.Queens), wide = wide)
            }
        }
        return model
    }

    private fun fontSizeSp(tag: String): Float {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single().layoutInput.style.fontSize.value
    }

    @Test
    fun `with no earlier solve the line is drawn at about half the size of the time`() {
        show(SamePuzzleSolves.None)

        val ratio = fontSizeSp("result.hero.best") / fontSizeSp("result.hero.time")
        assertTrue("line is $ratio of the time", ratio in 0.4f..0.6f)
    }

    @Test
    fun `without the same-puzzle facts the line keeps its body size`() {
        show(samePuzzle = null)

        assertEquals(16f, fontSizeSp("result.hero.best"), 0.01f)
    }

    private val columns = SamePuzzleSolves(BestScore(114_000L, 0), best = BestScore(101_000L, 0))

    @Test
    fun `the columns replace the best line`() {
        show(columns)

        composeRule.onNodeWithTag("result.hero.best").assertDoesNotExist()
        composeRule.onNodeWithTag("result.hero.previous").assertTextEquals("1:54")
        composeRule.onNodeWithTag("result.hero.gap").assertTextEquals("\u221212s")
        composeRule.onNodeWithTag("result.hero.compareBest").assertTextEquals("1:41")
    }

    @Test
    fun `the column values are exactly half the hero time on a phone and wide`() {
        show(columns)
        assertEquals(38f, fontSizeSp("result.hero.previous"), 0.01f)
        assertEquals(76f, fontSizeSp("result.hero.time"), 0.01f)
    }

    @Test
    fun `the column values are 48 sp on wide`() {
        show(columns, wide = true)
        assertEquals(48f, fontSizeSp("result.hero.compareBest"), 0.01f)
        assertEquals(96f, fontSizeSp("result.hero.time"), 0.01f)
    }

    private fun baselineY(tag: String): Float {
        val node = composeRule.onNodeWithTag(tag).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return node.positionInRoot.y + layouts.single().firstBaseline
    }

    @Test
    fun `the tag and BEST sit on the baseline of PREVIOUS`() {
        show(columns)

        assertEquals(baselineY("result.hero.previous"), baselineY("result.hero.gap"), 0.5f)
        assertEquals(baselineY("result.hero.previous"), baselineY("result.hero.compareBest"), 0.5f)
    }

    @Test
    fun `no BEST column when the best is the previous solve`() {
        show(columns.copy(best = BestScore(114_000L, 0)))

        composeRule.onNodeWithTag("result.hero.compareBest").assertDoesNotExist()
    }
}
