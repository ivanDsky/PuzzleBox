package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The rows under the result card: the Post to LinkedIn row beside Watch replay when the column is wide, above it when narrow. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h600dp")
class ResultBelowCardRowsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var hairline: Boolean? = null

    private fun show(width: Int, post: LinkedInPostRowState? = LinkedInPostRowState.Post) {
        composeRule.setContent {
            PuzzleBoxTheme {
                ResultBelowCardRows(
                    post = post,
                    time = "0:58",
                    onPost = {},
                    replay = { own ->
                        hairline = own
                        Text("replay", Modifier.testTag("replay"))
                    },
                    modifier = Modifier.width(width.dp),
                )
            }
        }
    }

    @Test
    fun `a wide column puts the post row beside the replay row`() {
        show(width = 530)
        assertEquals(false, hairline)
        val post = composeRule.onNodeWithTag("result.linkedin_post").getUnclippedBoundsInRoot()
        val replay = composeRule.onNodeWithTag("replay").getUnclippedBoundsInRoot()
        assertTrue(post.right <= replay.left)
        assertTrue(abs(post.top.value - replay.top.value) < 24f)
    }

    @Test
    fun `a narrow column stacks the post row above the replay row`() {
        show(width = 363)
        val post = composeRule.onNodeWithTag("result.linkedin_post").getUnclippedBoundsInRoot()
        val replay = composeRule.onNodeWithTag("replay").getUnclippedBoundsInRoot()
        assertTrue(post.bottom <= replay.top)
    }

    @Test
    fun `no post row leaves the replay row as it is, with its own hairline`() {
        show(width = 363, post = null)
        composeRule.onNodeWithTag("result.linkedin_post").assertDoesNotExist()
        composeRule.onNodeWithTag("replay").assertIsDisplayed()
        assertEquals(true, hairline)
    }
}
