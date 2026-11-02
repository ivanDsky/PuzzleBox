package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
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
 * Golden matrix (design-direction §10.8, ticket 04 DoD): light + dark × both A11
 * placement variants, all at fontScale 1.5 (T25 matrix precedent) — the reading text
 * (caption slot) must reflow while the board geometry stays pitch-exact. Plus a
 * tablet-landscape capture per placement variant (the A/B scaffold's judged pane).
 *
 * Committed goldens live in `src/test/resources/goldens/`; the module records by
 * default and `-Proborazzi.verify` flips to pixel-drain verification.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-420dpi")
class TangoBoardGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun goldenPath(name: String) =
        File("src/test/resources/goldens/$name")

    private fun capture(name: String, dark: Boolean, variants: TangoAbVariants, fontScale: Float) {
        composeRule.setContent {
            val density = LocalDensity.current
            PuzzleBoxTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = fontScale),
                ) {
                    TangoBoardRenderer(variants = variants, onPlacement = {})
                        .Content(sampleBoard(), Modifier.fillMaxWidth().padding(16.dp))
                }
            }
        }
        composeRule.waitForIdle()
        if (variants.placement == TangoPlacementVariant.TwoTapChooser) {
            // The B golden must show the variant's actual surface: tap an empty player
            // cell to open the anchored chooser before capture.
            composeRule.onNodeWithContentDescription("Row 3, column 2", substring = true)
                .performClick()
            composeRule.waitForIdle()
        }
        composeRule.onRoot().captureRoboImage(goldenPath(name))
    }

    /** §10.8: reading text reflows at fontScale 1.5, board geometry exact. */
    @Test fun `golden light placement A at fontScale 1_5`() =
        capture(
            "tango-board-light-a-frc1_5.png",
            dark = false,
            variants = TangoAbVariants(placement = TangoPlacementVariant.TapCycle),
            fontScale = 1.5f,
        )

    @Test fun `golden dark placement A at fontScale 1_5`() =
        capture(
            "tango-board-dark-a-frc1_5.png",
            dark = true,
            variants = TangoAbVariants(placement = TangoPlacementVariant.TapCycle),
            fontScale = 1.5f,
        )

    @Test fun `golden light placement B at fontScale 1_5`() =
        capture(
            "tango-board-light-b-frc1_5.png",
            dark = false,
            variants = TangoAbVariants(placement = TangoPlacementVariant.TwoTapChooser),
            fontScale = 1.5f,
        )

    @Test fun `golden dark placement B at fontScale 1_5`() =
        capture(
            "tango-board-dark-b-frc1_5.png",
            dark = true,
            variants = TangoAbVariants(placement = TangoPlacementVariant.TwoTapChooser),
            fontScale = 1.5f,
        )

    /** Mid-play state: givens + placements + all four markers + a live violation (§6). */
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
        rejection = TangoRendererModel.RejectedMove(
            8,
            com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict.Reason.ThreeInARow,
        ),
        status = TangoRendererModel.Status.PLAYING,
    )
}
