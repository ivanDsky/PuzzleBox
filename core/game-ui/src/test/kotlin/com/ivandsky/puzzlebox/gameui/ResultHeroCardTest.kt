package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import java.time.Instant
import org.junit.Assert.assertEquals
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
    ) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ResultHeroCard(
                    model = model,
                    brand = brand,
                    onPrimary = { next++ },
                    onShare = { shared++ },
                    onPlayAgain = if (playAgain) ({ again++ }) else null,
                    rolloverAt = rolloverAt,
                    wide = wide,
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
    fun `a replay offers done, and play again hides when not given`() {
        setContent(heroSampleModel(verdict = RecordVerdict.Replay, primary = ResultPrimaryAction.Done), playAgain = false)

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
}
