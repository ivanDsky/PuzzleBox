package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.game.BestScore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The side card's states: the streak title, which week dots are solved, the "You" label on the
 * bin that holds the solve, the ghost bars of a first solve, the sign-in button of a signed-out
 * player, and no rank row while it is hidden. Assertions ride tags and the model's own
 * strings; the chart's parts sit under its one description, so they are read unmerged.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ResultSideCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val brand = GameBrand(AccentSlot.Violet, GameGlyphs.Queens)
    private var signIns = 0

    private fun setContent(model: ResultHeroModel) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ResultSideCard(model = model, brand = brand, onSignIn = { signIns++ })
            }
        }
    }

    private fun stateIs(expected: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected)

    @Test
    fun `the streak title and sub line read from the model`() {
        val model = heroSampleModel(streak = 3)
        setContent(model)

        composeRule.onNodeWithTag("result.side.streak").assertTextEquals("3 day streak")
        composeRule.onNodeWithTag("result.side.streak.sub").assertTextEquals(model.streak!!.sub)
    }

    @Test
    fun `a long streak still reads in one line`() {
        setContent(heroSampleModel(streak = 365))

        composeRule.onNodeWithTag("result.side.streak").assertTextEquals("365 day streak")
    }

    @Test
    fun `the week dots mark the solved days`() {
        val model = heroSampleModel(streak = 3)
        setContent(model)

        val week = model.streak!!.week
        assertEquals(7, week.size)
        week.forEachIndexed { index, day ->
            composeRule.onNodeWithTag("result.side.week.$index")
                .assert(stateIs(if (day.solved) WEEK_SOLVED else WEEK_OPEN))
        }
        assertEquals(listOf(false, false, false, false, true, true, true), week.map { it.solved })
    }

    @Test
    fun `the You label and the full bar sit on the bin that holds the solve`() {
        val model = heroSampleModel(timeMs = 100_000L)
        setContent(model)

        val spread = model.spread!!
        val you = spread.youBin!!
        composeRule.onNodeWithTag("result.side.you", useUnmergedTree = true).assertTextEquals("You")
        composeRule.onNodeWithTag("result.side.bar.you", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("You", useUnmergedTree = true).assertCountEquals(1)
        spread.bins.forEachIndexed { index, bin ->
            val dim = composeRule.onNodeWithTag("result.side.bar.$index", useUnmergedTree = true)
            if (bin.count == 0 || index == you) dim.assertDoesNotExist() else dim.assertIsDisplayed()
        }
    }

    @Test
    fun `the chart reads aloud as one description`() {
        val model = heroSampleModel()
        setContent(model)

        composeRule.onNodeWithTag("result.side.chart")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(model.spread!!.description)))
    }

    @Test
    fun `the caption and the axis read from the model`() {
        val model = heroSampleModel(timeMs = 100_000L)
        setContent(model)

        val spread = model.spread!!
        composeRule.onNodeWithTag("result.side.caption").assertTextEquals(spread.caption!!.text)
        composeRule.onNodeWithTag("result.side.axis.low", useUnmergedTree = true).assertTextEquals(spread.axisLow)
        composeRule.onNodeWithTag("result.side.axis.high", useUnmergedTree = true).assertTextEquals(spread.axisHigh)
        composeRule.onNodeWithTag("result.side.heading").assertTextEquals(spread.heading)
    }

    @Test
    fun `a first solve draws ghost bars, the faster and slower axis and the first time line`() {
        val model = heroSampleModel(
            verdict = RecordVerdict.FirstSolve,
            previous = null,
            earlier = emptyList(),
            streak = 1,
        )
        setContent(model)

        composeRule.onNodeWithTag("result.side.bar.ghost.0", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("result.side.bar.ghost.9", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("result.side.axis.low", useUnmergedTree = true).assertTextEquals("faster")
        composeRule.onNodeWithTag("result.side.axis.high", useUnmergedTree = true).assertTextEquals("slower")
        composeRule.onNodeWithTag("result.side.first").assertTextEquals(HERO_FIRST_TIME_LINE)
        composeRule.onNodeWithTag("result.side.you", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a played solve draws no ghost bars`() {
        setContent(heroSampleModel())

        composeRule.onNodeWithTag("result.side.bar.ghost.0", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("result.side.first").assertDoesNotExist()
    }

    @Test
    fun `a ranked player sees the place and its sub line and no sign in`() {
        val model = heroSampleModel(rank = ResultRank.Ranked(position = 14, count = 212, topPercent = 7))
        setContent(model)

        val rank = model.rank as HeroRank.Place
        composeRule.onNodeWithTag("result.side.rank").assertIsDisplayed()
        composeRule.onAllNodesWithText(rank.value).assertCountEquals(1)
        composeRule.onAllNodesWithText(rank.sub).assertCountEquals(1)
        composeRule.onNodeWithTag("result.side.rank.signin").assertDoesNotExist()
    }

    @Test
    fun `connections show their combined place under the PuzzleBox rank`() {
        val model = heroSampleModel(
            rank = ResultRank.Ranked(position = 14, count = 212, topPercent = 7, withConnections = ResultRank.WithConnections(3, 12)),
        )
        setContent(model)

        val rank = model.rank as HeroRank.Place
        composeRule.onAllNodesWithText(rank.value).assertCountEquals(1)
        composeRule.onAllNodesWithText(rank.sub).assertCountEquals(1)
        composeRule.onNodeWithTag("result.side.rank.connections").assertIsDisplayed().assertTextEquals("3rd of 12 with connections")
    }

    @Test
    fun `without connections the rank row has no connections line`() {
        setContent(heroSampleModel(rank = ResultRank.Ranked(position = 14, count = 212, topPercent = 7)))

        composeRule.onNodeWithTag("result.side.rank.connections").assertDoesNotExist()
    }

    @Test
    fun `a signed out player gets the prompt and a Sign in button that calls back`() {
        val model = heroSampleModel(rank = ResultRank.SignedOut)
        setContent(model)

        composeRule.onAllNodesWithText((model.rank as HeroRank.SignIn).text).assertCountEquals(1)
        composeRule.onNodeWithTag("result.side.rank.signin").assertIsDisplayed().performClick()
        assertEquals(1, signIns)
    }

    @Test
    fun `no rank row while the rank is hidden`() {
        setContent(heroSampleModel(rank = ResultRank.Hidden))

        composeRule.onNodeWithTag("result.side.rank").assertDoesNotExist()
        composeRule.onNodeWithTag("result.side.streak").assertIsDisplayed()
    }

    @Test
    fun `a replay shows the spread but no streak and no rank row`() {
        setContent(heroSampleModel(verdict = RecordVerdict.Replay, replay = true))

        composeRule.onNodeWithTag("result.side.chart").assertIsDisplayed()
        composeRule.onNodeWithTag("result.side.streak").assertDoesNotExist()
        composeRule.onNodeWithTag("result.side.rank").assertDoesNotExist()
    }

    @Test
    fun `a practice puzzle draws no side card`() {
        setContent(heroSampleModel(verdict = null, practice = true, previous = BestScore(90_000L, 0)))

        composeRule.onNodeWithTag("result.side.streak").assertDoesNotExist()
        composeRule.onNodeWithTag("result.side.chart").assertDoesNotExist()
        composeRule.onNodeWithTag("result.side.rank").assertDoesNotExist()
    }
}
