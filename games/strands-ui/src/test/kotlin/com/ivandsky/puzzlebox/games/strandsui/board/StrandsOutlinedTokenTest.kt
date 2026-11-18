package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LightBoardTokens
import com.ivandsky.puzzlebox.designsystem.LocalFlatStrands
import com.ivandsky.puzzlebox.designsystem.LocalFlatSurfaces
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strandsui.StrandsBoard
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayController
import com.ivandsky.puzzlebox.games.strandsui.StrandsUiFixtures
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The outlined Strands look (nav-rethink 131, prototype 07 C): with [LocalFlatStrands] on, an idle letter is a thin
 * outlined square at 78% of its cell with no fill; a found word is tint, joined by a tint connector into one shape; the
 * spangram is gold with its inset ring and a wider connector; the live selection is a thick outline; the hint ring is
 * dashed. Flat off, none of it draws. Probed at 3x so every stroke is whole pixels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h400dp-xxhdpi")
class StrandsOutlinedTokenTest {

    @get:Rule
    val rule = createComposeRule()

    private val light = LightBoardTokens

    private val controller = StrandsPlayController(
        PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}),
    ).also { assertEquals(Adopt.Adopted, it.adoptToday(StrandsUiFixtures.envelope(), StrandsUiFixtures.ref)) }

    private fun show(flat: Boolean) = rule.setContent {
        PuzzleBoxTheme(darkTheme = false) {
            CompositionLocalProvider(LocalFlatStrands provides flat, LocalFlatSurfaces provides flat) {
                Box(Modifier.size(300.dp, 300.dp).testTag("host")) { StrandsBoard(controller) }
            }
        }
    }

    private fun play(path: List<Coord>) {
        path.forEach(controller::tap)
        controller.tap(path.last())
        rule.waitForIdle()
    }

    private fun cell(row: Int, col: Int): ImageBitmap = rule.onNodeWithTag("strands.cell.$row.$col").captureToImage()

    private fun board(): ImageBitmap = rule.onNodeWithTag("strands.board").captureToImage()

    private fun Color.near(other: Color) =
        abs(red - other.red) < 0.02f && abs(green - other.green) < 0.02f && abs(blue - other.blue) < 0.02f && abs(alpha - other.alpha) < 0.02f

    private fun assertColor(expected: Color, actual: Color, what: String) = assertTrue("$what: $actual is not $expected", actual.near(expected))

    /** The pixel [dy] px below the cell's centre line in the middle column, and the cell's bare corner. */
    private class Probe(image: ImageBitmap) {
        val map = image.toPixelMap()
        val pitch = image.height
        val cx = image.width / 2
        val cy = image.height / 2
        val bare: Color = map[cx, 0]
        val edgeTop = cy - (pitch * 0.78f / 2).toInt()
        fun at(dy: Int): Color = map[cx, (cy + dy).coerceIn(0, pitch - 1)]
    }

    @Test
    fun `outlined on, an idle letter is a thin outlined square at 78 percent and has no fill`() {
        show(flat = true)
        val p = Probe(cell(0, 0))
        assertColor(light.strandsFlatOutline.copy(alpha = StrandsOutlinedSpec.IdleOutlineAlpha).compositeOver(p.bare), p.map[p.cx, p.edgeTop + 2], "the softened outline on the token's top edge")
        assertColor(p.bare, p.map[p.cx, p.cy - (p.pitch * 0.33f).toInt()], "inside the token, above the letter")
        assertColor(p.bare, p.map[p.cx, p.edgeTop - 3], "just outside the 78 percent token")
    }

    @Test
    fun `outlined off, an idle letter draws no outline`() {
        show(flat = false)
        val p = Probe(cell(0, 0))
        assertColor(p.bare, p.map[p.cx, p.edgeTop + 2], "the lipped idle letter has no token")
    }

    @Test
    fun `outlined on, the cell and so the hit area is the size it was`() {
        show(flat = true)
        val on = rule.onNodeWithTag("strands.cell.0.0").getUnclippedBoundsInRoot()
        rule.waitForIdle()
        assertEquals(75.dp, on.right - on.left)
        assertEquals(75.dp, on.bottom - on.top)
    }

    @Test
    fun `outlined on, a found word is tint with no outline and an alternate word takes the alt tint`() {
        show(flat = true)
        play(StrandsUiFixtures.row(0))
        play(StrandsUiFixtures.row(2))
        val first = Probe(cell(0, 0))
        assertColor(light.strandsThemeTint, first.map[first.cx, first.edgeTop + 2], "the found token's top edge is its fill")
        assertColor(light.strandsThemeTint, first.map[first.cx, first.cy - (first.pitch * 0.33f).toInt()], "the found token's face")
        assertColor(first.bare, first.map[first.cx, first.edgeTop - 3], "outside the 78 percent token")
        val alt = Probe(cell(2, 0))
        assertColor(light.strandsThemeAltTint, alt.map[alt.cx, alt.cy - (alt.pitch * 0.33f).toInt()], "the alternate word's face")
    }

    @Test
    fun `outlined on, a found word's squares join through a 29 unit tint connector`() {
        show(flat = true)
        play(StrandsUiFixtures.row(0))
        val image = board()
        val map = image.toPixelMap()
        val pitch = image.width / 4
        val seam = pitch
        val mid = pitch / 2
        assertColor(light.strandsThemeTint, map[seam, mid], "the join, on the centre line")
        assertColor(light.strandsThemeTint, map[seam, mid - (pitch * 0.12f).toInt()], "the join, inside half of 29 units")
        assertColor(map[seam, mid + (pitch * 0.30f).toInt()], map[seam, mid - (pitch * 0.30f).toInt()], "beyond the connector both sides are bare")
        assertTrue(!map[seam, mid - (pitch * 0.25f).toInt()].near(light.strandsThemeTint))
    }

    @Test
    fun `outlined on, the spangram is gold with a black inset ring and a 41 unit connector`() {
        show(flat = true)
        play(StrandsUiFixtures.row(1))
        val p = Probe(cell(1, 0))
        assertColor(LightBoardTokens.strandsSpangramFill, p.at(-(p.pitch * 0.37f).toInt()), "the gold between ring and edge")
        assertColor(LightBoardTokens.strandsTileText, p.at(-73), "the 2dp inset ring")
        val image = board()
        val map = image.toPixelMap()
        val pitch = image.width / 4
        val row = pitch + pitch / 2
        assertColor(LightBoardTokens.strandsSpangramConnector, map[pitch, row - (pitch * 0.18f).toInt()], "the wide connector, inside half of 41 units")
        assertTrue(!map[pitch, row - (pitch * 0.30f).toInt()].near(LightBoardTokens.strandsSpangramConnector))
    }

    @Test
    fun `outlined on, a selected letter has a thick selection outline over a tint face`() {
        show(flat = true)
        controller.tap(Coord(0, 0))
        rule.waitForIdle()
        val p = Probe(cell(0, 0))
        assertColor(light.strandsFlatSelection, p.map[p.cx, p.edgeTop + 3], "the 3.5dp selection outline")
        assertColor(light.strandsThemeTint, p.map[p.cx, p.cy - (p.pitch * 0.33f).toInt()], "the selected face")
    }

    @Test
    fun `outlined on, a hinted letter has a dashed ring and no solid outline`() {
        show(flat = true)
        play(StrandsUiFixtures.row(0))
        listOf("TAKE", "RAKE", "CART").forEach { play(StrandsUiFixtures.nonTheme.getValue(it)) }
        controller.pressHint()
        rule.waitForIdle()
        val p = Probe(cell(3, 0))
        assertColor(p.bare, p.map[p.cx, p.edgeTop + 2], "a hinted letter's own edge is bare, the ring is outside it")
        val ring = (0 until 120).map { p.map[p.cx - 60 + it, p.cy - 93] }
        assertTrue("the ring has dashes", ring.any { it.near(light.strandsFlatHintRing) })
        assertTrue("and gaps between them", ring.any { it.near(p.bare) })
    }
}
