package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.create.GOLDEN_PHONE
import com.ivandsky.puzzlebox.create.settleAndCapture
import com.ivandsky.puzzlebox.create.showGolden
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Difficulty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 62: the tool strip with the Finish tool at phone width: disabled, enabled and busy, stacked in
 * one capture, light and dark; Undo, Redo, Size and Clear keep their full width beside it.
 * Rendered with `-Proborazzi.record=true` into `snapshots/finish-tool/` (untracked review
 * captures).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class FinishToolGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `states light`() = capture(dark = false)
    @Test fun `states dark`() = capture(dark = true)

    private fun capture(dark: Boolean) {
        rule.showGolden(dark) {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                strip("Disabled", slot(enabled = false, busy = false))
                strip("Enabled", slot(enabled = true, busy = false))
                strip("Busy", slot(enabled = true, busy = true))
            }
        }
        rule.settleAndCapture("finish-tool", "states", "phone", dark)
    }

    private fun slot(enabled: Boolean, busy: Boolean) =
        FinishSlot(enabled, busy, level = Difficulty.Level.Medium, onFinish = {}, onCancel = {})

    @Composable
    private fun strip(label: String, finish: FinishSlot) {
        val sizes = (4..8).map { VariantId("queens/$it") }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ToolStrip(
                canUndo = true,
                canRedo = false,
                onUndo = {},
                onRedo = {},
                sizes = sizes,
                size = sizes[2],
                sizeLabel = { variant -> variant.value.substringAfter('/').let { "$it×$it" } },
                onResize = {},
                onClear = {},
                hint = { Text("Drag across cells, then pick a colour", it, style = MaterialTheme.typography.bodySmall) },
                finish = finish,
            )
        }
    }
}
