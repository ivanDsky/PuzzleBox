package com.ivandsky.puzzlebox.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import com.ivandsky.puzzlebox.designsystem.LocalTabReselect
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.home.HomeScreenTest.Companion.TODAY
import com.ivandsky.puzzlebox.home.HomeScreenTest.Companion.row
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Today badge shows "Mini Sudoku" in full (A13): its name wraps onto a second line rather than
 * ellipsizing, at the 360 dp phone floor (compact badges) and on tablet. NATIVE graphics so the text
 * metrics are real.
 */
abstract class TodayBadgeNameScenes(private val layout: HomeLayout) {

    @get:Rule
    val composeRule = createComposeRule()

    protected fun assertNameShownInFull() {
        val today = TODAY.take(3) + row("mini", "Mini Sudoku", TodayStatus.NotStarted)
        composeRule.setContent {
            CompositionLocalProvider(LocalTabReselect provides MutableSharedFlow()) {
                PuzzleBoxTheme {
                    HomeScreen(
                        layout = layout,
                        state = HomeScreenTest.state(today, emptyList(), GameId("zip")),
                        onOpenGame = {},
                        onSelectDay = {},
                        modifier = Modifier,
                        title = "PuzzleBox",
                        summaries = HomeSummaries(onOpenRank = null, duels = null, onOpenDay = {}, onOpenStreak = {}),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val results = mutableListOf<TextLayoutResult>()
        val name = composeRule.onNodeWithText("Mini Sudoku", useUnmergedTree = true).fetchSemanticsNode()
        checkNotNull(name.config[SemanticsActions.GetTextLayoutResult].action).invoke(results)
        assertFalse("badge name ellipsized", results.single().hasVisualOverflow)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class TodayBadgeNamePhoneTest : TodayBadgeNameScenes(HomeLayout.SinglePane) {
    @Test fun `mini sudoku shows in full on the phone`() = assertNameShownInFull()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class TodayBadgeNameTabletTest : TodayBadgeNameScenes(HomeLayout.TwoPane) {
    @Test fun `mini sudoku shows in full on the tablet`() = assertNameShownInFull()
}
