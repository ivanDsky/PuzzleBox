package com.ivandsky.puzzlebox.gameui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.GameAccent
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.resultBeat
import com.ivandsky.puzzlebox.domain.SpreadScope

private const val SWAP_GLYPH = "⇄"
private val ChipRadius = 8.dp

/**
 * The histogram block: a heading whose scope word is a chip that swaps the scope (this weekday or
 * any day; kept across recomposition and rotation, not persisted), the caption, the chart, then
 * the verdict and notes. The heading and the text under the chart rise in on a swap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpreadBlock(spread: HeroSpread, palette: GameAccent, initialScope: SpreadScope?) {
    var picked by rememberSaveable(spread.defaultScope) { mutableStateOf(initialScope ?: spread.defaultScope) }
    val scope = if (spread.switchable) picked else SpreadScope.All
    val view = spread.views.getValue(scope)
    val chip = view.heading.chip
    val other = if (scope == SpreadScope.All) SpreadScope.Weekday else SpreadScope.All
    val headingStyle = MaterialTheme.typography.titleMedium
    FlowRow(
        modifier = Modifier.fillMaxWidth().resultBeat(ResultBeats.Blocks, 2).riseOnChange(scope).testTag("result.side.heading"),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (chip == null) view.heading.prefix else view.heading.prefix.trimEnd(),
            style = headingStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("result.side.heading.prefix"),
        )
        if (chip != null) {
            ScopeChip(
                word = chip,
                switchTo = spread.views.getValue(other).heading.chip.orEmpty(),
                palette = palette,
                onClick = { picked = other },
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )
            Text(
                text = view.heading.suffix.trimStart(),
                style = headingStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("result.side.heading.suffix"),
            )
        }
    }
    Text(
        text = view.caption,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xs).riseOnChange(scope).testTag("result.side.caption"),
    )
    TimeSpreadChart(view, spread.replay, palette, Modifier.padding(top = Spacing.sm))
    Text(
        text = view.verdict.annotated(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Spacing.sm).riseOnChange(scope).testTag("result.side.verdict"),
    )
    view.notes.forEachIndexed { index, note ->
        Text(
            text = note.annotated(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs).riseOnChange(scope).testTag("result.side.note.$index"),
        )
    }
}

/** The scope word in the heading: accent container, on-container text and a swap glyph, in a 48 dp hit area; one tap swaps scope. */
@Composable
private fun ScopeChip(word: String, switchTo: String, palette: GameAccent, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .testTag("result.side.chip")
            .clickable(role = Role.Button, onClickLabel = "Compare with $switchTo", onClick = onClick)
            .minimumInteractiveComponentSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$word $SWAP_GLYPH",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = palette.onContainer,
            maxLines = 1,
            modifier = Modifier
                .background(palette.container, RoundedCornerShape(ChipRadius))
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}

/** Fades and rises this block in again each time [key] changes after the first composition; reduced motion: a plain fade. */
private fun Modifier.riseOnChange(key: Any): Modifier = composed {
    val settled = LocalInspectionMode.current
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(1f) }
    val seen = remember { booleanArrayOf(false) }
    LaunchedEffect(key) {
        if (!seen[0]) {
            seen[0] = true
            return@LaunchedEffect
        }
        if (settled) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            1f,
            if (reduced) {
                tween(MotionTokens.ReducedFadeDuration)
            } else {
                tween(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate)
            },
        )
    }
    val rise = with(LocalDensity.current) { MotionTokens.EnterRise.toPx() }
    graphicsLayer {
        alpha = progress.value
        if (!reduced) translationY = (1f - progress.value) * rise
    }
}
