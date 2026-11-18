package com.ivandsky.puzzlebox.gameui

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Goldens of the loading board inside the play layout ([AdaptivePlayScaffold], as the games draw it), on a
 * phone and a landscape tablet, light and dark: a quiet board-shaped square with its label and no spinner, capped like
 * a board, the tablet's side panel beside it. Today's label on the phone, the plain one on the tablet. Recorded with
 * `-Proborazzi.record=true` (untracked review captures in `snapshots/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BoardLoadingGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private object Undo : PlayUndo {
        override val canUndo = false
        override val canRedo = false
        override fun undo() = Unit
        override fun redo() = Unit
    }

    private object Hints : PlayHints {
        override val hintsUsed = 0
        override val hintEnabled = false
        override val hintCaption: String? = null
        override fun requestHint() = Unit
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `phone, today's puzzle`() = capture(PlayLayout.SinglePane, PlayBoardStatus.Loading(todaysDaily = true), dark = false, "BoardLoading_phone.png")

    @Test
    @Config(qualifiers = PHONE)
    fun `phone, dark`() = capture(PlayLayout.SinglePane, PlayBoardStatus.Loading(todaysDaily = true), dark = true, "BoardLoading_phone_dark.png")

    @Test
    @Config(qualifiers = TABLET_LANDSCAPE)
    fun `landscape tablet, any other slot`() = capture(PlayLayout.TwoPane, PlayBoardStatus.Loading(), dark = false, "BoardLoading_tabletLandscape.png")

    @Test
    @Config(qualifiers = TABLET_LANDSCAPE)
    fun `landscape tablet, dark`() = capture(PlayLayout.TwoPane, PlayBoardStatus.Loading(), dark = true, "BoardLoading_tabletLandscape_dark.png")

    private fun capture(layout: PlayLayout, status: PlayBoardStatus, dark: Boolean, file: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        composeRule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                PuzzleBoxTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize()) {
                        AdaptivePlayScaffold(
                            layout = layout,
                            board = {
                                PlayBoardPane(Modifier.padding(horizontal = Spacing.lg)) {
                                    PlayBoardFallback(status, onRetry = {}, tagPrefix = "play")
                                }
                            },
                            side = { PlayChrome(clock = null, hints = Hints) },
                            actions = { PlayActionBar(Undo, Hints, clear = PlayClear {}, accent = AccentSlot.Teal) },
                            sidePanel = { Text("Live stats") },
                        )
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

    private companion object {
        const val PHONE = "w360dp-h800dp-420dpi"
        const val TABLET_LANDSCAPE = "w1280dp-h800dp-land-mdpi"
    }
}
