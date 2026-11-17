package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LightBoardTokens
import com.ivandsky.puzzlebox.designsystem.LocalFlatSurfaces
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strandsui.StrandsBoard
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayController
import com.ivandsky.puzzlebox.games.strandsui.StrandsUiFixtures
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Flat surfaces (nav-rethink M10-8): a Strands token is its face alone. With [LocalFlatSurfaces] on, the strip under a
 * found letter's face is the bare board; the layout, so the hit area, is the lip's, unchanged. The geometry the
 * connector clipper reads loses its lip too, so a faded line is never cut out of a lip that is not drawn.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h700dp")
class StrandsFlatTokenTest {

    @get:Rule
    val rule = createComposeRule()

    private val controller = StrandsPlayController(
        PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}),
    ).also { assertEquals(Adopt.Adopted, it.adoptToday(StrandsUiFixtures.envelope(), StrandsUiFixtures.ref)) }

    private fun show(flat: Boolean) = rule.setContent {
        PuzzleBoxTheme(darkTheme = false) {
            CompositionLocalProvider(LocalFlatSurfaces provides flat) {
                Box(Modifier.size(400.dp, 300.dp).testTag("host")) { StrandsBoard(controller) }
            }
        }
    }

    /** The pixels under row 0's first letter once row 0's theme word is found: the fill centre, the lip strip, the gap. */
    private fun foundLetterProbe(): Triple<Color, Color, Color> {
        StrandsUiFixtures.row(0).forEach { rule.onNodeWithTag("strands.cell.0.${it.col}").performClick() }
        rule.onNodeWithTag("strands.cell.0.3").performClick()
        rule.waitForIdle()
        val image = rule.onNodeWithTag("strands.cell.0.0").captureToImage().toPixelMap()
        val token = image.height * StrandsBoardSpec.TokenFraction
        val lip = token * StrandsBoardSpec.TokenLipFraction
        val faceBottom = image.height / 2f + token / 2
        val x = image.width / 2
        return Triple(image[x, (faceBottom - lip).toInt()], image[x, (faceBottom + lip / 2).toInt()], image[x, image.height - 1])
    }

    @Test
    fun `flat on, a found letter has no lip, the strip under its face is the bare board`() {
        show(flat = true)
        val (face, belowFace, bare) = foundLetterProbe()
        assertEquals(LightBoardTokens.strandsThemeFill, face)
        assertEquals(bare, belowFace)
        assertNotEquals(LightBoardTokens.strandsThemeLip, belowFace)
    }

    @Test
    fun `flat off, a found letter keeps its lip under the face`() {
        show(flat = false)
        val (_, belowFace, bare) = foundLetterProbe()
        assertEquals(LightBoardTokens.strandsThemeLip, belowFace)
        assertNotEquals(bare, belowFace)
    }

    @Test
    fun `flat, the token's lip is its face and no drop`() {
        val token = tokenGeometry(Offset(100f, 50f), half = 20f, scale = 0.5f, flat = true)
        assertEquals(0f, token.drop, 0f)
        assertEquals(token.face, token.lip)
        assertEquals(0f, tokenDrop(40f, flat = true), 0f)
    }

    @Test
    fun `flat, the clipper's token is the face alone, not face plus lip`() {
        val raised = Path().apply { addToken(Offset(50f, 50f), half = 20f) }.getBounds()
        val flat = Path().apply { addToken(Offset(50f, 50f), half = 20f, flat = true) }.getBounds()
        assertEquals(30f, flat.top, 0f)
        assertEquals(70f, flat.bottom, 0f)
        assertEquals(70f + tokenDrop(40f), raised.bottom, 0.01f)
    }
}
