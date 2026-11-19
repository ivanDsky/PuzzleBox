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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.window.PopupProperties
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.AppPopup
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

private val PopoverMaxWidth = 360.dp
private val PopoverElevation = 3.dp
private val ChipBorder = 2.dp
private val ChipMinWidth = 56.dp
private val ChipMinHeight = 48.dp

/** Origin-aware scale in from 0.9, never from 0 (02 handoff "Verdict and popovers"). */
private const val EnterFromScale = 0.9f

/**
 * A popover of the tool strip (02 handoff "Verdict and popovers"): [content] opens above its
 * anchor, the strip; an outside tap or Back goes to [onDismiss]. It scales in from 0.9 at its
 * bottom centre with a fade on MotionTokens medium (reduced motion: the plain fade); dismissal
 * is instant.
 */
@Composable
internal fun StripPopover(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val gap = with(LocalDensity.current) { Spacing.sm.roundToPx() }
    val reduced = rememberReducedMotion()
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    AppPopup(
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
            content()
        }
    }
}

/** A strip popover's card, tagged [tag]: its [content] in a column. */
@Composable
internal fun PopoverPanel(tag: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.widthIn(max = PopoverMaxWidth).testTag(tag),
        shape = AppShapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = PopoverElevation,
        shadowElevation = PopoverElevation,
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm), content = content)
    }
}

/**
 * One choice of a strip popover, at least 56×48dp: outlined on the surface; the checked one filled in the studio accent.
 * A [leadingIcon] sits before the word and takes the chip's content colour.
 */
@Composable
internal fun PopoverChip(
    text: String,
    selected: Boolean,
    tag: String,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                leadingIcon?.invoke()
                Text(text = text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}
