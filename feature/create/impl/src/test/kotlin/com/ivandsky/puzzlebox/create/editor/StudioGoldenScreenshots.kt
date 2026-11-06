package com.ivandsky.puzzlebox.create.editor

import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.LadderRung
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 29: the Studio chrome in every verdict state (spec S15, A7): Incomplete, Checking, Unique
 * with a difficulty, Unique · needs guessing (blocked), 2+ solutions, no solution and
 * Undetermined. On the phone the pill's card is open over the bar (a popup, so the whole
 * screen is captured); on the tablet the card sits inline in the side pane. Light and dark.
 * The board is the Studio preview's placeholder square: the game's own looks are the
 * Queens editor goldens (games/queens-ui) and the app's Studio composites. Recorded by
 * default, verified with `-Proborazzi.verify=true`; goldens in `snapshots/studio/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StudioGoldenScreenshotsPhone : StudioGoldenScreenshots(PlayLayout.SinglePane, "phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class StudioGoldenScreenshotsTablet : StudioGoldenScreenshots(PlayLayout.TwoPane, "tablet")

abstract class StudioGoldenScreenshots(private val layout: PlayLayout, private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `incomplete light`() = capture("incomplete", INCOMPLETE, dark = false)
    @Test fun `incomplete dark`() = capture("incomplete", INCOMPLETE, dark = true)

    @Test fun `checking light`() = capture("checking", Verdict.Checking, dark = false)
    @Test fun `checking dark`() = capture("checking", Verdict.Checking, dark = true)

    @Test fun `unique with difficulty light`() = capture("unique-difficulty", UNIQUE, dark = false)
    @Test fun `unique with difficulty dark`() = capture("unique-difficulty", UNIQUE, dark = true)

    @Test fun `unique needs guessing light`() = capture("unique-guessing", GUESSING, dark = false)
    @Test fun `unique needs guessing dark`() = capture("unique-guessing", GUESSING, dark = true)

    @Test fun `multiple light`() = capture("multiple", MULTIPLE, dark = false)
    @Test fun `multiple dark`() = capture("multiple", MULTIPLE, dark = true)

    @Test fun `none light`() = capture("none", Verdict.None(setOf(0, 5)), dark = false)
    @Test fun `none dark`() = capture("none", Verdict.None(setOf(0, 5)), dark = true)

    @Test fun `undetermined light`() = capture("undetermined", Verdict.Undetermined, dark = false)
    @Test fun `undetermined dark`() = capture("undetermined", Verdict.Undetermined, dark = true)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, verdict: Verdict, dark: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val card = VerdictCardState(recheck = {}).apply {
            read(verdict)
            if (layout == PlayLayout.SinglePane) show()
        }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                // The app's root Scaffold: the background colour, onBackground content colour.
                PuzzleBoxTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Studio(verdict, card) }
                }
            }
        }
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        captureScreenRoboImage(out(name, dark))
    }

    @Composable
    private fun Studio(verdict: Verdict, card: VerdictCardState) {
        StudioScreen(
            title = "Untitled 3",
            subtitle = "Queens 6×6",
            saving = false,
            verdict = verdict,
            verdictCard = card,
            layout = layout,
            onBack = {},
            onRename = {},
            onDuplicate = {},
            onDelete = {},
            board = { boardModifier ->
                Box(boardModifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, AppShapes.small))
            },
            toolStrip = { stripModifier ->
                ToolStrip(
                    canUndo = true,
                    canRedo = false,
                    onUndo = {},
                    onRedo = {},
                    sizes = SIZES,
                    size = SIZES[2],
                    sizeLabel = { variant -> variant.value.substringAfter('/').let { "$it×$it" } },
                    onResize = {},
                    onClear = {},
                    hint = { Text("Mode", it, style = MaterialTheme.typography.bodySmall) },
                    modifier = stripModifier,
                )
            },
            actionArea = { areaModifier ->
                ActionArea(passed = null, canTestSolve = verdict is Verdict.Unique, onTestSolve = {}, blocks = blocksOf(verdict), modifier = areaModifier)
            },
        )
    }

    private fun out(name: String, dark: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/studio/$name-$device-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    /** The readiness line's blocks, as the domain gates emit them for each verdict. */
    private fun blocksOf(verdict: Verdict): List<PublishBlock> = when {
        verdict is Verdict.Unique && verdict.needsGuessing -> listOf(PublishBlock(PublishBlock.NEEDS_GUESSING), NOT_TESTED)
        verdict is Verdict.Unique -> listOf(NOT_TESTED)
        else -> listOf(PublishBlock(PublishBlock.NOT_UNIQUE), NOT_TESTED)
    }

    /** A fake ladder's middle rung: the difficulty chip reads its level only. */
    private object MediumRung : LadderRung {
        override val id = "golden.medium"
        override val level = Difficulty.Level.Medium
        override val captionKey = "golden.medium"
    }

    private companion object {
        const val SETTLE_MS = 2_000L

        val SIZES = (4..8).map { VariantId("queens/$it") }
        val NOT_TESTED = PublishBlock(PublishBlock.NOT_TESTED)

        val SOLUTION_A = Solution(listOf(1, 9, 12, 22, 26, 35))
        val SOLUTION_B = Solution(listOf(1, 9, 12, 23, 26, 34))

        val INCOMPLETE = Verdict.Incomplete(IncompleteReason("queens.unpainted", 12))
        val UNIQUE = Verdict.Unique(SOLUTION_A, Difficulty(MediumRung))
        val GUESSING = Verdict.Unique(SOLUTION_A, needsGuessing = true)
        val MULTIPLE = Verdict.Multiple(SOLUTION_A, SOLUTION_B, setOf(22, 23, 34, 35))
    }
}
