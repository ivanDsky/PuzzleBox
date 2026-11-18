package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons

/**
 * The button right of the search field that opens and closes the chips, [open] being whether they show. While [active]
 * chips are on it is tinted and a badge says how many; with none on it is plain.
 */
@Composable
internal fun PickerFilterButton(active: Int, open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val tint = if (active > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    IconToggleButton(
        checked = open,
        onCheckedChange = { onToggle() },
        modifier = modifier.semantics { contentDescription = "Filters" }.testTag("duel.lineup.filter"),
        colors = IconButtonDefaults.iconToggleButtonColors(
            contentColor = tint,
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = tint,
        ),
    ) {
        BadgedBox(badge = {
            if (active > 0) Badge { Text("$active", modifier = Modifier.testTag("duel.lineup.filter.count")) }
        }) { Icon(PuzzleIcons.Filter, contentDescription = null) }
    }
}
