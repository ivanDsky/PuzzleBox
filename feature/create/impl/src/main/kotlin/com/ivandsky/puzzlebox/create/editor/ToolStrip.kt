package com.ivandsky.puzzlebox.create.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
import com.ivandsky.puzzlebox.game.VariantId

private val StripBorder = 2.dp
private val ToolWidth = 64.dp
private val ToolHeight = 56.dp
private val CompactTool = 48.dp
private val ToolIconSize = 20.dp

/**
 * The Studio tool strip under the board (prototype 02 A): a card with the studio accent
 * border holding Undo and Redo, each enabled only when [canUndo] / [canRedo] say a step
 * exists; Size, showing the open [size] and opening the [SizePopover] over [sizes] (the
 * game's variant table, A5) above the strip, left out when [sizes] holds one size or none
 * (nothing to pick); and Clear. A size pick other than the open one goes to [onResize];
 * Clear goes to [onClear]. Under the tools sits the game's one-line [hint]. Tools give press
 * feedback (scale [MotionTokens.PressScale]); reduced motion drops it.
 */
@Composable
fun ToolStrip(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    sizes: List<VariantId>,
    size: VariantId,
    sizeLabel: (VariantId) -> String,
    onResize: (VariantId) -> Unit,
    onClear: () -> Unit,
    hint: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(StripBorder, AccentSlot.Violet.colors().container, AppShapes.large)
            .testTag("create.tool.strip"),
        shape = AppShapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconTool("Undo", PuzzleIcons.Undo, canUndo, onUndo, "create.tool.undo")
                IconTool("Redo", PuzzleIcons.Redo, canRedo, onRedo, "create.tool.redo")
                if (sizes.size > 1) {
                    Tool(
                        "Size",
                        enabled = true,
                        active = picking,
                        onClick = { picking = !picking },
                        tag = "create.tool.size",
                    ) { ink ->
                        Text(sizeLabel(size), style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1)
                    }
                }
                IconTool("Clear", PuzzleIcons.Clear, enabled = true, onClick = onClear, tag = "create.tool.clear")
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm)) {
                hint(Modifier.fillMaxWidth())
            }
        }
        if (picking) {
            SizePopover(
                sizes = sizes,
                current = size,
                label = sizeLabel,
                onPick = { picked ->
                    picking = false
                    if (picked != size) onResize(picked)
                },
                onDismiss = { picking = false },
            )
        }
    }
}

@Composable
internal fun IconTool(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit, tag: String, compact: Boolean = false) {
    Tool(label, enabled, active = false, onClick, tag, compact) { ink ->
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(ToolIconSize))
    }
}

/**
 * One 64×56dp tool: [glyph] over label; [compact], a 48dp square of the glyph alone, the label
 * its description. A disabled tool keeps readable onSurfaceVariant ink, an [active] one (its
 * popover is open) sits on the studio accent container.
 */
@Composable
private fun Tool(
    label: String,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    tag: String,
    compact: Boolean = false,
    glyph: @Composable (ink: Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) MotionTokens.PressScale else 1f,
        animationSpec = spring(MotionTokens.SpringDampingDefault, MotionTokens.SpringStiffnessDefault),
        label = "tool-press",
    )
    val accent = AccentSlot.Violet.colors()
    val ink = when {
        active -> accent.onContainer
        enabled -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .size(if (compact) CompactTool else ToolWidth, if (compact) CompactTool else ToolHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(AppShapes.small)
            .background(if (active) accent.container else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(if (compact) Modifier.semantics { contentDescription = label } else Modifier)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.height(ToolIconSize), contentAlignment = Alignment.Center) { glyph(ink) }
        if (!compact) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Preview: a fresh draft (nothing to undo or redo), then mid-edit after an undo.
@PuzzleBoxPreviews
@Composable
private fun ToolStripPreview() {
    val sizes = (4..12).map { VariantId("queens/$it") }
    val label = { variant: VariantId -> variant.value.substringAfter('/').let { "$it×$it" } }
    PuzzleBoxTheme {
        Surface {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                ToolStrip(
                    canUndo = false,
                    canRedo = false,
                    onUndo = {},
                    onRedo = {},
                    sizes = sizes,
                    size = sizes[4],
                    sizeLabel = label,
                    onResize = {},
                    onClear = {},
                    hint = { PreviewHint(it) },
                )
                ToolStrip(
                    canUndo = true,
                    canRedo = true,
                    onUndo = {},
                    onRedo = {},
                    sizes = sizes,
                    size = sizes.last(),
                    sizeLabel = label,
                    onResize = {},
                    onClear = {},
                    hint = { PreviewHint(it) },
                )
            }
        }
    }
}

@Composable
private fun PreviewHint(modifier: Modifier) {
    Text(
        text = "Drag across cells, then pick a colour",
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
