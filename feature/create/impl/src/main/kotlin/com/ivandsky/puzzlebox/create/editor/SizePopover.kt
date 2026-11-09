package com.ivandsky.puzzlebox.create.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.game.VariantId

private val PopoverMaxWidth = 360.dp
private val PopoverElevation = 3.dp
private val ChipBorder = 2.dp
private val ChipMinWidth = 56.dp
private val ChipMinHeight = 48.dp

/** Origin-aware scale in from 0.9, never from 0 (02 handoff "Verdict and popovers"). */
private const val EnterFromScale = 0.9f

/** The most sizes the hint names one by one; a longer table reads as a range. */
private const val MaxListedSizes = 4

/**
 * The size popover (02 handoff "Size popover"): opens above its anchor, the tool strip,
 * with one chip per entry of [sizes], the game's variant table (A5), and [current] checked.
 * A pick goes to [onPick]; an outside tap or Back goes to [onDismiss]. It scales in from 0.9
 * at its bottom centre with a fade on MotionTokens medium (reduced motion: the plain fade);
 * dismissal is instant.
 */
@Composable
internal fun SizePopover(
    sizes: List<VariantId>,
    current: VariantId,
    label: (VariantId) -> String,
    onPick: (VariantId) -> Unit,
    onDismiss: () -> Unit,
) {
    val gap = with(LocalDensity.current) { Spacing.sm.roundToPx() }
    val reduced = rememberReducedMotion()
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    Popup(
        popupPositionProvider = remember(gap) { WindowClamped.above(gap) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = shown,
            enter = if (reduced) {
                fadeIn(tween(MotionTokens.ReducedFadeDuration))
            } else {
                val spec = tween<Float>(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasizedDecelerate)
                scaleIn(spec, EnterFromScale, TransformOrigin(0.5f, 1f)) + fadeIn(spec)
            },
            exit = ExitTransition.None,
        ) {
            SizePanel(sizes, current, label, onPick)
        }
    }
}

/** The popover's card: the size chips, then the range hint. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SizePanel(
    sizes: List<VariantId>,
    current: VariantId,
    label: (VariantId) -> String,
    onPick: (VariantId) -> Unit,
) {
    Surface(
        modifier = Modifier.widthIn(max = PopoverMaxWidth).testTag("create.size.popover"),
        shape = AppShapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = PopoverElevation,
        shadowElevation = PopoverElevation,
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                sizes.forEach { size ->
                    SizeChip(label(size), size == current, "create.size.chip.${size.value}") { onPick(size) }
                }
            }
            Text(
                text = "Boards are ${sizeRangeText(sizes.map(label))}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("create.size.hint"),
            )
        }
    }
}

/** One size: outlined on the surface; the checked one filled in the studio accent. */
@Composable
private fun SizeChip(text: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val accent = AccentSlot.Violet.colors()
    val scheme = MaterialTheme.colorScheme
    val fill = if (selected) accent.accent else scheme.surface
    val edge = if (selected) accent.accent else scheme.outlineVariant
    Surface(
        modifier = Modifier
            .defaultMinSize(ChipMinWidth, ChipMinHeight)
            .clip(AppShapes.small)
            .border(ChipBorder, edge, AppShapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(tag),
        shape = AppShapes.small,
        color = fill,
        contentColor = if (selected) accent.onAccent else scheme.onSurface,
    ) {
        Box(Modifier.defaultMinSize(ChipMinWidth, ChipMinHeight).padding(horizontal = Spacing.sm), contentAlignment = Alignment.Center) {
            Text(text = text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/**
 * The variant table's sizes for the hint: up to four in full ("4×4, 6×6, 8×8 or 10×10", so a
 * table with gaps reads as it is), five or more as "first to last" ("4×4 to 12×12").
 */
internal fun sizeRangeText(labels: List<String>): String = when {
    labels.size <= 1 -> labels.firstOrNull().orEmpty()
    labels.size <= MaxListedSizes -> labels.dropLast(1).joinToString(", ") + " or " + labels.last()
    else -> "${labels.first()} to ${labels.last()}"
}

// Preview: the panel mid-table (7×7 checked) and at the table's top end (12×12 checked).
@PuzzleBoxPreviews
@Composable
private fun SizePanelPreview() {
    val sizes = (4..12).map { VariantId("queens/$it") }
    val label = { variant: VariantId -> variant.value.substringAfter('/').let { "$it×$it" } }
    PuzzleBoxTheme {
        Surface {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                SizePanel(sizes, current = sizes[3], label = label, onPick = {})
                SizePanel(sizes, current = sizes.last(), label = label, onPick = {})
            }
        }
    }
}
