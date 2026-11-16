package com.ivandsky.puzzlebox.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.height
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.LocalTabReselect
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home before its local state lands (spec A2, §2.1): every number is blank, never a zero or
 * "0 days in a row · Best 0", and the real numbers arriving move nothing. Phone and tablet two-pane,
 * with a golden of the unloaded screen each. NATIVE graphics: real text metrics, so a slot of the
 * wrong height would show. Goldens live in `feature/home/impl/snapshots/`.
 */
abstract class HomeUnloadedScenes(private val layout: HomeLayout, private val golden: String) {

    @get:Rule
    val composeRule = createComposeRule()

    private val unloaded = HomeScreenTest.state(HomeScreenTest.TODAY.map { it.copy(status = null) }, emptyList(), GameId("zip"))
        .copy(
            stats = GameStats(0, 0, 0, 0, null, null, best = null, rankOrder = RankOrder.TimeThenHints),
            isLocalStateLoaded = false,
        )
    private val loaded = HomeScreenTest.state(HomeScreenTest.TODAY, emptyList(), GameId("tango"))

    private val shown = mutableStateOf(unloaded)

    private fun show(state: HomeUiState) {
        // The Today card's skeletons shimmer forever: a free-running clock would never idle.
        composeRule.mainClock.autoAdvance = false
        shown.value = state
        composeRule.setContent {
            CompositionLocalProvider(LocalTabReselect provides MutableSharedFlow()) {
                PuzzleBoxTheme {
                    val current by shown
                    HomeScreen(
                        layout = layout,
                        state = current,
                        onOpenGame = {},
                        onSelectDay = {},
                        modifier = Modifier,
                        title = "PuzzleBox",
                        summaries = HomeSummaries(onOpenRank = null, duels = null, onOpenDay = {}, onOpenStreak = {}),
                    )
                }
            }
        }
        settle()
    }

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.waitForIdle()
    }

    private fun assertNoNumbers() {
        // Unmerged: a zero hiding inside a merged node would still be on screen.
        listOf(
            hasText("days in a row", substring = true),
            hasText("day in a row", substring = true),
            hasText("Best", substring = true),
            hasText("0"),
            hasText("–"),
        ).forEach { composeRule.onAllNodes(it, useUnmergedTree = true).assertCountEquals(0) }
    }

    private fun slotHeights(): List<Float> = SLOT_TAGS.map { tag ->
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().height.value
    }

    @Test
    fun `unloaded Home shows no zero and no streak line`() {
        show(unloaded)
        assertNoNumbers()
    }

    @Test
    fun `the numbers arriving move nothing`() {
        show(unloaded)
        val before = slotHeights()
        val cardBefore = cardHeight()

        shown.value = loaded
        settle()

        assertEquals("the number slots changed height", before, slotHeights())
        assertEquals("the streak card changed height", cardBefore, cardHeight())
    }

    @Test
    fun `loaded Home then shows the real numbers`() {
        show(unloaded)
        shown.value = loaded
        settle()

        if (layout == HomeLayout.TwoPane) {
            composeRule.onNodeWithTag("home.pane_streak", useUnmergedTree = true).assertTextEquals("3")
            composeRule.onNodeWithTag("home.stat_solved", useUnmergedTree = true).assert(hasAnyDescendant(hasText("12")))
        } else {
            composeRule.onAllNodes(hasText("3 days in a row · Best 5"), useUnmergedTree = true).assertCountEquals(1)
        }
    }

    @Test
    fun `unloaded Home golden`() {
        show(unloaded)
        val out = File(checkNotNull(System.getProperty("user.dir"))).resolve("snapshots/$golden-light.png")
        composeRule.onRoot().captureRoboImage(out.canonicalFile.absolutePath)
    }

    private fun cardHeight(): Float {
        val tag = if (layout == HomeLayout.TwoPane) "home.streak_pane" else "home.streak"
        return composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().height.value
    }

    private val SLOT_TAGS = listOfNotNull(
        "home.streak_door",
        "home.stat_solved".takeIf { layout == HomeLayout.TwoPane },
        "home.stat_since".takeIf { layout == HomeLayout.TwoPane },
    )

    private companion object {
        /** Past the entrance slide and the staggered list items. */
        const val SETTLE_MS = 2_000L
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class HomeUnloadedPhoneTest : HomeUnloadedScenes(HomeLayout.SinglePane, "home-unloaded-phone360")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class HomeUnloadedTabletTest : HomeUnloadedScenes(HomeLayout.TwoPane, "home-unloaded-tablet1280")
