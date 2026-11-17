package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow

/**
 * Prototype 141 (throwaway): the debug switcher a tab pager draws over its bottom edge while
 * [TabSwipeProto.switcher] is on: one chip per [TabSwipeOption] under a line saying what the current one does. A tap
 * changes the option for every tab pager at once.
 */
@Composable
internal fun TabSwipeSwitcher(modifier: Modifier = Modifier) {
    val current = TabSwipeProto.option
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.padding(bottom = Spacing.sm).testTag("tabswipe.switcher"),
        shape = MaterialTheme.shapes.large,
        color = scheme.inverseSurface,
        contentColor = scheme.inverseOnSurface,
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(current.caption, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TabSwipeOption.entries.forEach { option ->
                    val on = option == current
                    Box(
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(if (on) scheme.inverseOnSurface else Color.Transparent)
                            .clickable { TabSwipeProto.option = option }
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                            .testTag("tabswipe.option.${option.name}"),
                    ) {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (on) scheme.inverseSurface else scheme.inverseOnSurface,
                        )
                    }
                }
            }
        }
    }
}
