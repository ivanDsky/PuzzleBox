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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * Prototype 141 (throwaway): the debug switcher a tab pager draws over its bottom edge while
 * [TabSwipeProto.switcher] is on: one chip per [TabSwipeOption] under a line saying what the current one does and the
 * gesture that shows it. A tap changes the option for every tab pager at once. While the system's animations are off
 * a second line says so, because every option then shows its reduced form and they look alike.
 */
@Composable
internal fun TabSwipeSwitcher(modifier: Modifier = Modifier) {
    val current = TabSwipeProto.option
    val reduced = rememberReducedMotion()
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
            Text(
                current.caption,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (reduced) {
                Text(
                    ReducedNote,
                    modifier = Modifier.testTag("tabswipe.reduced"),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.inversePrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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

private const val ReducedNote = "Animations are off on this device: every option only fades in place"
