package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.GameBrand
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The hub's world as approved on build .5 (F8): each game's own wash and its faint glyph, and between two games the
 * blend of both as far as the scrub is. The world carries no tag or semantics of its own (the glyph is decoration), so
 * these read what it draws, in the light theme.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RanksWorldTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val brands = listOf(GameBrand(AccentSlot.Blue, GameGlyphs.Zip), GameBrand(AccentSlot.Orange, GameGlyphs.Tango))
    private lateinit var scrub: BoardScrub

    private fun show(position: Int) = composeRule.setContent {
        PuzzleBoxTheme(darkTheme = false) {
            val scope = rememberCoroutineScope()
            scrub = remember { BoardScrub(position, scope) }
            RanksWorld(brands, scrub, wide = false, Modifier.fillMaxSize().testTag("world")) {}
        }
    }

    // Every pixel of the world as 0xRRGGBB, row by row from the top.
    private fun drawn(): List<Int> {
        val pixels = composeRule.onNodeWithTag("world").captureToImage().toPixelMap()
        return List(pixels.width * pixels.height) { pixels[it % pixels.width, it / pixels.width].toArgb() and 0xFFFFFF }
    }

    // The wash is the top corner, which the glyph at the bottom end never reaches.
    private fun List<Int>.wash() = "#%06X".format(first())

    // The glyphs' inks are the flat colours that cover the most of what is not wash (their edges are a few pixels of
    // every shade between), the least red first.
    private fun List<Int>.inks(count: Int) =
        filter { it != first() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(count).map { it.key }.sorted()

    // The renderer rounds a glyph's opacity to a whole 8-bit alpha, which moves a channel by one step at most.
    private fun assertInks(expected: List<Int>, drawn: List<Int>) {
        val near = expected.size == drawn.size &&
            expected.zip(drawn).all { (want, drew) -> listOf(16, 8, 0).all { abs((want shr it and 0xFF) - (drew shr it and 0xFF)) <= 1 } }
        assertTrue("expected ${expected.map { "#%06X".format(it) }}, drew ${drawn.map { "#%06X".format(it) }}", near)
    }

    @Test
    fun `a game's board is washed in its own tint with its glyph faint over it`() {
        show(position = 0)

        val world = drawn()

        // 60% of the way from the background F2F4F7 to Blue's container CAEAFF; the glyph is Blue's accent 00B4FE at 22% over it.
        assertEquals("#DAEEFC", world.wash())
        assertInks(listOf(0xAAE1FC), world.inks(1))
    }

    @Test
    fun `another game's board has another wash and another glyph ink`() {
        show(position = 1)

        val world = drawn()

        // 60% of the way from the background to Orange's container FFDDC9; the glyph is Orange's accent FF7F02 at 22% over it.
        assertEquals("#FAE6DC", world.wash())
        assertInks(listOf(0xFBCFAC), world.inks(1))
    }

    @Test
    fun `halfway between two games the wash is halfway between theirs and both glyphs show at half strength`() {
        show(position = 0)

        composeRule.runOnIdle { scrub.dragBy(-0.5f, brands.size) }
        val world = drawn()

        // Halfway from DAEEFC to FAE6DC; each accent at 11% over it.
        assertEquals("#EBEAEC", world.wash())
        assertInks(listOf(0xD1E4EE, 0xEDDED2), world.inks(2))
    }
}
