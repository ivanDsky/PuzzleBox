package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Flat Strands (nav-rethink M10-8, ticket 19c): a Strands token is its face alone. The strip under a
 * found letter's face is the bare board; the layout, so the hit area, is unchanged. The geometry the
 * connector clipper reads is the face too, so a faded line is never cut out of a shape that is not drawn.
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

    private fun show() = rule.setContent {
        PuzzleBoxTheme(darkTheme = false) {
            Box(Modifier.size(400.dp, 300.dp).testTag("host")) { StrandsBoard(controller) }
        }
    }

    /** The pixels under row 0's first letter once row 0's theme word is found: the fill centre, the strip under the face, the gap. */
    private fun foundLetterProbe(): Triple<Color, Color, Color> {
        StrandsUiFixtures.row(0).forEach { rule.onNodeWithTag("strands.cell.0.${it.col}").performClick() }
        rule.onNodeWithTag("strands.cell.0.3").performClick()
        rule.waitForIdle()
        val image = rule.onNodeWithTag("strands.cell.0.0").captureToImage().toPixelMap()
        val token = image.height * StrandsOutlinedSpec.TokenFraction
        val strip = token * StrandsBoardSpec.RingRoomFraction
        val faceBottom = image.height / 2f + token / 2
        val x = image.width / 2
        return Triple(image[x, (faceBottom - strip).toInt()], image[x, (faceBottom + strip / 2).toInt()], image[x, image.height - 1])
    }

    @Test
    fun `with no flag set a found letter has no lip, the strip under its face is the bare board`() {
        show()
        val (face, belowFace, bare) = foundLetterProbe()
        assertEquals(LightBoardTokens.strandsThemeTint, face)
        assertEquals(bare, belowFace)
    }

    @Test
    fun `the token's geometry is its face alone, centred and square`() {
        val token = tokenGeometry(Offset(100f, 50f), half = 20f, scale = 0.5f)
        assertEquals(Offset(100f, 50f), token.face.center)
        assertEquals(10f, token.face.width / 2f, 0f)
        assertEquals(token.face.width, token.face.height, 0f)
        assertEquals(token.face.width * StrandsBoardSpec.TokenCornerFraction, token.cornerRadius.x, 0f)
    }

    @Test
    fun `the clipper's token is the face alone`() {
        val bounds = Path().apply { addToken(Offset(50f, 50f), half = 20f) }.getBounds()
        assertEquals(30f, bounds.top, 0f)
        assertEquals(70f, bounds.bottom, 0f)
    }
}
