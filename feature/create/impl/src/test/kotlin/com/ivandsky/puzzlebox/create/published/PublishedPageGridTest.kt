package com.ivandsky.puzzlebox.create.published

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.create.GOLDEN_TABLET
import com.ivandsky.puzzlebox.create.showGolden
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Ticket 132: the author's published page in the Community tablet's right pane (no Back) lays out two columns with
 * CommunityPuzzles on: the plain sharp board left; the title, the share ticket, the can't-edit note, Duplicate,
 * Unpublish and the game's disabled publish fields in a 372 dp column right. A pane too narrow for both columns
 * (320 dp board + 24 dp gap + 372 dp) falls back to the single column; the flag off is the page as shipped.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class PublishedPageGridTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `wide pane, flag on, the board is left of the ticket and the actions`() {
        show(PlainGame.STRANDS, ON, pane = 880.dp)

        assertEquals(1, count(GRID))
        val board = bounds(PLAIN_BOARD)
        assertTrue("board left of the ticket", board.right <= bounds("create.published.ticket").left)
        assertTrue("board left of Duplicate", board.right <= bounds("create.publishedPage.duplicate").left)
        assertTrue("board left of Unpublish", board.right <= bounds("create.publishedPage.unpublish").left)
    }

    @Test
    fun `wide pane, flag on, the right column runs title, ticket, Duplicate, Unpublish, publish fields`() {
        show(PlainGame.STRANDS, ON, pane = 880.dp)

        val tops = listOf(
            "create.publishedPage.title", "create.published.ticket", "create.publishedPage.duplicate",
            "create.publishedPage.unpublish", "fake.editor.publishFields.disabled",
        ).map { it to bounds(it).top }
        assertEquals(tops.sortedBy { it.second }, tops)
        assertEquals(1, count("create.publishedPage.title"))
    }

    @Test
    fun `wide pane, flag on, the right column is 372 dp wide, both columns start at the top and there is no bar`() {
        show(PlainGame.QUEENS, ON, pane = 880.dp)

        assertEquals(with(rule.density) { 372.dp.toPx() }, bounds("$GRID.right").width, 1f)
        assertEquals(bounds(PLAIN_BOARD).top, bounds("create.publishedPage.title").top, 1f)
        assertEquals(0, count("create.publishedPage.back"))
    }

    @Test
    fun `narrow pane, flag on, falls back to the single column`() {
        show(PlainGame.QUEENS, ON, pane = 600.dp)

        assertEquals(0, count(GRID))
        assertTrue("board above the ticket", bounds(PLAIN_BOARD).bottom <= bounds("create.published.ticket").top)
    }

    @Test
    fun `a pane just wide enough for both columns draws the grid`() {
        show(PlainGame.QUEENS, ON, pane = 748.dp)

        assertEquals(1, count(GRID))
    }

    @Test
    fun `a pane one dp short of both columns stays single`() {
        show(PlainGame.QUEENS, ON, pane = 747.dp)

        assertEquals(0, count(GRID))
    }

    @Test
    fun `wide pane, flag off, is the page as shipped`() {
        show(PlainGame.QUEENS, FakeReleaseFlags(), pane = 880.dp, awaitPlain = false)

        assertEquals(0, count(GRID))
        assertEquals(0, count("create.publishedPage.title"))
        assertEquals(0, count(PLAIN_BOARD))
    }

    private fun show(game: PlainGame, flags: ReleaseFlags, pane: Dp, awaitPlain: Boolean = true) {
        val world = PublishedPlainWorld(game, flags)
        world.start()
        rule.showGolden(dark = false, frozen = false) {
            Box(Modifier.wrapContentWidth(Alignment.Start).width(pane)) { PublishedPuzzleRoute(world.key, PlayLayout.TwoPane, onBack = null, onOpen = {}) }
        }
        awaitTag("create.published.ticket")
        if (awaitPlain) awaitTag(PLAIN_CONTENT)
        rule.waitForIdle()
    }

    private fun awaitTag(tag: String) = rule.waitUntil(TIMEOUT_MS) {
        ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
        count(tag) > 0
    }

    private fun count(tag: String) = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun bounds(tag: String): Rect {
        val node = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        return Rect(node.positionInRoot, node.size.toSize())
    }

    private companion object {
        const val GRID = "create.publishedPage.grid"
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
        val ON = FakeReleaseFlags(ReleaseFlag.CommunityPuzzles)
    }
}
