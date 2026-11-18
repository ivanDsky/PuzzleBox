package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.resultui.board.DailyBoardState
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeVisibilityRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The share-replays question beside the hub's other sheets, on the real [ReplayOptInViewModel] wired as the route wires
 * it: with SheetScreens on it never lands on top of Tune or How ranks work and shows once they close; off, as before.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RanksSheetHoldTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val hub = RanksUiState(RanksBoardSlice("Zip · today", RankOrder.TimeThenHints, DailyBoardState.Loading))

    private val visibility = FakeVisibilityRepository()

    private fun show(flags: FakeReleaseFlags) {
        val viewModel = ReplayOptInViewModel(visibility, flags)
        viewModel.rankedRow(true)
        composeRule.setContent {
            val ask by viewModel.show.collectAsStateWithLifecycle()
            PuzzleBoxTheme {
                RanksScreen(
                    hub,
                    RanksActions(onSignIn = {}, onSwipe = {}, onSheetOpen = viewModel::hold),
                    replayOptIn = ask,
                    tuneDefaults = previewTuneDefaults(),
                )
            }
        }
    }

    private fun openTune() {
        composeRule.onNodeWithTag("ranks.tune").performClick()
        composeRule.onNodeWithTag("ranks.tune.sheet").assertIsDisplayed()
    }

    private fun closeTune() {
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss)
        composeRule.waitForIdle()
    }

    private fun questionCount() = composeRule.onAllNodesWithTag("ranks.optin.sheet").fetchSemanticsNodes().size

    @Test
    fun `with SheetScreens on, the question that turns up over Tune waits and shows when Tune closes`() {
        show(FakeReleaseFlags(ReleaseFlag.SheetScreens))
        openTune()

        visibility.emitOwn(OwnTier.Unset)
        composeRule.waitForIdle()
        assertEquals(0, questionCount())

        closeTune()
        composeRule.onNodeWithTag("ranks.optin.sheet").assertIsDisplayed()
    }

    @Test
    fun `with SheetScreens off, the question shows over Tune as today`() {
        show(FakeReleaseFlags())
        openTune()

        visibility.emitOwn(OwnTier.Unset)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ranks.optin.sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("ranks.tune.sheet").assertIsDisplayed()
    }
}
