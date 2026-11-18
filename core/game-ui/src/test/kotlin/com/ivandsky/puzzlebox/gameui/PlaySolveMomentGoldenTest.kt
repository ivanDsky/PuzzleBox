package com.ivandsky.puzzlebox.gameui

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import java.io.File
import java.time.Instant
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens of the solve moment B (nav-rethink 129) around a board in its pane, with the rest on and the clock held:
 * the solve itself, the comet turning the first corner at 700 ms, the line past half way at 1000 ms, the closed frame
 * at the 1500 ms hand-off, and reduced motion's outline. The game's glyph stamps onto the board with the pop (440 ms), so
 * every pose from 560 ms on wears it; reduced motion has none. The stamp has landed at 1000 ms in light and dark, on a phone and on a tablet. One pair follows Zip's rounded outline, whose path runs the
 * other way round; the popped board and the comet stay inside the pane's margin. A plain square stands for the board.
 * Rendered with `-Proborazzi.record=true` (untracked review captures in `snapshots/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h480dp-xhdpi")
class PlaySolveMomentGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var solved by mutableStateOf<Instant?>(null)

    /** Zip's board outline: a rounded rectangle as a generic path, as `ZipPlayUi` builds it. */
    private val rounded = GenericShape { size, _ -> addRoundRect(RoundRect(size.toRect(), CornerRadius(size.minDimension / 12))) }

    @After
    fun restoreMotion() = setAnimatorScale(1f)

    @Test fun `the solve, before anything moves`() = capture(0, "SolveMoment_0ms.png")

    @Test fun `the pop at its peak as the frame line starts`() = capture(560, "SolveMoment_560ms.png")

    @Test fun `the comet turns the first corner`() = capture(700, "SolveMoment_700ms.png")

    @Test fun `the line is past half way`() = capture(1_000, "SolveMoment_1000ms.png")

    @Test fun `the comet fades as the lap closes`() = capture(1_260, "SolveMoment_1260ms.png")

    @Test fun `the closed frame at the hand-off`() = capture(1_500, "SolveMoment_1500ms.png")

    @Test fun `the line is past half way, dark`() = capture(1_000, "SolveMoment_1000ms_dark.png", dark = true)

    @Test @Config(sdk = [34], qualifiers = TABLET)
    fun `the stamp has landed, tablet`() = capture(1_000, "SolveMoment_stamp_tablet.png")

    @Test @Config(sdk = [34], qualifiers = TABLET)
    fun `the stamp has landed, tablet dark`() = capture(1_000, "SolveMoment_stamp_tablet_dark.png", dark = true)

    @Test fun `the closed frame at the hand-off, dark`() = capture(1_500, "SolveMoment_1500ms_dark.png", dark = true)

    @Test fun `reduced motion, the outline only`() = capture(700, "SolveMoment_reduced.png", reduced = true)

    @Test fun `a rounded outline, the comet turns the first corner`() = capture(700, "SolveMoment_rounded_700ms.png", shape = rounded)

    @Test fun `a rounded outline, the closed frame`() = capture(1_500, "SolveMoment_rounded_1500ms.png", shape = rounded)

    private fun setAnimatorScale(scale: Float) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    /** The pane [ms] after the solve was seen. */
    private fun capture(ms: Long, file: String, dark: Boolean = false, reduced: Boolean = false, shape: Shape = RectangleShape) {
        if (reduced) setAnimatorScale(0f)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config, LocalSolveRestOnBoard provides true) {
                PuzzleBoxTheme(darkTheme = dark) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        PlayBoardPane(Modifier.padding(horizontal = Spacing.lg)) {
                            PlaySolveFrame(brand = Brand, solved = solved, onHandOff = {}, shape = shape) {
                                Box(
                                    Modifier
                                        .size(BOARD.dp)
                                        .background(MaterialTheme.colorScheme.surface, shape)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, shape),
                                )
                            }
                        }
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(FRAME_MS)
        solved = Instant.ofEpochSecond(1)
        Snapshot.sendApplyNotifications()
        // One frame for the composition to see the solve, one for the clock's first reading.
        composeRule.mainClock.advanceTimeBy(2 * FRAME_MS + ms)
        composeRule.onRoot().captureRoboImage(
            File(checkNotNull(System.getProperty("user.dir")))
                .resolve("snapshots/$file")
                .canonicalFile
                .absolutePath,
        )
    }

    private companion object {
        const val FRAME_MS = 16L
        const val TABLET = "w1280dp-h800dp-xhdpi"

        /** 328 dp is the widest board the 360 dp pane takes inside its gutters. */
        const val BOARD = 328
        val Brand = GameBrand(AccentSlot.Blue, GameBrand.Neutral.glyph)
    }
}
