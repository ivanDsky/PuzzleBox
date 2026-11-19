package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal val FilterHeaderHeight = 56.dp
private val IconButtonSize = 48.dp

/** A filter surface's header: a 48dp button with [icon] ([iconLabel] describes it), then the [title] as a heading; at least 56dp tall. */
@Composable
internal fun FilterHeader(
    title: String,
    icon: ImageVector,
    iconLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTag: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = FilterHeaderHeight).padding(end = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(IconButtonSize).then(if (iconTag == null) Modifier else Modifier.testTag(iconTag))) {
            Icon(icon, contentDescription = iconLabel)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
    }
}

/** The page's header: a 48dp Back arrow (description `Back`, [backTag]) and the [title] as a heading, at least 56dp tall. */
@Composable
internal fun FilterPageHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, backTag: String? = null) {
    FilterHeader(title, PuzzleIcons.Back, "Back", onBack, modifier, backTag)
}
