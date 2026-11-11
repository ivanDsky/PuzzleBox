package com.ivandsky.puzzlebox.home

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.test.getAlignmentLinePosition
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import com.ivandsky.puzzlebox.designsystem.LocalTabReselect
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.WeekDay
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins the approved Today stat tiles: the "Playing since" date keeps its smaller type but sits on the same
 * baseline as the Solved headline value, and all four tiles are one height. NATIVE graphics: the default
 * Robolectric text metrics give every style the same baseline and would hide a misaligned date.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w1200dp-h800dp")
class HomeStatTilesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val day = LocalDate.of(2026, 11, 4)
    private val tags = listOf("solved", "hint_free", "average", "since").map { "home.stat_$it" }

    private fun value(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).onChildren()[1]

    private fun baseline(tag: String): Float {
        val node = value(tag)
        return node.getUnclippedBoundsInRoot().top.value + node.getAlignmentLinePosition(FirstBaseline).value
    }

    @Test
    fun `the date value shares the Solved baseline and all tiles are one height`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalTabReselect provides MutableSharedFlow()) {
                PuzzleBoxTheme {
                    HomeScreen(
                        layout = HomeLayout.TwoPane,
                        state = HomeUiState(
                            today = emptyList(),
                            identity = null,
                            history = emptyList(),
                            puzzleDay = day,
                            playTarget = null,
                            stats = GameStats(3, 5, 12, 4, 48_000L, day.minusDays(20), best = null, rankOrder = RankOrder.TimeThenHints),
                            week = (6L downTo 0L).map { WeekDay(day.minusDays(it), emptyList(), isToday = it == 0L) },
                            rolloverAt = Instant.now().plus(3, ChronoUnit.HOURS),
                        ),
                        onOpenGame = {},
                        onOpenHistory = {},
                        onSelectDay = {},
                        modifier = Modifier,
                        title = "PuzzleBox",
                        summaries = HomeSummaries(onOpenRank = null, duels = null),
                    )
                }
            }
        }

        val heights = tags.map { composeRule.onNodeWithTag(it, useUnmergedTree = true).getUnclippedBoundsInRoot().height.value }
        heights.forEach { assertEquals("tile height", heights.first(), it, 0.5f) }
        assertEquals("date baseline vs Solved baseline", baseline("home.stat_solved"), baseline("home.stat_since"), 1f)
    }
}
