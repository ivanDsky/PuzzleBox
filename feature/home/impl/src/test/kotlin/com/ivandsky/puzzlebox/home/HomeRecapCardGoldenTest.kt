package com.ivandsky.puzzlebox.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.LocalTabReselect
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Review render of Home with the recap card (phone, light and dark): the full Today list with the card above the
 * rank and duels rows. Renders land in `feature/home/impl/snapshots/` and are never committed (ADR-0033).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h900dp-420dpi")
class HomeRecapCardGoldenTest : HomeStreakScenes() {

    private fun capture(dark: Boolean) {
        render(dark) {
            CompositionLocalProvider(LocalTabReselect provides MutableSharedFlow()) {
                PuzzleBoxTheme {
                    HomeScreen(
                        layout = HomeLayout.SinglePane,
                        state = HomeScreenTest.state(HomeScreenTest.TODAY, emptyList(), GameId("tango"))
                            .copy(rank = TodayRank(GameId("zip"), "Zip", 3)),
                        onOpenGame = {},
                        onSelectDay = {},
                        modifier = Modifier,
                        title = "PuzzleBox",
                        summaries = HomeSummaries(
                            onOpenRank = {},
                            duels = HomeDuelsSummary(onOpen = {}, needYou = { 2 }),
                            onOpenDay = {},
                            onOpenStreak = {},
                            recap = RecapSlot("October 2026", onOpen = {}, onDismiss = {}),
                        ),
                    )
                }
            }
        }
        composeRule.onNodeWithTag("summary.recap").assertExists()
        val out = File(checkNotNull(System.getProperty("user.dir"))).resolve("snapshots/home-recap-card-phone360-${if (dark) "dark" else "light"}.png")
        out.canonicalFile.parentFile?.mkdirs()
        composeRule.onRoot().captureRoboImage(out.canonicalFile.absolutePath)
    }

    @Test
    fun `home with the recap card on the phone light`() = capture(dark = false)

    @Test
    fun `home with the recap card on the phone dark`() = capture(dark = true)
}
