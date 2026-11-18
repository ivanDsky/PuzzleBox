package com.ivandsky.puzzlebox.games.queensui

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensSession
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Queens board under the readable board theme, light and dark, on a compact phone. The 8×8 play
 * fixture has irregular regions, so the walls turn corners and end against the outline; two queens with their auto
 * crosses and two manual crosses show the glyph ink on several fills. Rendered with
 * `-Proborazzi.record=true` into the same `snapshots/` directory as QueensGoldenScreenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class QueensReadableBoardGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `readable board light`() = capture(dark = false)

    @Test
    fun `readable board dark`() = capture(dark = true)

    private fun capture(dark: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val puzzle = QueensPlayFixtures.puzzle()
        var session = QueensSession(ref = null, puzzle = puzzle)
        listOf(56, 57).forEach { session = session.withCrossToggled(Cell(it), QueensUiFixtures.T0) }
        listOf(0, 29).forEach { session = session.withQueenPlaced(Cell(it), QueensUiFixtures.T0) }
        val state = QueensPlayFixtures.boardState(session)
        val side = queensBoardSideFor(
            paneWidth = 360.dp,
            paneHeight = 640.dp,
            reservedTop = 56.dp,
            reservedBottom = 0.dp,
            cellsPerSide = puzzle.size,
            maxSide = QueensBoardDefaults.cellMaxSize * puzzle.size,
        )
        composeRule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                PuzzleBoxTheme(darkTheme = dark) {
                    QueensPlayBoard(state, Modifier.size(side))
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            File(checkNotNull(System.getProperty("user.dir")))
                .resolve("snapshots/queens-readable-compact-${if (dark) "dark" else "light"}.png")
                .canonicalFile
                .absolutePath,
        )
    }
}
