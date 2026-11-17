package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.LocalTabReselect
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.home.HomeScreenTest.Companion.TODAY
import com.ivandsky.puzzlebox.home.HomeScreenTest.Companion.row
import com.ivandsky.puzzlebox.model.GameId
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home's Recent card and Today badges (A13, A17). The tablet keeps a full-screen capture; the phone
 * captures are section frames at the 360 dp floor, because the Recent card sits below the fold of a
 * 640 dp phone root. NATIVE graphics. Goldens live in `feature/home/impl/snapshots/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class HomeRecentEmptyTabletTest {

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
                        layout = HomeLayout.TwoPane,
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
        val out = File(checkNotNull(System.getProperty("user.dir"))).resolve("snapshots/home-recent-empty-tablet1280-light.png")
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
class HomeRecentPhoneFramesTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `empty recent section golden`() = capture("home-recent-empty-section-phone360", Spacing.lg) {
        HomeRecentSection(
            days = emptyList(),
            loaded = true,
            selectedDay = null,
            onDayClick = {},
            selectable = false,
            onOpenDay = {},
        )
    }

    @Test
    fun `unloaded recent section golden`() = capture("home-recent-unloaded-section-phone360", Spacing.lg) {
        HomeRecentSection(
            days = emptyList(),
            loaded = false,
            selectedDay = null,
            onDayClick = {},
            selectable = false,
            onOpenDay = {},
        )
    }

    @Test
    fun `today badges golden`() = capture("home-today-badges-phone360", 0.dp) {
        HomeTodaySummary(
            state = HomeScreenTest.state(FIVE_TODAY_GAMES, emptyList(), GameId("zip")),
            onOpenGame = {},
            tagPrefix = "home.today",
            badgeTagPrefix = "home.badge.",
        )
    }

    @Test
    fun `today badges all solved golden`() = capture("home-today-all-solved-phone360", 0.dp) {
        val solved = FIVE_TODAY_GAMES.map { it.copy(status = TodayStatus.Solved(36_000L)) }
        HomeTodaySummary(
            state = HomeScreenTest.state(solved, emptyList(), GameId("zip")).copy(rolloverAt = Instant.EPOCH),
            onOpenGame = {},
            tagPrefix = "home.today",
            badgeTagPrefix = "home.badge.",
        )
    }

    private fun capture(golden: String, outer: Dp, content: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(
                    Modifier
                        .testTag(FRAME)
                        .width(PHONE_WIDTH)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(outer),
                ) { content() }
            }
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.waitForIdle()
        val out = File(checkNotNull(System.getProperty("user.dir"))).resolve("snapshots/$golden-light.png")
        composeRule.onNodeWithTag(FRAME).captureRoboImage(out.canonicalFile.absolutePath)
    }

    private companion object {
        const val FRAME = "golden.frame"
        val PHONE_WIDTH = 360.dp

        /** Past the entrance slide and the staggered list items. */
        const val SETTLE_MS = 2_000L
    }
}
