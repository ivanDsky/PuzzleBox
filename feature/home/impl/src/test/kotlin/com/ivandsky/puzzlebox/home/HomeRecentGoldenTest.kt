package com.ivandsky.puzzlebox.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.LocalTabReselect
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.home.HomeScreenTest.Companion.TODAY
import com.ivandsky.puzzlebox.home.HomeScreenTest.Companion.row
import com.ivandsky.puzzlebox.model.GameId
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home with the Recent card loaded and empty, beside a today set that includes "Mini Sudoku" in
 * full (A13, A17): the card says one line, "Finished puzzles show here". Phone at the 360 dp floor
 * and tablet two-pane. NATIVE graphics. Goldens live in `feature/home/impl/snapshots/`.
 */
abstract class HomeRecentEmptyScenes(private val layout: HomeLayout, private val golden: String) {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `empty recent card golden`() {
        val today = TODAY.take(3) + row("mini", "Mini Sudoku", TodayStatus.NotStarted)
        composeRule.mainClock.autoAdvance = false
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
        // The Today card's skeletons shimmer forever: advance past the entrance, then idle.
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.waitForIdle()
        val out = File(checkNotNull(System.getProperty("user.dir"))).resolve("snapshots/$golden-light.png")
        composeRule.onRoot().captureRoboImage(out.canonicalFile.absolutePath)
    }

    private companion object {
        /** Past the entrance slide and the staggered list items. */
        const val SETTLE_MS = 2_000L
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class HomeRecentEmptyPhoneTest : HomeRecentEmptyScenes(HomeLayout.SinglePane, "home-recent-empty-phone360")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class HomeRecentEmptyTabletTest : HomeRecentEmptyScenes(HomeLayout.TwoPane, "home-recent-empty-tablet1280")
