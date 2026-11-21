package com.ivandsky.puzzlebox.train.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing

private val RowHeight = 48.dp

/**
 * The watch column of the intro: the variants to pick from (only with two or more), the step track of the one shown
 * ("Watch it done"), its written rule with how to spot it and what to do, and Replay with the button that leaves the
 * intro ([primary], "Start practice", or "Back to board 1" in a replay). [compact] is the phone's layout, with the two
 * buttons side by side; otherwise they are stacked full width (the prototype's action stack), so neither label wraps.
 */
@Composable
internal fun WatchColumn(
    entries: List<IntroEntry>,
    variant: Int,
    frame: Int,
    primary: String,
    compact: Boolean,
    onSelect: (Int) -> Unit,
    onSeek: (Int) -> Unit,
    onReplay: () -> Unit,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = entries[variant]
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (entries.size > 1) VariantList(entries, variant, compact, onSelect)
        Label("Watch it done")
        StepTrack(entry.demo.captions, frame, compact, onSeek)
        Rule(entry.variant.rule, entry.variant.spotIt, entry.variant.doIt)
        if (compact) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton("Replay", onReplay, Modifier.weight(1f).testTag("train.watch.replay"))
                PrimaryButton(primary, onPrimary, Modifier.weight(1f).testTag("train.watch.start"))
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton("Replay", onReplay, Modifier.fillMaxWidth().testTag("train.watch.replay"))
                PrimaryButton(primary, onPrimary, Modifier.fillMaxWidth().testTag("train.watch.start"))
            }
        }
    }
}

@Composable
private fun Label(text: String) =
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** The variants as a list of rows, or on the phone side by side; the one shown is lifted and tapping another plays it. */
@Composable
private fun VariantList(entries: List<IntroEntry>, selected: Int, compact: Boolean, onSelect: (Int) -> Unit) {
    val item: @Composable (Int, Modifier) -> Unit = { index, modifier ->
        Row(
            modifier
                .heightIn(min = RowHeight)
                .clip(MaterialTheme.shapes.small)
                .background(if (index == selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface)
                .clickable { onSelect(index) }
                .semantics(mergeDescendants = true) { this.selected = index == selected }
                .padding(horizontal = Spacing.md)
                .testTag("train.watch.variant.$index"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                entries[index].variant.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    if (compact) {
        Row(Modifier.fillMaxWidth().testTag("train.watch.variants"), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            entries.indices.forEach { item(it, Modifier.weight(1f)) }
        }
    } else {
        Column(Modifier.fillMaxWidth().testTag("train.watch.variants"), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            entries.indices.forEach { item(it, Modifier.fillMaxWidth()) }
        }
    }
}

/** The variant's written rule, then its Spot it and Do it lines. */
@Composable
private fun Rule(rule: String, spotIt: String, doIt: String) {
    Column(Modifier.testTag("train.watch.rule"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(rule, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Line("Spot it", spotIt)
        Line("Do it", doIt)
    }
}

@Composable
private fun Line(label: String, text: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
