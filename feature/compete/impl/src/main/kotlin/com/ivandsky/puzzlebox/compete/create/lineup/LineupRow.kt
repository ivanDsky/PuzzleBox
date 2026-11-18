package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize

private val NUMBER_WIDTH = 24.dp
private val ROW_MIN_HEIGHT = 56.dp
private val LIFT_ELEVATION = 8.dp

/**
 * One puzzle of the lineup: its [position] (from 1), glyph, title and subtitle, and a remove button that is off on the last
 * puzzle, and a grip to drag it by ([drag]). A [lifted] row is raised. A [flashed] row takes the primary container colour for a moment, and a [refused] one is outlined as an error.
 * Tapping the row opens its change sheet; with no [onClick] the row is not tappable.
 */
@Composable
internal fun LineupRow(
    position: Int,
    model: LineupRowModel,
    brand: GameBrand,
    canRemove: Boolean,
    flashed: Boolean,
    refused: Boolean,
    lifted: Boolean,
    drag: RowDrag,
    onClick: (() -> Unit)?,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val container by animateColorAsState(
        targetValue = if (flashed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = if (reduced) snap() else tween(MotionTokens.DurationMedium),
        label = "lineup row flash",
    )
    val elevation by animateDpAsState(
        targetValue = if (lifted) LIFT_ELEVATION else 0.dp,
        animationSpec = if (reduced) snap() else tween(MotionTokens.DurationShort),
        label = "lineup row lift",
    )
    val outline = if (refused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth().heightIn(min = ROW_MIN_HEIGHT).testTag("duel.lineup.row.$position"),
        shape = MaterialTheme.shapes.medium,
        color = container,
        shadowElevation = elevation,
        border = BorderStroke(if (refused) 2.dp else 1.dp, outline),
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LineupDragHandle(drag, Modifier.testTag("duel.lineup.handle.$position"))
            Text(
                text = position.toString(),
                style = MaterialTheme.typography.titleMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(NUMBER_WIDTH),
            )
            GameGlyph(brand, GameGlyphSize.Chip)
            Column(Modifier.weight(1f)) {
                Text(model.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(model.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(
                onClick = onRemove,
                enabled = canRemove,
                modifier = Modifier.semantics { contentDescription = "Remove puzzle $position" }.testTag("duel.lineup.remove.$position"),
            ) { Icon(PuzzleIcons.Close, contentDescription = null) }
        }
    }
}
