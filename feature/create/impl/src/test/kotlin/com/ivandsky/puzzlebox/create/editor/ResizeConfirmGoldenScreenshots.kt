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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ivandsky.puzzlebox.create.GOLDEN_PHONE
import com.ivandsky.puzzlebox.create.GOLDEN_TABLET
import com.ivandsky.puzzlebox.create.settleAndCapture
import com.ivandsky.puzzlebox.create.showGolden
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.game.VariantId
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
 * The resize confirm ("Start a blank board?") over a 6×6 Studio with content, picking 8×8:
 * phone (single pane) and tablet (two panes), light and dark. The board is the Studio
 * goldens' placeholder square. Goldens in `snapshots/studio/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class ResizeConfirmGoldenScreenshotsPhone : ResizeConfirmGoldenScreenshots(PlayLayout.SinglePane, "phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class ResizeConfirmGoldenScreenshotsTablet : ResizeConfirmGoldenScreenshots(PlayLayout.TwoPane, "tablet")

abstract class ResizeConfirmGoldenScreenshots(private val layout: PlayLayout, private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `resize confirm light`() = capture("resize-confirm", dark = false)
    @Test fun `resize confirm dark`() = capture("resize-confirm", dark = true)

    private fun capture(name: String, dark: Boolean) {
        rule.showGolden(dark) {
            Studio()
            ResizeConfirmDialog(sizeLabel = label(SIZES[4]), onConfirm = {}, onCancel = {})
        }
        rule.settleAndCapture("studio", name, device, dark)
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
                    sizeLabel = { label(it) },
                    onResize = {},
                    onClear = {},
                    hint = { Text("Drag across cells, then pick a colour", it, style = MaterialTheme.typography.bodySmall) },
                    modifier = stripModifier,
                )
            },
            actionArea = { areaModifier ->
                ActionArea(passed = null, canTestSolve = false, onTestSolve = {}, blocks = BLOCKS, modifier = areaModifier)
            },
        )
    }

    private companion object {
        val SIZES = (4..8).map { VariantId("queens/$it") }
        val INCOMPLETE = Verdict.Incomplete(IncompleteReason("queens.unpainted", 12))
        val BLOCKS = listOf(PublishBlock(PublishBlock.NOT_UNIQUE), PublishBlock(PublishBlock.NOT_TESTED))

        fun label(variant: VariantId): String = variant.value.substringAfter('/').let { "$it×$it" }
    }
}
