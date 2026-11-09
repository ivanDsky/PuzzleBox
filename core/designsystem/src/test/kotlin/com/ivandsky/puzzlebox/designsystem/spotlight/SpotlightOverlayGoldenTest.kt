package com.ivandsky.puzzlebox.designsystem.spotlight

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.BoardTokens
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Goldens of the four Spotlight states at their final frame over a plain 6×6 board, light and dark, with a stand-in
 * filled-disc glyph. Recorded with `-Proborazzi.record=true`, verified with `-Proborazzi.verify=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class SpotlightOverlayGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `deduction - evidence row, excluded marks and the target ghost`() = capture(DEDUCTION, dark = false, "Spotlight_deduction.png")

    @Test
    fun `deduction in the dark theme`() = capture(DEDUCTION, dark = true, "Spotlight_deduction_dark.png")

    @Test
    fun `elimination - evidence and excluded marks`() = capture(ELIMINATION, dark = false, "Spotlight_elimination.png")

    @Test
    fun `elimination in the dark theme`() = capture(ELIMINATION, dark = true, "Spotlight_elimination_dark.png")

    @Test
    fun `culprit - error glyph and badge, no hairline`() = capture(CULPRIT, dark = false, "Spotlight_culprit.png")

    @Test
    fun `culprit in the dark theme`() = capture(CULPRIT, dark = true, "Spotlight_culprit_dark.png")

    @Test
    fun `reveal - the target alone`() = capture(REVEAL, dark = false, "Spotlight_reveal.png")

    @Test
    fun `reveal in the dark theme`() = capture(REVEAL, dark = true, "Spotlight_reveal_dark.png")

    private fun capture(spotlight: Spotlight, dark: Boolean, file: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        composeRule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                PuzzleBoxTheme(darkTheme = dark) {
                    val board = LocalBoardColors.current
                    Box(Modifier.size(BOARD_SIDE)) {
                        Spacer(
                            Modifier.fillMaxSize().drawBehind {
                                drawRect(board.boardSurface)
                                val line = BoardTokens.boardGridLineWeight.toPx()
                                for (i in 0..SIDE) {
                                    val at = i * size.width / SIDE
                                    drawLine(board.boardGridLine, Offset(at, 0f), Offset(at, size.height), line)
                                    drawLine(board.boardGridLine, Offset(0f, at), Offset(size.width, at), line)
                                }
                            },
                        )
                        SpotlightOverlay(spotlight)
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            File(checkNotNull(System.getProperty("user.dir")))
                .resolve("snapshots/$file")
                .canonicalFile
                .absolutePath,
        )
    }

    /** Stand-in game glyph: a filled disc. */
    private data object DiscGlyphs : SpotlightGlyphs {
        override fun DrawScope.draw(cell: Int, role: SpotlightRole, size: Size, color: Color) {
            drawCircle(color, radius = size.minDimension * 0.3f)
        }
    }

    private companion object {
        const val SIDE = 6
        val BOARD_SIDE = 312.dp
        private val row = setOf(14, 15, 16)

        fun spotlight(roles: Map<Int, SpotlightRole>, text: String, anchor: Set<Int>) =
            Spotlight(SIDE, SIDE, roles, SpotlightCallout(text, anchor), DiscGlyphs, playId = 0)

        val DEDUCTION = spotlight(
            row.associateWith { SpotlightRole.EVIDENCE } + listOf(3, 33).associateWith { SpotlightRole.EXCLUDED } + (15 to SpotlightRole.TARGET),
            "Last cell: this row has one open cell left.",
            row,
        )
        val ELIMINATION = spotlight(
            row.associateWith { SpotlightRole.EVIDENCE } + listOf(2, 8, 26, 32).associateWith { SpotlightRole.EXCLUDED },
            "Pointing: the 4 in this box is on this row, so not elsewhere in it.",
            row,
        )
        val CULPRIT = spotlight(mapOf(21 to SpotlightRole.CULPRIT), "Wrong queen: hint again removes it.", setOf(21))
        val REVEAL = spotlight(mapOf(9 to SpotlightRole.TARGET), "Reveal: this cell is a moon.", setOf(9))
    }
}
