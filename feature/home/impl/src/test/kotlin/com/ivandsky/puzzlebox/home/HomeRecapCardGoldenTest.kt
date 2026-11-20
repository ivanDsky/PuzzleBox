package com.ivandsky.puzzlebox.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.LocalTabReselect
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PHONE = "w411dp-h891dp-420dpi"
private const val TABLET = "w1280dp-h800dp-land-mdpi"

/**
 * Review renders of Home with the recap tile — unopened, opened, the year and the loading skeleton, on the phone and the
 * tablet, light and dark — each next to the contract's `entry-points__<state>__<device>__<theme>.png`. Renders land in
 * `feature/home/impl/snapshots/` and are never committed (ADR-0033).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeRecapCardGoldenTest : HomeStreakScenes() {

    private fun today(day: LocalDate) = listOf(
        row("queens", "Queens", TodayStatus.Solved(42_000L)),
        row("tango", "Tango", TodayStatus.InProgress(67_000L)),
        row("zip", "Zip", TodayStatus.NotStarted),
        row("sudoku", "Mini Sudoku", TodayStatus.NotStarted),
        row("strands", "Strands", TodayStatus.Solved(118_000L)),
    ).let { rows -> HomeScreenTest.state(rows, emptyList(), GameId("tango")).copy(puzzleDay = day) }

    private fun row(id: String, name: String, status: TodayStatus) =
        HomeScreenTest.row(id, name, status).copy(brand = RecapPreviewBrands.getValue(GameId(id)), rankOrder = RankOrder.TimeThenHints)

    private fun capture(state: String, device: String, dark: Boolean, slot: RecapSlot, day: LocalDate) {
        render(dark) {
            CompositionLocalProvider(LocalTabReselect provides MutableSharedFlow()) {
                PuzzleBoxTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                    HomeScreen(
                        layout = if (device == "tablet") HomeLayout.TwoPane else HomeLayout.SinglePane,
                        state = today(day),
                        onOpenGame = {},
                        onSelectDay = {},
                        modifier = Modifier,
                        title = "Today",
                        summaries = HomeSummaries(onOpenRank = null, duels = null, onOpenDay = {}, onOpenStreak = {}, recap = slot),
                    )
                    }
                }
            }
        }
        // The entry motion ends by 2.2s (the sheen); render has run 2s.
        composeRule.mainClock.advanceTimeBy(1_500L)
        if (slot is RecapSlot.Loading) composeRule.onNodeWithContentDescription("Loading your recap").assertExists()
        else composeRule.onNodeWithTag("summary.recap").assertExists()
        val out = File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/entry-points__${state}__${device}__${if (dark) "dark" else "light"}.png")
        out.canonicalFile.parentFile?.mkdirs()
        composeRule.onRoot().captureRoboImage(out.canonicalFile.absolutePath)
    }

    private val november = LocalDate.of(2026, 11, 2)
    private val december = LocalDate.of(2026, 12, 20)
    private val unopened get() = previewRecap()
    private val opened get() = previewRecap(opened = true)
    private val year get() = previewRecap(label = "2026 so far", periodId = "2026", yearly = true)

    @Test @Config(sdk = [34], qualifiers = PHONE)
    fun `entry-points home unopened phone light`() = capture("home-unopened", "phone", false, unopened, november)

    @Test @Config(sdk = [34], qualifiers = PHONE)
    fun `entry-points home unopened phone dark`() = capture("home-unopened", "phone", true, unopened, november)

    @Test @Config(sdk = [34], qualifiers = TABLET)
    fun `entry-points home unopened tablet light`() = capture("home-unopened", "tablet", false, unopened, november)

    @Test @Config(sdk = [34], qualifiers = TABLET)
    fun `entry-points home unopened tablet dark`() = capture("home-unopened", "tablet", true, unopened, november)

    @Test @Config(sdk = [34], qualifiers = PHONE)
    fun `entry-points home opened phone light`() = capture("home-opened", "phone", false, opened, november)

    @Test @Config(sdk = [34], qualifiers = PHONE)
    fun `entry-points home opened phone dark`() = capture("home-opened", "phone", true, opened, november)

    @Test @Config(sdk = [34], qualifiers = TABLET)
    fun `entry-points home opened tablet light`() = capture("home-opened", "tablet", false, opened, november)

    @Test @Config(sdk = [34], qualifiers = TABLET)
    fun `entry-points home opened tablet dark`() = capture("home-opened", "tablet", true, opened, november)

    @Test @Config(sdk = [34], qualifiers = PHONE)
    fun `entry-points home year phone light`() = capture("home-year", "phone", false, year, december)

    @Test @Config(sdk = [34], qualifiers = PHONE)
    fun `entry-points home year phone dark`() = capture("home-year", "phone", true, year, december)

    @Test @Config(sdk = [34], qualifiers = TABLET)
    fun `entry-points home year tablet light`() = capture("home-year", "tablet", false, year, december)

    @Test @Config(sdk = [34], qualifiers = TABLET)
    fun `entry-points home year tablet dark`() = capture("home-year", "tablet", true, year, december)

    @Test @Config(sdk = [34], qualifiers = PHONE)
    fun `entry-points loading phone light`() = capture("loading", "phone", false, RecapSlot.Loading, november)

    @Test @Config(sdk = [34], qualifiers = PHONE)
    fun `entry-points loading phone dark`() = capture("loading", "phone", true, RecapSlot.Loading, november)

    @Test @Config(sdk = [34], qualifiers = TABLET)
    fun `entry-points loading tablet light`() = capture("loading", "tablet", false, RecapSlot.Loading, november)

    @Test @Config(sdk = [34], qualifiers = TABLET)
    fun `entry-points loading tablet dark`() = capture("loading", "tablet", true, RecapSlot.Loading, november)
}
