package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The rows under the result card: on a two-column page the Post to LinkedIn row under the hero card and Watch replay
 * under the side card, on one column the post row above Watch replay; whatever the status, the rows stay put.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp")
class ResultBelowCardRowsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var hairline: Boolean? = null

    private fun show(width: Int, post: LinkedInPostRowState?, columnGap: Dp?) {
        composeRule.setContent {
            PuzzleBoxTheme {
                ResultBelowCardRows(
                    post = post,
                    time = "0:58",
                    onPost = {},
                    replay = { own ->
                        hairline = own
                        Box(Modifier.testTag("replay").fillMaxWidth().height(44.dp)) { Text("replay") }
                    },
                    modifier = Modifier.width(width.dp),
                    columnGap = columnGap,
                )
            }
        }
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    fun `two columns put a short status under the hero card and the replay row under the side card`() =
        assertUnderColumns(LinkedInPostRowState.Post)

    @Test
    fun `two columns give a long status the same place, so a status change does not move the rows`() =
        assertUnderColumns(LinkedInPostRowState.Check)

    private fun assertUnderColumns(state: LinkedInPostRowState) {
        show(width = WIDTH, post = state, columnGap = GAP.dp)
        assertEquals(false, hairline)
        val post = bounds("result.linkedin_post")
        val replay = bounds("replay")
        assertEquals("post starts at the hero column", 0f, post.left.value, 0.5f)
        assertEquals("post spans the hero column", HERO_WIDTH, post.width.value, 0.5f)
        assertEquals("replay starts at the side column", HERO_WIDTH + GAP, replay.left.value, 0.5f)
        assertEquals("replay spans the side column", WIDTH - HERO_WIDTH - GAP, replay.width.value, 0.5f)
        assertEquals("one line, centred on each other", (post.top + post.bottom).value / 2, (replay.top + replay.bottom).value / 2, 0.5f)
    }

    @Test
    fun `one column stacks the post row above the replay row, both full width`() {
        show(width = 363, post = LinkedInPostRowState.Check, columnGap = null)
        assertEquals(false, hairline)
        val post = bounds("result.linkedin_post")
        val replay = bounds("replay")
        assertTrue("post above replay", post.bottom <= replay.top)
        assertEquals("post full width", 363f, post.width.value, 0.5f)
        assertEquals("replay full width", 363f, replay.width.value, 0.5f)
    }

    @Test
    fun `two columns without a post row keep the replay row under the hero card, with its own hairline`() {
        show(width = WIDTH, post = null, columnGap = GAP.dp)
        composeRule.onNodeWithTag("result.linkedin_post").assertDoesNotExist()
        assertEquals(true, hairline)
        assertEquals("replay spans the hero column", HERO_WIDTH, bounds("replay").width.value, 0.5f)
    }

    @Test
    fun `one column without a post row leaves the replay row as it is, with its own hairline`() {
        show(width = 363, post = null, columnGap = null)
        composeRule.onNodeWithTag("result.linkedin_post").assertDoesNotExist()
        composeRule.onNodeWithTag("replay").assertIsDisplayed()
        assertEquals(true, hairline)
    }

    private companion object {
        const val WIDTH = 1040
        const val GAP = 24f

        /** The hero column's share of a 1.1 : 1 split, as [ResultHeroScreen] lays its cards out. */
        const val HERO_WIDTH = (WIDTH - GAP) * 1.1f / 2.1f
    }
}
