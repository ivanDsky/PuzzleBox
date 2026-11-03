package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Golden screenshot suite (queens-08 DoD): every meaningful board state × light/dark,
 * at compact (360dp) and expanded (1280dp) widths. Robolectric JVM capture (A17: no
 * emulator); recorded by default, verified with `-Proborazzi.verify`. Goldens live in
 * `.scratch/queens/design/evidence/goldens/` — the queens-09 HITL walk is
 * preview-first, device-confirm, so the state matrix is walkable from these.
 *
 * One state per test method: the activity renders exactly one board, so the
 * window-cropped capture is exactly that board (Roborazzi crops the window bitmap).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class QueensGoldenCompactTest : QueensGoldenTestBase("compact")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h900dp-420dpi")
class QueensGoldenExpandedTest : QueensGoldenTestBase("expanded")

abstract class QueensGoldenTestBase(private val widthLabel: String) {

    @get:Rule
    val composeRule = createComposeRule()

    private fun out(name: String, dark: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("../../.scratch/queens/design/evidence/goldens/$name-$widthLabel-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    /** Fixture board's regions ARE its rows (see QueensUiFixtures). */
    private fun state(queens: List<Int> = emptyList(), manual: List<Int> = emptyList()): QueensSession {
        var s = QueensSession(ref = null, puzzle = QueensUiFixtures.puzzle())
        manual.forEach { s = s.withCrossToggled(Cell(it), QueensUiFixtures.T0) }
        queens.forEach { s = s.withQueenPlaced(Cell(it), QueensUiFixtures.T0) }
        return s
    }

    private val solved: QueensSession by lazy {
        QueensUiFixtures.solutionCells.fold(QueensUiFixtures.session()) { acc, c ->
            acc.withQueenPlaced(c, QueensUiFixtures.T0)
        }
    }

    private fun capture(
        name: String,
        dark: Boolean,
        session: QueensSession,
        autoCheck: Boolean = false,
        controls: Boolean = false,
        overlay: Boolean = false,
    ) {
        val state = QueensBoardState(initialSession = session)
        state.autoCheck = autoCheck
        // Deterministic captures: the board's reduced-motion path (§5) snaps every
        // entrance to its end state — identical pixels, no mid-pop race in the bitmap.
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        android.provider.Settings.Global.putFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
        // §6 sizing policy decides the board side for this pane — computed, never eyeballed.
        val pane = if (widthLabel == "compact") 360 to 640 else 1280 to 900
        val side = queensBoardSideFor(
            paneWidth = pane.first.dp, paneHeight = pane.second.dp,
            reservedTop = 56.dp,
            reservedBottom = if (controls) 72.dp else 0.dp,
            cellsPerSide = QueensUiFixtures.puzzle().size,
        )
        composeRule.setContent {
            // Fidelity fix (fix round 3): `currentIsDarkTheme()` reads the configuration's
            // uiMode, so a darkTheme=true capture under the night-less Robolectric
            // qualifiers painted LIGHT fills under the dark scheme — every "dark" golden
            // was a light board. Each capture now sets the matching uiMode, as a device
            // would, so theme and palette agree.
            val config = android.content.res.Configuration(
                androidx.compose.ui.platform.LocalConfiguration.current,
            ).apply {
                uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) android.content.res.Configuration.UI_MODE_NIGHT_YES
                    else android.content.res.Configuration.UI_MODE_NIGHT_NO
            }
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides config,
            ) {
                PuzzleBoxTheme(darkTheme = dark) {
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.fillMaxWidth(),
                    ) {
                        androidx.compose.foundation.layout.Column {
                            QueensPlayBoard(state, Modifier.size(side))
                            if (controls) QueensPlayControls(state)
                        }
                        // R-3 (fix round 4): the win is presented FULLSCREEN over the
                        // play surface — the golden captures the screen-level overlay,
                        // not a board-pane-clipped banner.
                        if (overlay) QueensWinOverlay(state, onPlayAgain = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
        // R-5 (fix round 5): the win presents MODALLY in its own dialog window — the
        // screen capture includes every window (dialog + the dimmed play surface
        // behind it); single-window captures keep the old window-cropped path.
        if (overlay) {
            captureScreenRoboImage(java.io.File(out(name, dark)))
        } else {
            composeRule.onRoot().captureRoboImage(out(name, dark))
        }
    }

    // One method per (state × theme): the fixture board's regions are its rows, so a
    // queen auto-fills its own row — the §3.2/§3.3 provenance pair reads side by side.

    @Test fun `empty light`() = capture("empty", false, QueensUiFixtures.session())
    @Test fun `empty dark`() = capture("empty", true, QueensUiFixtures.session())

    @Test fun `manual x light`() = capture("x-manual", false, state(manual = listOf(0, 5, 10, 15)))
    @Test fun `manual x dark`() = capture("x-manual", true, state(manual = listOf(0, 5, 10, 15)))

    @Test fun `auto x light`() = capture("x-auto", false, state(queens = listOf(1), manual = listOf(15)))
    @Test fun `auto x dark`() = capture("x-auto", true, state(queens = listOf(1), manual = listOf(15)))

    @Test fun `queens light`() = capture("queens", false, state(queens = listOf(1, 8)))
    @Test fun `queens dark`() = capture("queens", true, state(queens = listOf(1, 8)))

    @Test fun `violation light`() = capture("violation", false, state(queens = listOf(1, 2)), autoCheck = true)
    @Test fun `violation dark`() = capture("violation", true, state(queens = listOf(1, 2)), autoCheck = true)

    @Test fun `win light`() = capture("win", false, solved, controls = true, overlay = true)
    @Test fun `win dark`() = capture("win", true, solved, controls = true, overlay = true)

    // R-2 (fix round 4): live violation with its self-explanatory caption near the controls.
    @Test fun `violation caption light`() = capture("violation-caption", false, state(queens = listOf(1, 2)), autoCheck = true, controls = true)
    @Test fun `violation caption dark`() = capture("violation-caption", true, state(queens = listOf(1, 2)), autoCheck = true, controls = true)
}
