package com.ivandsky.puzzlebox.create.published

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.create.GOLDEN_TABLET
import com.ivandsky.puzzlebox.create.showGolden
import com.ivandsky.puzzlebox.designsystem.HiddenBoardTokens
import com.ivandsky.puzzlebox.designsystem.SHIMMER_TAG
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.PlayLayout
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
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
 * Ticket 132: the author's published page in the Community tablet's right pane (no Back) lays out two columns:
 * the plain sharp board left; the title, the share ticket, the can't-edit note, Duplicate,
 * Unpublish and the game's disabled publish fields in a 372 dp column right. A pane too narrow for both columns
 * (320 dp board + 24 dp gap + 372 dp) falls back to the single column.
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
    fun `wide pane, the board is left of the ticket and the actions`() {
        show(PlainGame.STRANDS, pane = 880.dp)

        assertEquals(1, count(GRID))
        val board = bounds(PLAIN_BOARD)
        assertTrue("board left of the ticket", board.right <= bounds("create.published.ticket").left)
        assertTrue("board left of Duplicate", board.right <= bounds("create.publishedPage.duplicate").left)
        assertTrue("board left of Unpublish", board.right <= bounds("create.publishedPage.unpublish").left)
    }

    @Test
    fun `wide pane, the right column runs title, ticket, Duplicate, Unpublish, publish fields`() {
        show(PlainGame.STRANDS, pane = 880.dp)

        val tops = listOf(
            "create.publishedPage.title", "create.published.ticket", "create.publishedPage.duplicate",
            "create.publishedPage.unpublish", "fake.editor.publishFields.disabled",
        ).map { it to bounds(it).top }
        assertEquals(tops.sortedBy { it.second }, tops)
        assertEquals(1, count("create.publishedPage.title"))
    }

    @Test
    fun `wide pane, the right column is 372 dp wide and there is no bar`() {
        show(PlainGame.QUEENS, pane = 880.dp)

        assertEquals(with(rule.density) { 372.dp.toPx() }, bounds("$GRID.right").width, 1f)
        assertEquals(0, count("create.publishedPage.back"))
    }

    @Test
    fun `a tablet pane, the board is wider than today's cap and fits the pane`() {
        show(PlainGame.QUEENS, pane = 1200.dp)

        val board = bounds(PLAIN_BOARD)
        val pane = bounds("route.publishedPuzzle")
        assertTrue("board ${board.width} wider than the cap", board.width > with(rule.density) { HiddenBoardTokens.MaxSide.toPx() })
        assertEquals("the board keeps its square", board.width, board.height, 1f)
        assertTrue("board in the pane: $board of $pane", board.top >= pane.top && board.bottom <= pane.bottom)
    }

    @Test
    fun `a tablet pane, the board fills the left column's width when the height allows`() {
        show(PlainGame.QUEENS, pane = 880.dp)

        val expected = with(rule.density) { (880.dp - Spacing.lg * 2 - Spacing.xl - 372.dp).toPx() }
        assertEquals(expected, bounds(PLAIN_BOARD).width, 1f)
    }

    @Test
    fun `a pane shorter than the board's bound keeps today's size`() {
        show(PlainGame.QUEENS, pane = 1200.dp, height = 420.dp)

        assertEquals(with(rule.density) { HiddenBoardTokens.MaxSide.toPx() }, bounds(PLAIN_BOARD).width, 1f)
    }

    @Test
    fun `a pane 880 dp wide, the columns sit vertically centred in the pane`() = assertCentred(880.dp)

    @Test
    fun `a pane 1200 dp wide, the columns sit vertically centred in the pane`() = assertCentred(1200.dp)

    @Test
    fun `a tablet pane, loading draws the two-column skeleton and not a blank surface`() {
        val world = PublishedPlainWorld(PlainGame.QUEENS).apply { gate = CompletableDeferred() }
        showWorld(world, pane = 1200.dp, await = false)
        // The golden harness freezes motion; the page's own skeleton runs on the system default.
        Settings.Global.putFloat(ApplicationProvider.getApplicationContext<Context>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        rule.waitForIdle()

        assertEquals(1, count("$GRID.skeleton"))
        assertTrue("the skeleton bars sweep a sheen", count(SHIMMER_TAG) > 0)
        assertEquals(1, count("$GRID.skeleton.right"))
        assertEquals(1, count(PLAIN_LOADING))
        assertEquals(0, count("create.published.ticket"))
        assertTrue("board left of the right column", bounds(PLAIN_LOADING).right <= bounds("$GRID.skeleton.right").left)
    }

    @Test
    fun `a pane 1200 dp wide, the skeleton's board is exactly where the loaded board lands`() = assertSkeletonInPlace(1200.dp)

    @Test
    fun `a pane 1000 dp wide, the skeleton's board is exactly where the loaded board lands`() = assertSkeletonInPlace(1000.dp)

    @Test
    fun `a narrow pane keeps the blank surface while loading`() {
        val world = PublishedPlainWorld(PlainGame.QUEENS).apply { gate = CompletableDeferred() }
        showWorld(world, pane = 600.dp, await = false)

        assertEquals(0, count("$GRID.skeleton"))
        assertEquals(0, count(PLAIN_LOADING))
    }

    @Test
    fun `narrow pane falls back to the single column`() {
        show(PlainGame.QUEENS, pane = 600.dp)

        assertEquals(0, count(GRID))
        assertTrue("board above the ticket", bounds(PLAIN_BOARD).bottom <= bounds("create.published.ticket").top)
    }

    @Test
    fun `a pane just wide enough for both columns draws the grid`() {
        show(PlainGame.QUEENS, pane = 748.dp)

        assertEquals(1, count(GRID))
    }

    @Test
    fun `a pane one dp short of both columns stays single`() {
        show(PlainGame.QUEENS, pane = 747.dp)

        assertEquals(0, count(GRID))
    }

    @Test
    fun `a pane 880 dp wide, the skeleton's right column blocks sit where the loaded pieces land`() = assertRightColumnInPlace(880.dp)

    @Test
    fun `a pane 1200 dp wide, the skeleton's right column blocks sit where the loaded pieces land`() = assertRightColumnInPlace(1200.dp)

    /**
     * Each block of the skeleton's right column starts and ends where the loaded page's piece does, within 8 dp, and the
     * columns span the same top and bottom, so nothing moves vertically when the page arrives.
     */
    private fun assertRightColumnInPlace(pane: Dp) {
        val world = PublishedPlainWorld(PlainGame.QUEENS, gameSlots = false).apply { gate = CompletableDeferred() }
        showWorld(world, pane, await = false)
        val skeleton = RIGHT_PIECES.mapValues { (piece, _) -> bounds("$GRID.skeleton.$piece") }
        world.gate?.complete(Unit)
        awaitTag(PLAIN_CONTENT)
        rule.waitForIdle()

        val tolerance = with(rule.density) { 8.dp.toPx() }
        val off = RIGHT_PIECES.flatMap { (piece, loadedTag) ->
            val drawn = skeleton.getValue(piece)
            val landed = bounds(loadedTag)
            listOf("$piece top" to drawn.top - landed.top, "$piece bottom" to drawn.bottom - landed.bottom)
        }.filter { (_, delta) -> kotlin.math.abs(delta) > tolerance }
        assertTrue("skeleton off the loaded page (dp): ${off.map { (what, delta) -> "$what ${delta / rule.density.density}" }}", off.isEmpty())
    }

    private fun assertSkeletonInPlace(pane: Dp) {
        val world = PublishedPlainWorld(PlainGame.QUEENS).apply { gate = CompletableDeferred() }
        showWorld(world, pane, await = false)
        val loading = bounds(PLAIN_LOADING)
        world.gate?.complete(Unit)
        awaitTag(PLAIN_CONTENT)
        rule.waitForIdle()

        assertEquals(loading, bounds(PLAIN_BOARD))
    }

    /** The topmost and lowest parts of the page leave equal gaps above and below, within 1 dp. */
    private fun assertCentred(pane: Dp) {
        show(PlainGame.QUEENS, pane = pane)

        val route = bounds("route.publishedPuzzle")
        val top = minOf(bounds(PLAIN_BOARD).top, bounds("create.publishedPage.title").top)
        val bottom = maxOf(bounds(PLAIN_BOARD).bottom, bounds("fake.editor.publishFields.disabled").bottom)
        assertEquals("gaps above and below", top - route.top, route.bottom - bottom, with(rule.density) { 1.dp.toPx() })
    }

    private fun show(game: PlainGame, pane: Dp, height: Dp = Dp.Unspecified) =
        showWorld(PublishedPlainWorld(game), pane, height = height)

    private fun showWorld(world: PublishedPlainWorld, pane: Dp, await: Boolean = true, height: Dp = Dp.Unspecified) {
        world.start()
        rule.showGolden(dark = false, frozen = false) {
            Box(Modifier.wrapContentWidth(Alignment.Start).width(pane)
                .then(if (height.isSpecified) Modifier.wrapContentHeight(Alignment.Top).height(height) else Modifier)) {
                PublishedPuzzleRoute(world.key, PlayLayout.TwoPane, onBack = null, onOpen = {})
            }
        }
        if (await) {
            awaitTag("create.published.ticket")
            awaitTag(PLAIN_CONTENT)
        }
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
        /** Skeleton piece to the loaded page's tag: the column and its blocks. */
        val RIGHT_PIECES = linkedMapOf(
            "right" to "$GRID.right",
            "ticket" to "create.published.ticket",
            "duplicate" to "create.publishedPage.duplicate",
            "unpublish" to "create.publishedPage.unpublish",
        )
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
    }
}
