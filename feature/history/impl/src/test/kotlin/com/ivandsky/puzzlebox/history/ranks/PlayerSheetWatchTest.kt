package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ranks.PlayerActions
import com.ivandsky.puzzlebox.domain.ranks.PlayerActions.Challenge
import com.ivandsky.puzzlebox.domain.ranks.PlayerActions.Watch
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The sheet's Watch tile: a tap fires only when the use case allowed it and the host wired it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PlayerSheetWatchTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val target = PlayerSheetViewModel.Target("maya", "Maya", Standing.Place(2), GameId("zip"), LocalDate.of(2026, 11, 13))

    private fun show(watch: Watch, onWatch: (() -> Unit)?) {
        val state = PlayerSheetViewModel.State(target, PlayerActions(Challenge.Allowed, watch))
        composeRule.setContent { PuzzleBoxTheme { PlayerSheet(state, onChallenge = {}, onDismiss = {}, onWatch = onWatch) } }
    }

    @Test
    fun `an allowed Watch fires its tap`() {
        var taps = 0
        show(Watch.Allowed, onWatch = { taps++ })

        composeRule.onNodeWithContentDescription("Watch").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Watch").performClick()

        assertEquals(1, taps)
    }

    @Test
    fun `a locked Watch stays disabled`() {
        show(Watch.SolveFirst, onWatch = {})

        composeRule.onNodeWithContentDescription("Watch").assertIsNotEnabled()
    }

    @Test
    fun `a Watch waiting on the viewer's solve says Solve it first`() {
        show(Watch.SolveFirst, onWatch = {})

        composeRule.onNodeWithTag("ranks.player.watch.caption").assertTextEquals("Solve it first")
    }
}
