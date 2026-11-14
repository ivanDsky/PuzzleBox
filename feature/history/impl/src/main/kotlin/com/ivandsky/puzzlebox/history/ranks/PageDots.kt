package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.ivandsky.puzzlebox.designsystem.Spacing

private val DOT = 6.dp
private val DOT_SELECTED = 18.dp

/**
 * Where the swipe stands: one dot per board, the board at [index] drawn as a longer, accent one, which swells
 * to [pulseScale] (read while drawing) for the one-time nudge. With a [scrub] the dots follow it: the dot of the
 * board being left shrinks and fades as the next one's stretches and fills, as far as the scrub is between the two
 * (read where the dots are laid out and drawn), so they move with the finger and settle with the board. Without one,
 * and under reduced motion (the scrub does not move then), the dot steps at once. Nothing to show for a single board.
 */
@Composable
internal fun PageDots(
    count: Int,
    index: Int,
    modifier: Modifier = Modifier,
    pulseScale: () -> Float = { 1f },
    scrub: BoardScrub? = null,
) {
    if (count <= 1) return
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = "Board ${index + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { dot ->
            Dot(selected = dot == index, pulseScale) { scrub?.share(dot) ?: if (dot == index) 1f else 0f }
        }
    }
}

/** One dot, [share] (0 to 1) of the way from a plain one to the selected one. */
@Composable
private fun Dot(selected: Boolean, pulseScale: () -> Float, share: () -> Float) {
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .layout { measurable, _ ->
                val width = lerp(DOT, DOT_SELECTED, share()).roundToPx()
                val height = DOT.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(width, height))
                layout(width, height) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                if (selected) {
                    scaleX = pulseScale()
                    scaleY = scaleX
                }
            }
            .drawBehind { drawRoundRect(lerp(off, on, share()), cornerRadius = CornerRadius(size.height / 2)) },
    )
}
