package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden matrix (design-direction §10.8, ticket 04 DoD): phone posture (w360dp) with
 * light + dark, all at fontScale 1.5 (T25 matrix precedent) — the reading text
 * (caption slot) must reflow while the board geometry stays pitch-exact. Plus the
 * tablet-landscape posture (w1280dp-h800dp, §10.8's second posture) in light.
 *
 * Rendered with `-Proborazzi.record=true` into `snapshots/` (untracked review captures); without
 * the flag the class does not run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-420dpi")
class TangoBoardGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capturePath(name: String) =
        File("snapshots/$name")

    private fun capture(name: String, dark: Boolean, fontScale: Float) {
        composeRule.setContent {
            val density = LocalDensity.current
            PuzzleBoxTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = fontScale),
                ) {
                    TangoBoardRenderer(onPlacement = {})
                        .Content(sampleBoard(), Modifier.fillMaxWidth().padding(16.dp))
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(capturePath(name).apply { parentFile?.mkdirs() })
    }

    // ---- phone posture (w360dp): light + dark ----

    /** §10.8: reading text reflows at fontScale 1.5, board geometry exact. */
    @Test fun `golden light placement A at fontScale 1_5`() =
        capture(
            "tango-board-light-a-frc1_5.png",
            dark = false,
            fontScale = 1.5f,
        )

    @Test fun `golden dark placement A at fontScale 1_5`() =
        capture(
            "tango-board-dark-a-frc1_5.png",
            dark = true,
            fontScale = 1.5f,
        )

    // ---- tablet-landscape posture (w1280dp-h800dp, §10.8's second posture) ----

    @Test
    @Config(qualifiers = "w1280dp-h800dp-240dpi")
    fun `golden tablet landscape light placement A at fontScale 1_5`() =
        capture(
            "tango-board-tablet-landscape-light-a-frc1_5.png",
            dark = false,
            fontScale = 1.5f,
        )

    /** Mid-play state: givens + placements + all four markers + live violations (§6,
     * state-driven scan: row 1 sun run + row 5 moon run are marked, no rejection event). */
    private fun sampleBoard() = TangoRendererModel(
        n = 6,
        cells = (0 until 36).map { idx ->
            TangoRendererModel.CellRender(
                value = when (idx) {
                    in setOf(0, 1, 6, 7, 8, 16, 29) -> TangoCell.SUN
                    in setOf(2, 14, 22, 32, 33, 34) -> TangoCell.MOON
                    else -> null
                },
                isGiven = idx in setOf(0, 1, 6, 7, 14, 16, 22, 29, 32, 33),
            )
        },
        markers = listOf(
            TangoRendererModel.MarkerRender(3, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
            TangoRendererModel.MarkerRender(18, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
            TangoRendererModel.MarkerRender(18, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
            TangoRendererModel.MarkerRender(34, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
        ),
        rejection = null,
        status = TangoRendererModel.Status.PLAYING,
    )
}
