package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.BestScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The hero card with the same-puzzle comparison: its two lines, and the comparison's type at about half the time's. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ResultHeroCardSamePuzzleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(samePuzzle: SamePuzzleSolves?): ResultHeroModel {
        val model = resultHeroModel(heroSampleFacts(timeMs = 102_000L).copy(samePuzzle = samePuzzle))
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ResultHeroCard(model = model, brand = GameBrand(AccentSlot.Violet, GameGlyphs.Queens), wide = false)
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
    fun `the comparison and the best on this puzzle are both drawn`() {
        show(SamePuzzleSolves(BestScore(114_000L, 0), best = BestScore(101_000L, 0)))

        composeRule.onNodeWithTag("result.hero.best").assertTextEquals("12s faster than last time (1:54)")
        composeRule.onNodeWithTag("result.hero.bestOn").assertIsDisplayed().assertTextEquals("Best on this puzzle 1:41")
    }

    @Test
    fun `no second line when the best is the previous solve`() {
        show(SamePuzzleSolves(BestScore(114_000L, 0), best = BestScore(114_000L, 0)))

        composeRule.onNodeWithTag("result.hero.bestOn").assertDoesNotExist()
    }

    @Test
    fun `the comparison is drawn at about half the size of the time`() {
        show(SamePuzzleSolves(BestScore(114_000L, 0), best = BestScore(101_000L, 0)))

        val ratio = fontSizeSp("result.hero.best") / fontSizeSp("result.hero.time")
        assertTrue("comparison is $ratio of the time", ratio in 0.4f..0.6f)
    }

    @Test
    fun `the second line stays small`() {
        show(SamePuzzleSolves(BestScore(114_000L, 0), best = BestScore(101_000L, 0)))

        assertTrue(fontSizeSp("result.hero.bestOn") < fontSizeSp("result.hero.best"))
    }

    @Test
    fun `without the same-puzzle facts the line keeps its body size and no second line shows`() {
        show(samePuzzle = null)

        assertEquals(16f, fontSizeSp("result.hero.best"), 0.01f)
        composeRule.onNodeWithTag("result.hero.bestOn").assertDoesNotExist()
    }
}
