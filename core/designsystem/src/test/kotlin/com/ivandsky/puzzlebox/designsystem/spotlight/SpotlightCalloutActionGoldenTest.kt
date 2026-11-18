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
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The callout with its "Place it" pill over a plain 6×6 board on a 360 dp phone, light and dark, at the final frame. Rendered
 * with `-Proborazzi.record=true` (untracked review captures in `snapshots/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class SpotlightCalloutActionGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `callout with the Place it pill`() = capture(dark = false, "Spotlight_callout_action.png")

    @Test
    fun `callout with the Place it pill in the dark theme`() = capture(dark = true, "Spotlight_callout_action_dark.png")

    private fun capture(dark: Boolean, file: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val spotlight = Spotlight(
            rows = SIDE,
            cols = SIDE,
            roles = mapOf(
                14 to SpotlightRole.EVIDENCE, 15 to SpotlightRole.EVIDENCE, 16 to SpotlightRole.EVIDENCE,
                13 to SpotlightRole.TARGET, 20 to SpotlightRole.EXCLUDED, 21 to SpotlightRole.EXCLUDED,
            ),
            callout = SpotlightCallout("Last cell: one open cell left.", setOf(13, 14, 15, 16), SpotlightAction(PLACE_IT) {}),
            glyphs = DiscGlyphs,
            playId = 0,
        )
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
    }
}
