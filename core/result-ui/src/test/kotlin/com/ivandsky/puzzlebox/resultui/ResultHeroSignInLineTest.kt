package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.gameui.GameBrand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The hero's sign-in line (Result hero polish): when the model has it, where it sits and that a tap starts sign-in. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ResultHeroSignInLineTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var signIns = 0

    private fun facts(rank: ResultRank = ResultRank.SignedOut, line: Boolean = true, playAgain: Boolean = false, practice: Boolean = false) =
        heroSampleFacts(rank = rank, playAgain = playAgain, practice = practice).copy(signInLine = line)

    private fun show(facts: ResultHeroFacts, widthDp: Int, heightDp: Int) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ResultHeroScreen(
                    facts = facts,
                    onSignIn = { signIns++ },
                    modifier = Modifier.requiredSize(widthDp.dp, heightDp.dp),
                    brand = GameBrand(AccentSlot.Violet, GameGlyphs.Queens),
                )
            }
        }
    }

    @Test
    fun `a signed-out counting solve with the line on carries the sign-in invitation`() {
        assertEquals(HERO_SIGN_IN_LINE, resultHeroModel(facts()).signInLine)
    }

    @Test
    fun `the line is off by default, for a signed-in player, a play again and a practice puzzle`() {
        assertNull(resultHeroModel(facts(line = false)).signInLine)
        assertNull(resultHeroModel(facts(rank = ResultRank.Ranked(position = 3, count = 40, topPercent = 8))).signInLine)
        assertNull(resultHeroModel(facts(playAgain = true)).signInLine)
        assertNull(resultHeroModel(facts(practice = true)).signInLine)
    }

    @Test
    fun `tapping the line on a phone starts sign-in`() {
        show(facts(), widthDp = 411, heightDp = 891)

        composeRule.onNodeWithTag("result.hero.signin").assertIsDisplayed().assertTextEquals(HERO_SIGN_IN_LINE).performClick()

        assertEquals(1, signIns)
    }

    @Test
    fun `tapping the line on a tablet starts sign-in`() {
        show(facts(), widthDp = 1280, heightDp = 800)

        composeRule.onNodeWithTag("result.hero.signin").performClick()

        assertEquals(1, signIns)
    }

    @Test
    fun `the line sits under the time, above the chips and the actions, with a 48 dp touch target`() {
        show(facts(), widthDp = 411, heightDp = 891)

        val time = composeRule.onNodeWithTag("result.hero.time").getUnclippedBoundsInRoot()
        val line = composeRule.onNodeWithTag("result.hero.signin").assertHeightIsAtLeast(48.dp).getUnclippedBoundsInRoot()
        val chip = composeRule.onNodeWithTag("result.hero.chip.hints").getUnclippedBoundsInRoot()
        assertTrue("line ${line.top} under time ${time.bottom}", line.top >= time.bottom)
        assertTrue("chips ${chip.top} under line ${line.bottom}", chip.top >= line.bottom)
    }

    @Test
    fun `with the line off a signed-out hero has no sign-in line`() {
        show(facts(line = false), widthDp = 411, heightDp = 891)
        composeRule.onNodeWithTag("result.hero.signin").assertDoesNotExist()
    }

    @Test
    fun `a signed-in hero has no sign-in line`() {
        show(facts(rank = ResultRank.Ranked(position = 3, count = 40, topPercent = 8)), widthDp = 411, heightDp = 891)
        composeRule.onNodeWithTag("result.hero.signin").assertDoesNotExist()
    }
}
