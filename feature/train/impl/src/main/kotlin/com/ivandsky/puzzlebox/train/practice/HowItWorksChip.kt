package com.ivandsky.puzzlebox.train.practice

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

private val ChipHeight = 32.dp
private val BubbleWidth = 264.dp
private val BubbleGap = 36.dp

/** Takes no room in layout and puts its content [gap] below its top-left corner, free of the incoming constraints, so a bubble hangs over what is below without moving it. */
private fun Modifier.hangBelow(gap: Dp): Modifier = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(0, 0) { placeable.place(0, gap.roundToPx()) }
}

/** The header's "How it works" chip: [onClick] replays the intro. With [hint] the one-time bubble under it says so; [onDismissHint] is its OK. */
internal class HowItWorks(val onClick: () -> Unit, val hint: Boolean, val onDismissHint: () -> Unit)

/** A small outlined pill with an eye and "How it works", which opens the technique's intro again; the one-time hint hangs under it. */
@Composable
internal fun HowItWorksChip(chip: HowItWorks, modifier: Modifier = Modifier) {
    Box(modifier) {
        Row(
            Modifier
                .height(ChipHeight)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(onClick = chip.onClick)
                .padding(horizontal = Spacing.md)
                .testTag("train.howItWorks"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PuzzleIcons.Visibility, contentDescription = null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("How it works", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        if (chip.hint) HintBubble(chip.onDismissHint, Modifier.hangBelow(BubbleGap))
    }
}

/** The one-time bubble: what the chip is for, and OK. */
@Composable
internal fun HintBubble(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier.width(BubbleWidth).testTag("train.hint"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(start = Spacing.lg, top = Spacing.md, end = Spacing.sm, bottom = Spacing.xs)) {
            Text(
                "You can watch this intro again any time from here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
            Row(Modifier.align(Alignment.End)) {
                TextButton(onClick = onDismiss, Modifier.testTag("train.hint.ok")) {
                    Text("OK", color = MaterialTheme.colorScheme.inversePrimary)
                }
            }
        }
    }
}
