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
import com.ivandsky.puzzlebox.designsystem.TwoColumnRightWidth
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.Assert.assertEquals
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
    fun `the pane layout loads as the two columns, the right one 372 dp, both centred in the pane`() {
        rule.setContent { PuzzleBoxTheme { DuelLoading(PlayLayout.TwoPane) } }

        val root = rule.onNodeWithTag("duel.legs.grid.skeleton").getUnclippedBoundsInRoot()
        val left = rule.onNodeWithTag("duel.legs.grid.skeleton.left").getUnclippedBoundsInRoot()
        val right = rule.onNodeWithTag("duel.legs.grid.skeleton.right").getUnclippedBoundsInRoot()
        assertEquals(TwoColumnRightWidth.value, right.width.value, 1f)
        assertEquals("centred across", 1280f / 2, (root.left + root.right).value / 2f, 1f)
        assertEquals("left centred down", 800f / 2, (left.top + left.bottom).value / 2f, 1f)
        assertEquals("right centred down", 800f / 2, (right.top + right.bottom).value / 2f, 1f)
        assertEquals("the page is shorter than the pane", true, root.height.value < 800f)
    }

    @Test
    fun `the phone layout loads as the spinner, not the skeleton`() {
        rule.setContent { PuzzleBoxTheme { DuelLoading(PlayLayout.SinglePane) } }

        rule.onAllNodesWithTag("duel.legs.grid.skeleton").assertCountEquals(0)
    }
}
