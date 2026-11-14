package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import kotlin.math.min
import kotlinx.coroutines.delay

/** The title menu's row for [place] (the [selected] board is marked), the [index]th in line; a pick reports [onClick]. */
@Composable
internal fun BoardItem(place: BoardPlace, selected: Boolean, index: Int, reduced: Boolean, onClick: () -> Unit) {
    MenuRow(index, reduced, Modifier.testTag("ranks.menu.item.$index")) {
        DropdownMenuItem(
            text = {
                Text(
                    place.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else null,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            },
            leadingIcon = {
                if (selected) Icon(PuzzleIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(RanksMetrics.IconSize))
                else Spacer(Modifier.size(RanksMetrics.IconSize))
            },
            trailingIcon = { StandingText(place.standing, reduced) },
            onClick = onClick,
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "${place.name}, ${place.standing.describe()}" },
        )
    }
}

/** A place that changes (the boards answering, a window switch) crossfades in place rather than snapping. */
@Composable
private fun StandingText(standing: Standing, reduced: Boolean) {
    Crossfade(standing.label(), animationSpec = tween(if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.DurationMedium), label = "place") { label ->
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = RanksMetrics.PlaceMinWidth).padding(start = Spacing.sm),
        )
    }
}

/** A menu row that rises and fades in [index]th in line once the menu opens (the stagger is capped); reduced motion: a plain fade. */
@Composable
internal fun MenuRow(index: Int, reduced: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    val still = LocalInspectionMode.current
    val enter = remember { Animatable(if (still) 1f else 0f) }
    val rise = with(LocalDensity.current) { MotionTokens.EnterRise.toPx() }
    LaunchedEffect(Unit) {
        if (still) return@LaunchedEffect
        delay(min(index, MotionTokens.EnterStaggerCap).toLong() * MotionTokens.EnterStagger)
        val duration = if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.EnterDuration
        enter.animateTo(1f, tween(duration, easing = MotionTokens.EasingEmphasizedDecelerate))
    }
    Box(
        modifier.graphicsLayer {
            alpha = enter.value
            translationY = if (reduced) 0f else (1f - enter.value) * rise
        },
    ) { content() }
}

@PuzzleBoxPreviews
@Composable
private fun BoardItemPreview() {
    PuzzleBoxTheme {
        Box(Modifier.padding(Spacing.lg)) {
            BoardItem(BoardPlace(RanksBoard.Overall, "Overall", Standing.Place(12)), selected = true, index = 0, reduced = false) {}
        }
    }
}
