package com.ivandsky.puzzlebox.designsystem.spotlight

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.designsystem.LightColorScheme
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The overlay over time: where marks land, when the sequence settles, reduced motion, clearing and replaying. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SpotlightOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val glyphs = RecordingGlyphs(paint = true)

    private var current by mutableStateOf<Spotlight?>(null)

    private fun spotlight(roles: Map<Int, SpotlightRole>, anchor: Set<Int>? = roles.keys, side: Int = SIDE, playId: Int = 0) =
        Spotlight(side, side, roles, anchor?.let { SpotlightCallout("Last cell: one open cell left.", it) }, glyphs, playId)

    /** Mounts [spotlight] over a square board on a paused clock; [reducedMotion] sets the system animator scale to 0. */
    private fun show(spotlight: Spotlight, reducedMotion: Boolean = false) {
        if (reducedMotion) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        }
        composeRule.mainClock.autoAdvance = false
        current = spotlight
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.size(BOARD_SIZE).testTag(BOARD)) { SpotlightOverlay(current) }
            }
        }
    }

    /** Hands the overlay [next] and runs exactly one frame. */
    private fun post(next: Spotlight?) {
        current = next
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeByFrame()
    }

    /**
     * The glyph calls drawn by capturing the board now. The board only redraws what changed since the last capture,
     * so an empty list means nothing moved.
     */
    private fun redraw(): List<RecordingGlyphs.Call> {
        glyphs.calls.clear()
        composeRule.onNodeWithTag(BOARD).captureToImage()
        return glyphs.calls.toList()
    }

    /** The glyph calls of every frame from now through [ms] more, one list per frame. */
    private fun framesFor(ms: Int): List<List<RecordingGlyphs.Call>> = List(ms / FRAME_MS + 1) {
        composeRule.mainClock.advanceTimeBy(FRAME_MS.toLong())
        redraw()
    }

    private fun cellSize(side: Int = SIDE): Size {
        val board = composeRule.onNodeWithTag(BOARD).fetchSemanticsNode().size
        return Size(board.width.toFloat() / side, board.height.toFloat() / side)
    }

    private fun calloutBounds(): DpRect = composeRule.onNodeWithTag(SpotlightCalloutTag).getBoundsInRoot()

    private fun calloutExists(): Boolean = composeRule.onAllNodesWithTag(SpotlightCalloutTag).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the glyph fills exactly its cell's rect`() {
        show(spotlight(mapOf(7 to SpotlightRole.TARGET), anchor = null), reducedMotion = true)
        composeRule.mainClock.advanceTimeByFrame()
        val image = composeRule.onNodeWithTag(BOARD).captureToImage()
        val pixels = image.toPixelMap()
        val cell = cellSize()
        // Cell 7 on a 4-wide board is column 3, row 1.
        val rect = Rect(3 * cell.width, cell.height, 4 * cell.width, 2 * cell.height)
        val inside = listOf(rect.left + 1, rect.right - 2).flatMap { x -> listOf(rect.top + 1, rect.bottom - 2).map { y -> x to y } }
        for ((x, y) in inside) assertEquals("inside at $x,$y", RecordingGlyphs.MARKER, pixels[x.toInt(), y.toInt()])
        val outside = listOf(rect.left - 2 to rect.center.y, rect.center.x to rect.top - 2, rect.center.x to rect.bottom + 1)
        for ((x, y) in outside) assertNotEquals("outside at $x,$y", RecordingGlyphs.MARKER, pixels[x.toInt(), y.toInt()])
    }

    @Test
    fun `a deduction pulses its target, then settles on its ghost and stops by TOTAL_MS`() {
        show(spotlight(mapOf(5 to SpotlightRole.EVIDENCE, 6 to SpotlightRole.TARGET)))
        val cell = cellSize()
        val frames = framesFor(SpotlightTokens.TOTAL_MS + 2 * FRAME_MS)
        assertTrue("no pulse", frames.flatten().any { it.role == SpotlightRole.TARGET && it.size.width > cell.width })
        val ghost = RecordingGlyphs.Call(6, SpotlightRole.TARGET, cell, SpotlightTokens.targetGhost(LightColorScheme))
        assertEquals(listOf(ghost), frames.last { it.isNotEmpty() })
        assertIdle()
    }

    @Test
    fun `a culprit pulses`() {
        show(spotlight(mapOf(6 to SpotlightRole.CULPRIT)))
        val cell = cellSize()
        assertTrue(framesFor(SpotlightTokens.TOTAL_MS).flatten().any { it.role == SpotlightRole.CULPRIT && it.size.width > cell.width })
    }

    @Test
    fun `an elimination never pulses and settles before TOTAL_MS`() {
        val roles = mapOf(1 to SpotlightRole.EVIDENCE, 4 to SpotlightRole.EXCLUDED, 8 to SpotlightRole.EXCLUDED)
        show(spotlight(roles))
        val cell = cellSize()
        val settleMs = SpotlightSequence(spotlight(roles)).endMs
        assertTrue(settleMs < SpotlightTokens.TOTAL_MS)
        val frames = framesFor(settleMs + 2 * FRAME_MS)
        assertTrue(frames.flatten().all { it.role == SpotlightRole.EXCLUDED && it.size.width <= cell.width })
        val settled = frames.last { it.isNotEmpty() }
        assertEquals(setOf(4, 8), settled.map { it.cell }.toSet())
        assertTrue(settled.all { it.size == cell })
        assertIdle()
    }

    @Test
    fun `excluded marks Scale in from below full size`() {
        show(spotlight(mapOf(1 to SpotlightRole.EVIDENCE, 4 to SpotlightRole.EXCLUDED)))
        val cell = cellSize()
        val sizes = framesFor(SpotlightTokens.TOTAL_MS).flatten().map { it.size.width }
        assertTrue(sizes.any { it < cell.width })
        assertEquals(cell.width, sizes.last(), 0.01f)
    }

    @Test
    fun `under reduced motion the first frame is the final frame and nothing pulses`() {
        show(spotlight(mapOf(5 to SpotlightRole.EVIDENCE, 6 to SpotlightRole.TARGET)), reducedMotion = true)
        composeRule.mainClock.advanceTimeByFrame()
        val cell = cellSize()
        val first = redraw()
        assertEquals(listOf(RecordingGlyphs.Call(6, SpotlightRole.TARGET, cell, SpotlightTokens.targetGhost(LightColorScheme))), first)
        assertTrue(calloutExists())
        assertIdle()
    }

    @Test
    fun `null mid-sequence clears the callout and every mark in the same frame`() {
        show(spotlight(mapOf(5 to SpotlightRole.EVIDENCE, 6 to SpotlightRole.TARGET)))
        composeRule.mainClock.advanceTimeBy(CLEAR_AT_MS)
        assertTrue(calloutExists())
        assertTrue(markerPixels() > 0)
        post(null)
        assertFalse(calloutExists())
        assertEquals(0, markerPixels())
    }

    /** Nothing redraws and the callout stays put however long the clock runs on. */
    private fun assertIdle() {
        redraw()
        val callout = calloutBounds()
        composeRule.mainClock.advanceTimeBy(SpotlightTokens.TOTAL_MS.toLong())
        assertEquals(emptyList<RecordingGlyphs.Call>(), redraw())
        assertEquals(callout, calloutBounds())
    }

    private fun markerPixels(): Int {
        val pixels = composeRule.onNodeWithTag(BOARD).captureToImage().toPixelMap()
        return (0 until pixels.width).sumOf { x -> (0 until pixels.height).count { y -> pixels[x, y] == RecordingGlyphs.MARKER } }
    }

    @Test
    fun `a new playId replays the sequence from the start and the same playId does not`() {
        val step = spotlight(mapOf(5 to SpotlightRole.EVIDENCE, 6 to SpotlightRole.TARGET))
        show(step)
        composeRule.mainClock.advanceTimeBy(SpotlightTokens.TOTAL_MS.toLong() * 2)
        val full = calloutBounds()

        post(step.copy(glyphs = RecordingGlyphs(paint = true)))
        assertEquals(full, calloutBounds())

        post(step.copy(playId = step.playId + 1))
        val restarted = calloutBounds()
        val expected = (full.right - full.left) * SpotlightTokens.CALLOUT_START_SCALE
        assertEquals(expected.value, (restarted.right - restarted.left).value, 1f)
        composeRule.mainClock.advanceTimeBy(SpotlightTokens.TOTAL_MS.toLong() * 2)
        assertEquals(full, calloutBounds())
    }

    @Test
    fun `the callout never covers a lit cell`() {
        val side = 6
        val cases = listOf(
            mapOf(13 to SpotlightRole.EVIDENCE, 14 to SpotlightRole.TARGET, 15 to SpotlightRole.EVIDENCE),
            mapOf(1 to SpotlightRole.EVIDENCE, 33 to SpotlightRole.TARGET, 20 to SpotlightRole.EXCLUDED),
            mapOf(30 to SpotlightRole.CULPRIT),
        )
        show(spotlight(cases[0], side = side), reducedMotion = true)
        for ((index, roles) in cases.withIndex()) {
            post(spotlight(roles, side = side, playId = index))
            val callout = calloutBounds()
            val cellDp = BOARD_SIZE / side
            for (cell in roles.keys) {
                val left = cellDp * (cell % side)
                val top = cellDp * (cell / side)
                val overlaps = callout.left < left + cellDp && left < callout.right && callout.top < top + cellDp && top < callout.bottom
                assertFalse("step $index: callout $callout covers lit cell $cell", overlaps)
            }
        }
    }

    private companion object {
        const val BOARD = "board"
        const val SIDE = 4
        const val FRAME_MS = 16
        const val CLEAR_AT_MS = 200L
        val BOARD_SIZE = 300.dp
    }
}
