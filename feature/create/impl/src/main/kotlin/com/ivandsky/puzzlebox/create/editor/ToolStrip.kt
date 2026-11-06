package com.ivandsky.puzzlebox.create.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

private val StripBorder = 2.dp
private val ToolWidth = 64.dp
private val ToolHeight = 56.dp
private val ToolIconSize = 20.dp

/**
 * The Studio tool strip under the board (prototype 02 A): a card with the studio accent
 * border holding Undo and Redo, each enabled only when [canUndo] / [canRedo] say a step
 * exists, over the game's one-line [hint]. Tools give press feedback (scale
 * [MotionTokens.PressScale]); reduced motion drops it.
 */
@Composable
fun ToolStrip(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    hint: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(StripBorder, AccentSlot.Violet.colors().container, AppShapes.large)
            .testTag("create.tool.strip"),
        shape = AppShapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Tool("Undo", PuzzleIcons.Undo, canUndo, onUndo, "create.tool.undo")
                Tool("Redo", PuzzleIcons.Redo, canRedo, onRedo, "create.tool.redo")
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm)) {
                hint(Modifier.fillMaxWidth())
            }
        }
    }
}

/** One 64×56dp tool: icon over label; a disabled tool keeps readable onSurfaceVariant ink. */
@Composable
private fun Tool(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit, tag: String) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) MotionTokens.PressScale else 1f,
        animationSpec = spring(MotionTokens.SpringDampingDefault, MotionTokens.SpringStiffnessDefault),
        label = "tool-press",
    )
    val ink = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .size(ToolWidth, ToolHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(AppShapes.small)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(ToolIconSize))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = ink, maxLines = 1)
    }
}

// Preview: a fresh draft (nothing to undo or redo), then mid-edit after an undo.
@PuzzleBoxPreviews
@Composable
private fun ToolStripPreview() {
    PuzzleBoxTheme {
        Surface {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                ToolStrip(canUndo = false, canRedo = false, onUndo = {}, onRedo = {}, hint = { PreviewHint(it) })
                ToolStrip(canUndo = true, canRedo = true, onUndo = {}, onRedo = {}, hint = { PreviewHint(it) })
            }
        }
    }
}

@Composable
private fun PreviewHint(modifier: Modifier) {
    Text(
        text = "Tap a cell to cycle its region colour",
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
