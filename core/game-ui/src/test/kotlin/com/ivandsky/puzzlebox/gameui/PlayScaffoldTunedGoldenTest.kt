package com.ivandsky.puzzlebox.gameui

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
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
 * Goldens of [AdaptivePlayScaffold] under [PlayTuning.Starting] (nav-rethink B5, B6), light and
 * dark: the control row directly under the board at the board's width on a phone, a portrait
 * tablet and a landscape tablet, the phone's caption under the row, and the landscape side
 * column. A plain square stands for the board. Recorded with `-Proborazzi.record=true`,
 * verified with `-Proborazzi.verify=true`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PlayScaffoldTunedGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private object Undo : PlayUndo {
        override val canUndo = true
        override val canRedo = false
        override fun undo() = Unit
        override fun redo() = Unit
    }

    private object Hints : PlayHints {
        override val hintsUsed = 1
        override val hintEnabled = true
        override val hintCaption = "Hint: the next cell continues the path"
        override fun requestHint() = Unit
    }

    private val sideColumn = PlayTuning.Starting.copy(landscapeControls = LandscapeControls.SideColumn)

    @Test
    @Config(qualifiers = PHONE)
    fun `phone, row under the board, caption under the row`() =
        capture(PlayLayout.SinglePane, PlayTuning.Starting, dark = false, "PlayScaffoldTuned_phone.png")

    @Test
    @Config(qualifiers = PHONE)
    fun `phone, dark`() = capture(PlayLayout.SinglePane, PlayTuning.Starting, dark = true, "PlayScaffoldTuned_phone_dark.png")

    @Test
    @Config(qualifiers = TABLET_PORTRAIT)
    fun `portrait tablet, capped board with the row under it`() =
        capture(PlayLayout.SinglePane, PlayTuning.Starting, dark = false, "PlayScaffoldTuned_tabletPortrait.png")

    @Test
    @Config(qualifiers = TABLET_PORTRAIT)
    fun `portrait tablet, dark`() =
        capture(PlayLayout.SinglePane, PlayTuning.Starting, dark = true, "PlayScaffoldTuned_tabletPortrait_dark.png")

    @Test
    @Config(qualifiers = TABLET_LANDSCAPE)
    fun `landscape tablet, row under the board, no toolbar above it`() =
        capture(PlayLayout.TwoPane, PlayTuning.Starting, dark = false, "PlayScaffoldTuned_tabletLandscape.png")

    @Test
    @Config(qualifiers = TABLET_LANDSCAPE)
    fun `landscape tablet, dark`() =
        capture(PlayLayout.TwoPane, PlayTuning.Starting, dark = true, "PlayScaffoldTuned_tabletLandscape_dark.png")

    @Test
    @Config(qualifiers = TABLET_LANDSCAPE)
    fun `landscape tablet, controls in a column beside the board`() =
        capture(PlayLayout.TwoPane, sideColumn, dark = false, "PlayScaffoldTuned_tabletLandscape_sideColumn.png")

    @Test
    @Config(qualifiers = TABLET_LANDSCAPE)
    fun `landscape tablet side column, dark`() =
        capture(PlayLayout.TwoPane, sideColumn, dark = true, "PlayScaffoldTuned_tabletLandscape_sideColumn_dark.png")

    private fun capture(layout: PlayLayout, tuning: PlayTuning, dark: Boolean, file: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        composeRule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config, LocalPlayTuning provides tuning) {
                PuzzleBoxTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize()) {
                        AdaptivePlayScaffold(
                            layout = layout,
                            board = { paneCap ->
                                val cap = paneCap ?: PlayBoardMaxWidth
                                PlayBoardPane(Modifier.padding(horizontal = Spacing.lg)) {
                                    Box(
                                        Modifier
                                            .widthIn(max = cap)
                                            .heightIn(max = cap)
                                            .aspectRatio(1f)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
                                    )
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
        const val TABLET_PORTRAIT = "w800dp-h1280dp-mdpi"
        const val TABLET_LANDSCAPE = "w1280dp-h800dp-land-mdpi"
    }
}
