package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.ivandsky.puzzlebox.designsystem.AppDropdownMenu
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.PlayOverflowItem

/**
 * The play top bar: Up, the game's [glyph] and [title], then the play [actions]. With a [label] the title is a name
 * and what the screen is: [title] keeps to one line and is cut with an ellipsis when the bar is short, and [label]
 * follows it whole.
 */
@Composable
internal fun PlayTopBar(
    title: String,
    pastDay: Boolean,
    onBack: () -> Unit,
    label: String? = null,
    glyph: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val modifier = if (pastDay) Modifier.testTag("game.past_day") else Modifier
    val navigationIcon: @Composable () -> Unit = {
        IconButton(onClick = onBack, modifier = Modifier.testTag("game.up")) {
            Icon(
                imageVector = PuzzleIcons.Back,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        glyph()
    }
    if (label == null) {
        CompactTopBar(title = title, modifier = modifier, navigationIcon = navigationIcon, actions = actions)
    } else {
        CompactTopBar(title = { LabelledTitle(title, label) }, modifier = modifier, navigationIcon = navigationIcon, actions = actions)
    }
}

/** [name] on one line, cut with an ellipsis when the row is short, then [label], never cut. A blank [name] leaves [label] alone. */
@Composable
private fun RowScope.LabelledTitle(name: String, label: String) {
    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        val style = MaterialTheme.typography.titleLarge
        val color = MaterialTheme.colorScheme.onSurface
        if (name.isNotBlank()) {
            Text(name, Modifier.weight(1f, fill = false), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, style = style)
        }
        Text(if (name.isBlank()) label else "· $label", color = color, maxLines = 1, softWrap = false, style = style)
    }
}

/**
 * The game's settings behind the bar's overflow button, e.g. Queens' Auto-cross.
 * A toggle shows a check while on; picking an item closes the menu. No items, no button.
 */
@Composable
internal fun PlayOverflow(items: List<PlayOverflowItem>) {
    if (items.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("play.overflow")) {
            Icon(
                imageVector = PuzzleIcons.More,
                contentDescription = "More",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                val check: (@Composable () -> Unit)? =
                    if (item.checked == true) ({ Icon(PuzzleIcons.Check, contentDescription = "On") }) else null
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = {
                        open = false
                        item.onClick()
                    },
                    modifier = Modifier.testTag("play.overflow.item"),
                    trailingIcon = check,
                )
            }
        }
    }
}
