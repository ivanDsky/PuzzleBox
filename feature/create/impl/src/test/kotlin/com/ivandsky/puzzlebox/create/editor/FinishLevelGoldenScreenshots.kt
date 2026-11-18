package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.create.GOLDEN_PHONE
import com.ivandsky.puzzlebox.create.GOLDEN_TABLET
import com.ivandsky.puzzlebox.create.settleAndCapture
import com.ivandsky.puzzlebox.create.showGolden
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 90: the Finish level picker open over the Studio, after a tap on the Finish tool: the question,
 * the four levels with the author's last choice (Hard) checked, and the note on the harder two.
 * Phone, phone in landscape (one pane, like the app: too short for two) and tablet (the strip sits
 * in a different place in each), light and dark. The board is the Studio preview's placeholder
 * square. The popover is its own window, so the tap runs on a live clock and the whole screen is
 * captured. Rendered with `-Proborazzi.record=true` into `snapshots/finish-level/` (untracked
 * review captures).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class FinishLevelGoldenScreenshotsPhone : FinishLevelGoldenScreenshots(PlayLayout.SinglePane, "phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w891dp-h411dp-land-420dpi")
class FinishLevelGoldenScreenshotsLandscape : FinishLevelGoldenScreenshots(PlayLayout.SinglePane, "landscape")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class FinishLevelGoldenScreenshotsTablet : FinishLevelGoldenScreenshots(PlayLayout.TwoPane, "tablet")

abstract class FinishLevelGoldenScreenshots(private val layout: PlayLayout, private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `picker light`() = capture(dark = false)
    @Test fun `picker dark`() = capture(dark = true)

    private fun capture(dark: Boolean) {
        rule.showGolden(dark, frozen = false) { Studio() }
        rule.onNodeWithTag("create.tool.finish").performClick()
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithTag("create.finish.levels").fetchSemanticsNodes().isNotEmpty() }
        rule.settleAndCapture("finish-level", "picker", device, dark)
    }

    @Composable
    private fun Studio() {
        StudioScreen(
            title = "Untitled 3",
            subtitle = "Queens 6×6",
            saving = false,
            verdict = INCOMPLETE,
            cardOpen = false,
            overlay = EditorOverlay(),
            autoShow = true,
            layout = layout,
            onBack = {},
            onRename = {},
            onRollTitle = { "Amber Falcon" },
            onDuplicate = {},
            onDelete = {},
            board = { boardModifier ->
                Box(boardModifier, contentAlignment = Alignment.Center) {
                    Box(Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true).background(MaterialTheme.colorScheme.surfaceContainerHighest, AppShapes.small))
                }
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
                    hint = { Text("Drag across cells, then pick a colour", it, style = MaterialTheme.typography.bodySmall) },
                    modifier = stripModifier,
                    finish = FinishSlot(enabled = true, busy = false, level = Difficulty.Level.Hard, onFinish = {}, onCancel = {}),
                )
            },
            actionArea = { areaModifier ->
                ActionArea(passed = null, canTestSolve = false, onTestSolve = {}, blocks = BLOCKS, modifier = areaModifier)
            },
        )
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        val SIZES = (4..8).map { VariantId("queens/$it") }
        val INCOMPLETE = Verdict.Incomplete(IncompleteReason("queens.unpainted", 12))
        val BLOCKS = listOf(PublishBlock(PublishBlock.NOT_UNIQUE), PublishBlock(PublishBlock.NOT_TESTED))
    }
}
