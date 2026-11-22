package com.ivandsky.puzzlebox.insights.stats.grouped

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.stats.StatsBody
import com.ivandsky.puzzlebox.insights.stats.StatsFixtures
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A group's body lays out two-up from a 640dp pane (the pane, not the pane less its side padding) and stacks below it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class StatsGroupFrameTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun speedBlock(): StatsGroupBlock {
        val sections = (StatsFixtures.readyWithLines(game = StatsFixtures.zip).body as StatsBody.Ready).sections
        return groupStatsSections(sections, StatsFixtures.zip).first { it.group == StatsGroup.Speed }
    }

    private fun showIn(paneWidth: Int, block: StatsGroupBlock = speedBlock()) {
        rule.setContent {
            PuzzleBoxTheme {
                Box(Modifier.width(paneWidth.dp)) {
                    StatsGroupFrame(block, StatsFixtures::gameName, onOpenSolve = {}, singleGame = true)
                }
            }
        }
    }

    private fun assertLayout(paneWidth: Int, twoUp: Boolean) {
        showIn(paneWidth)
        val chart = rule.onNodeWithTag("stats.trend").getUnclippedBoundsInRoot()
        val records = rule.onNodeWithTag("stats.group.records.speed").getUnclippedBoundsInRoot()
        if (twoUp) {
            assertTrue("$paneWidth dp pane is two-up: records right of the chart", records.left >= chart.right)
            assertTrue("$paneWidth dp pane is two-up: same row", kotlin.math.abs(records.top.value - chart.top.value) < 1f)
        } else {
            assertTrue("$paneWidth dp pane stacks: records under the chart", records.top >= chart.bottom)
        }
    }

    @Test
    fun `a phone pane stacks the records under the chart`() = assertLayout(360, twoUp = false)

    @Test
    fun `a pane just under 640dp stacks`() = assertLayout(639, twoUp = false)

    @Test
    fun `a pane of exactly 640dp goes two-up`() = assertLayout(640, twoUp = true)

    @Test
    fun `a pane a little over 640dp goes two-up`() = assertLayout(660, twoUp = true)

    @Test
    fun `a chart with no side block keeps the width it has beside one`() {
        val sections = (StatsFixtures.readyWithLines(game = StatsFixtures.zip).body as StatsBody.Ready).sections
        val paired = speedBlock()
        val alone = StatsGroupBlock(StatsGroup.HintFree, sections.first { it is StatsSection.HintFreeRate }, side = null)
        rule.setContent {
            PuzzleBoxTheme {
                Column(Modifier.width(800.dp)) {
                    StatsGroupFrame(paired, StatsFixtures::gameName, onOpenSolve = {}, singleGame = true)
                    StatsGroupFrame(alone, StatsFixtures::gameName, onOpenSolve = {}, singleGame = true)
                }
            }
        }
        val beside = rule.onNodeWithTag("stats.trend").getUnclippedBoundsInRoot().let { it.right - it.left }
        val lone = rule.onNodeWithTag("stats.hintFree").getUnclippedBoundsInRoot().let { it.right - it.left }
        assertEquals(beside.value, lone.value, 1f)
    }
}
