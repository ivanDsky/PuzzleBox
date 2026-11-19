package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.gameui.ResultHeroChip
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The hero card's states: the time, the best line, the pill for a new best and a first solve
 * (and none for a plain solve), the hint chip, and the Next, Share and Play again actions.
 * Assertions ride tags and the model's own strings, so a copy edit moves one place.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ResultHeroCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val brand = GameBrand(AccentSlot.Violet, GameGlyphs.Queens)
    private var shared = 0
    private var next = 0
    private var again = 0

    private fun setContent(
        model: ResultHeroModel,
        rolloverAt: Instant? = null,
        playAgain: Boolean = true,
        wide: Boolean = false,
        withActions: Boolean = true,
        width: Dp = 360.dp,
    ) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ResultHeroCard(
                    model = model,
                    brand = brand,
                    modifier = Modifier.requiredWidth(width),
                    wide = wide,
                    actions = if (withActions) {
                        {
                            ResultHeroActions(
                                primary = model.primary,
                                onPrimary = { next++ },
                                onShare = { shared++ },
                                onPlayAgain = if (playAgain) ({ again++ }) else null,
                                rolloverAt = rolloverAt,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }

    @Test
    fun `a new best shows its time, its improved line, the pill and the chips`() {
        val model = heroSampleModel(
            verdict = RecordVerdict.NewBest,
            timeMs = 58_000L,
            previous = BestScore(65_000L, 0),
        )
        setContent(model)

        composeRule.onNodeWithTag("result.hero.time").assertTextEquals("0:58")
        composeRule.onNodeWithTag("result.hero.best").assertTextEquals(model.bestLine!!)
        composeRule.onNodeWithTag("result.hero.pill").assertTextEquals("New best")
        composeRule.onNodeWithTag("result.hero.title").assertTextEquals(model.title)
        composeRule.onNodeWithTag("result.hero.date").assertTextEquals(model.dateLine)
        composeRule.onNodeWithTag("result.hero.chip.hints").assertIsDisplayed()
        composeRule.onNodeWithTag("result.hero.chip.today").assertIsDisplayed()
        composeRule.onNodeWithTag("result.hero.chip.average").assertIsDisplayed()
    }

    @Test
    fun `a first solve shows the pill and no average chip`() {
        val model = heroSampleModel(verdict = RecordVerdict.FirstSolve, previous = null, earlier = emptyList())
        setContent(model)

        composeRule.onNodeWithTag("result.hero.pill").assertTextEquals("First solve")
        composeRule.onNodeWithTag("result.hero.best").assertTextEquals(model.bestLine!!)
        composeRule.onNodeWithTag("result.hero.chip.average").assertDoesNotExist()
    }

    @Test
    fun `a plain solve has no pill but names the best to chase`() {
        val model = heroSampleModel(verdict = RecordVerdict.NotBest, timeMs = 100_000L)
        setContent(model)

        composeRule.onNodeWithTag("result.hero.pill").assertDoesNotExist()
        composeRule.onNodeWithTag("result.hero.best").assertTextEquals(model.bestLine!!)
    }

    @Test
    fun `a hints-first new best words its line from the hints`() {
        val model = heroSampleModel(
            verdict = RecordVerdict.NewBest,
            order = RankOrder.HintsThenTime,
            game = "Strands",
            hintsUsed = 0,
            timeMs = 142_000L,
            previous = BestScore(150_000L, 2),
        )
        setContent(model)

        composeRule.onNodeWithTag("result.hero.time").assertTextEquals("2:22")
        composeRule.onNodeWithTag("result.hero.best").assertTextEquals(model.bestLine!!)
        composeRule.onNodeWithTag("result.hero.chip.hints").assertIsDisplayed()
    }

    @Test
    fun `an hour-plus time and twelve hints keep the card whole`() {
        val model = heroSampleModel(
            verdict = RecordVerdict.NotBest,
            timeMs = 3_723_000L,
            hintsUsed = 12,
            game = "A very long game name that keeps going",
            previous = BestScore(3_000_000L, 0),
        )
        setContent(model, wide = true)

        composeRule.onNodeWithTag("result.hero.time").assertTextEquals("1:02:03")
        composeRule.onNodeWithTag("result.hero.chip.hints").assertIsDisplayed()
        composeRule.onNodeWithTag("result.next").assertIsDisplayed()
    }

    @Test
    fun `next, share and play again hand back`() {
        setContent(heroSampleModel(verdict = RecordVerdict.NotBest))

        composeRule.onNodeWithTag("result.next").performClick()
        composeRule.onNodeWithTag("result.share").performClick()
        composeRule.onNodeWithTag("result.playAgain").performClick()

        assertEquals(listOf(1, 1, 1), listOf(next, shared, again))
    }

    @Test
    fun `share and play again show their labels and keep their names`() {
        setContent(heroSampleModel(verdict = RecordVerdict.NotBest))

        composeRule.onNodeWithTag("result.share")
            .assertTextEquals("Share")
            .assertContentDescriptionEquals("Share")
            .assertHasClickAction()
        composeRule.onNodeWithTag("result.playAgain")
            .assertTextEquals("Play again")
            .assertContentDescriptionEquals("Play again")
            .assertHasClickAction()
    }

    @Test
    fun `on a phone, share and play again sit side by side under the primary, 48 dp tall`() {
        setContent(heroSampleModel(verdict = RecordVerdict.NotBest))

        val primary = composeRule.onNodeWithTag("result.next").getUnclippedBoundsInRoot()
        val share = composeRule.onNodeWithTag("result.share").getUnclippedBoundsInRoot()
        val again = composeRule.onNodeWithTag("result.playAgain").getUnclippedBoundsInRoot()
        assertTrue("share ${share.top} must sit under primary ${primary.bottom}", share.top >= primary.bottom)
        assertEquals("play again shares share's row", share.top.value, again.top.value, 0.5f)
        assertTrue("play again ${again.left} must sit right of share ${share.right}", again.left >= share.right)
        assertEquals("the pair spans the primary's width", primary.left.value, share.left.value, 0.5f)
        assertEquals("the pair spans the primary's width", primary.right.value, again.right.value, 0.5f)
        assertTrue("play again takes the rest of the row", (again.right - again.left) > (share.right - share.left))
        assertEquals(48f, share.height.value, 0.5f)
        assertEquals(48f, again.height.value, 0.5f)
    }

    @Test
    fun `on a wide card the pair still stacks under the primary`() {
        setContent(heroSampleModel(verdict = RecordVerdict.NotBest), width = 640.dp)

        val primary = composeRule.onNodeWithTag("result.next").getUnclippedBoundsInRoot()
        val share = composeRule.onNodeWithTag("result.share").getUnclippedBoundsInRoot()
        val again = composeRule.onNodeWithTag("result.playAgain").getUnclippedBoundsInRoot()
        assertTrue("share ${share.top} must sit under primary ${primary.bottom}", share.top >= primary.bottom)
        assertEquals("play again shares share's row", share.top.value, again.top.value, 0.5f)
        assertTrue("play again ${again.left} must sit right of share ${share.right}", again.left >= share.right)
        assertEquals(48f, share.height.value, 0.5f)
    }

    @Test
    fun `the countdown stands above the share and play again row`() {
        setContent(
            heroSampleModel(verdict = RecordVerdict.NotBest, primary = ResultPrimaryAction.AllDone),
            rolloverAt = Instant.now().plusSeconds(3 * 3600L),
        )

        val countdown = composeRule.onNodeWithTag("result.all_done").getUnclippedBoundsInRoot()
        val share = composeRule.onNodeWithTag("result.share").getUnclippedBoundsInRoot()
        assertTrue("share ${share.top} must sit under countdown ${countdown.bottom}", share.top >= countdown.bottom)
        assertEquals(48f, share.height.value, 0.5f)
    }

    @Test
    fun `a play-again offers done, and play again hides when not given`() {
        setContent(heroSampleModel(verdict = RecordVerdict.PlayAgain, primary = ResultPrimaryAction.Done), playAgain = false)

        composeRule.onNodeWithTag("result.done").assertIsDisplayed()
        composeRule.onNodeWithTag("result.playAgain").assertDoesNotExist()
    }

    @Test
    fun `when everything is solved the countdown stands in for next`() {
        setContent(
            heroSampleModel(verdict = RecordVerdict.NotBest, primary = ResultPrimaryAction.AllDone),
            rolloverAt = Instant.now().plusSeconds(3 * 3600L),
        )

        composeRule.onNodeWithTag("result.all_done").assertIsDisplayed()
        composeRule.onNodeWithTag("result.countdown").assertIsDisplayed()
        composeRule.onNodeWithTag("result.next").assertDoesNotExist()
    }

    @Test
    fun `a pending primary hides the actions from touch and semantics`() {
        setContent(heroSampleModel(verdict = RecordVerdict.NotBest, primary = null))

        composeRule.onNodeWithTag("result.next").assertDoesNotExist()
        composeRule.onNodeWithTag("result.hero.time").assertIsDisplayed()
    }

    @Test
    fun `no actions slot draws no action buttons`() {
        setContent(heroSampleModel(verdict = RecordVerdict.NotBest), withActions = false)

        composeRule.onNodeWithTag("result.next").assertDoesNotExist()
        composeRule.onNodeWithTag("result.share").assertDoesNotExist()
        composeRule.onNodeWithTag("result.playAgain").assertDoesNotExist()
    }

    @Test
    fun `a supplied chip replaces the Today chip`() {
        val chip = ResultHeroChip("test.chip", "1 of 3", "Supplied")
        setContent(resultHeroModel(heroSampleFacts(), chip = chip))

        composeRule.onNodeWithTag("test.chip").assertIsDisplayed()
        composeRule.onNodeWithText(chip.value).assertIsDisplayed()
        composeRule.onNodeWithText(chip.label).assertIsDisplayed()
        composeRule.onNodeWithTag("result.hero.chip.today").assertDoesNotExist()
        composeRule.onNodeWithTag("result.hero.chip.hints").assertIsDisplayed()
    }

    @Test
    fun `without a supplied chip the Today chip stays`() {
        setContent(heroSampleModel(verdict = RecordVerdict.NotBest))

        composeRule.onNodeWithTag("result.hero.chip.today").assertIsDisplayed()
    }
}
