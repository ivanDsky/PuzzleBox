package com.ivandsky.puzzlebox.resultui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** L3 and L4: each next thing is the main button with its own words and tag; the Challenge row sits under it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ResultHeroActionsNextThingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var primaryTaps = 0
    private var challengeTaps = 0
    private val maya = ResultPrimaryAction.ChallengeFriend("u1", "Maya")
    private val queens = GameId("queens")
    private val code = ShareCode.parse("ABC123")!!

    private fun show(primary: ResultPrimaryAction, challenge: ResultPrimaryAction.ChallengeFriend? = null) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ResultHeroActions(
                    primary = primary,
                    onPrimary = { primaryTaps++ },
                    onShare = {},
                    onPlayAgain = {},
                    challenge = challenge,
                    onChallenge = { challengeTaps++ },
                )
            }
        }
    }

    private fun assertButton(primary: ResultPrimaryAction) {
        show(primary)
        val step = checkNotNull(primary.step(Locale.getDefault()))
        composeRule.onNodeWithTag(step.tag).assertTextEquals(step.text).assertHasClickAction().performClick()
        composeRule.runOnIdle { assertEquals(1, primaryTaps) }
    }

    @Test
    fun `beat a friend is the main button`() = assertButton(maya)

    @Test
    fun `a missed day is the main button`() = assertButton(ResultPrimaryAction.MissedDay(queens, LocalDate.of(2026, 11, 3), "Queens"))

    @Test
    fun `a friend's puzzle is the main button`() = assertButton(ResultPrimaryAction.FriendPuzzle(code, queens, "Maya"))

    @Test
    fun `a community puzzle is the main button`() = assertButton(ResultPrimaryAction.CommunityPuzzle(code, queens, "Sam"))

    @Test
    fun `the Challenge row names the friend, taps once and is a full touch target`() {
        show(ResultPrimaryAction.Next(queens, "Queens"), challenge = maya)
        composeRule.onNodeWithTag("result.challenge").assertTextEquals("Challenge Maya").assertHasClickAction()
        val bounds = composeRule.onNodeWithTag("result.challenge").getUnclippedBoundsInRoot()
        assertTrue("height ${bounds.height}", bounds.height.value >= 48f)
        composeRule.onNodeWithTag("result.challenge").performClick()
        composeRule.runOnIdle {
            assertEquals(1, challengeTaps)
            assertEquals(0, primaryTaps)
        }
    }

    @Test
    fun `the Challenge row is hidden when the main button already is the challenge`() {
        show(maya, challenge = maya)
        composeRule.onNodeWithTag("result.challengeMain").assertTextEquals("Challenge Maya")
        composeRule.onNodeWithTag("result.challenge").assertDoesNotExist()
    }

    @Test
    fun `without a challenge there is no row, and no Make one like this`() {
        show(ResultPrimaryAction.Next(queens, "Queens"))
        composeRule.onNodeWithTag("result.challenge").assertDoesNotExist()
        composeRule.onNodeWithTag("result.makeOne").assertDoesNotExist()
    }

    @Test
    fun `a Result with a challenge row has no Make one like this under it`() {
        show(ResultPrimaryAction.Next(queens, "Queens"), challenge = maya)
        composeRule.onNodeWithTag("result.makeOne").assertDoesNotExist()
    }
}
