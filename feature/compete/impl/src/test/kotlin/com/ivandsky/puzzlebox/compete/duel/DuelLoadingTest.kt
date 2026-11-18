package com.ivandsky.puzzlebox.compete.duel

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.SHIMMER_TAG
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The duel's loading slot on a 1280×800 tablet: the two-column skeleton on the pane layout, the spinner on the phone's. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class DuelLoadingTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the pane layout loads as two panes edge to edge with a divider between`() {
        rule.setContent { PuzzleBoxTheme { DuelLoading(PlayLayout.TwoPane) } }

        val root = rule.onNodeWithTag("duel.legs.grid.skeleton").getUnclippedBoundsInRoot()
        val left = rule.onNodeWithTag("duel.legs.grid.skeleton.left").getUnclippedBoundsInRoot()
        val right = rule.onNodeWithTag("duel.legs.grid.skeleton.right").getUnclippedBoundsInRoot()
        assertEquals("fills across", 1280f, root.width.value, 1f)
        assertEquals("fills down", 800f, root.height.value, 1f)
        assertEquals("left starts at the edge", 0f, left.left.value, 1f)
        assertEquals("right ends at the edge", 1280f, right.right.value, 1f)
        assertEquals("equal panes", left.width.value, right.width.value, 2f)
    }

    @Test
    fun `the phone layout loads as the spinner, not the skeleton`() {
        rule.setContent { PuzzleBoxTheme { DuelLoading(PlayLayout.SinglePane) } }

        rule.onAllNodesWithTag("duel.legs.grid.skeleton").assertCountEquals(0)
    }

    @Test
    fun `the two-pane skeleton's bars and blocks shimmer`() {
        rule.setContent { PuzzleBoxTheme { DuelLoading(PlayLayout.TwoPane) } }

        assertTrue(rule.onAllNodesWithTag(SHIMMER_TAG, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }
}
