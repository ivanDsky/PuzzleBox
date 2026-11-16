package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Box
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
import com.ivandsky.puzzlebox.designsystem.AppDropdownMenu
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.gameui.PlayOverflowItem

/** The play top bar: Up, the game's [glyph] and [title], then the play [actions]. */
@Composable
internal fun PlayTopBar(
    title: String,
    pastDay: Boolean,
    onBack: () -> Unit,
    glyph: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    CompactTopBar(
        title = title,
        modifier = if (pastDay) Modifier.testTag("game.past_day") else Modifier,
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("game.up")) {
                Icon(
                    imageVector = PuzzleIcons.Back,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            glyph()
        },
        actions = actions,
    )
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
