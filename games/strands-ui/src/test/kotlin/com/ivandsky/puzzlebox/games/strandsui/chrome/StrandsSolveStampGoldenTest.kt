package com.ivandsky.puzzlebox.games.strandsui.chrome

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayController
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayScreen
import com.ivandsky.puzzlebox.games.strandsui.StrandsUiFixtures
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.LocalSolveRestOnBoard
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import java.io.File
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
 * Golden of a Strands solve moment with the game's glyph stamped on the board (nav-rethink 139), light and dark,
 * 1000 ms after the winning word: the stamp has landed with the pop, and the frame line is lapping the board.
 * Rendered with `-Proborazzi.record=true` (untracked review captures in `snapshots/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StrandsSolveStampGoldenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test fun `the stamp has landed, light`() = capture(dark = false, "solve-stamp-light.png")

    @Test fun `the stamp has landed, dark`() = capture(dark = true, "solve-stamp-dark.png")

    private fun capture(dark: Boolean, file: String) {
        val controller = StrandsPlayController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}))
        assertEquals(Adopt.Adopted, controller.adoptToday(StrandsUiFixtures.envelope(), StrandsUiFixtures.ref))
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config, LocalSolveRestOnBoard provides true) {
                PuzzleBoxTheme(darkTheme = dark) {
                    StrandsPlayScreen(
                        controller = controller,
                        status = PlayBoardStatus.Ready,
                        layout = PlayLayout.SinglePane,
                        sidePanel = null,
                        onRetry = {},
                        onSolved = { _, _ -> },
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(16)
        rule.runOnIdle {
            (0 until 4).forEach { row ->
                StrandsUiFixtures.row(row).also { path ->
                    path.forEach(controller::tap)
                    controller.tap(path.last())
                }
            }
        }
        // 50 ms steps: with nothing animating, one big step would skip past the clock's wake-ups.
        repeat(1_000 / 50) { rule.mainClock.advanceTimeBy(50L) }
        rule.onRoot().captureRoboImage(
            File(checkNotNull(System.getProperty("user.dir"))).resolve("snapshots/$file").canonicalFile.absolutePath,
        )
    }
}
