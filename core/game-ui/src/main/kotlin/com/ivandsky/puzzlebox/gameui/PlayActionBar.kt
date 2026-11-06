package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

/** Leading icon size of every action (identity §2.5). */
private val ActionIconSize = 20.dp

/**
 * The bottom action bar every game shares (A14): Undo · Redo · Hint · Clear, each a 20dp
 * PuzzleIcons glyph over a label. The hint half is CORE only as far as the button and the
 * running count ([hintsUsed], the session's A3 count); what a press does is the game's.
 * [onClear] null hides Clear (Tango has none); the rest are always shown, disabled when
 * they cannot act. Buttons keep the 48dp touch floor (§9).
 */
@Composable
fun PlayActionBar(
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onHint: () -> Unit,
    onClear: (() -> Unit)?,
    canUndo: Boolean,
    canRedo: Boolean,
    hintEnabled: Boolean,
    modifier: Modifier = Modifier,
    hintsUsed: Int = 0,
    canClear: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().testTag("play.action_bar"),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton("Undo", PuzzleIcons.Undo, onUndo, canUndo, "play.undo")
        ActionButton("Redo", PuzzleIcons.Redo, onRedo, canRedo, "play.redo")
        ActionButton(
            label = if (hintsUsed == 0) "Hint" else "Hint · $hintsUsed",
            icon = PuzzleIcons.Hint,
            onClick = onHint,
            enabled = hintEnabled,
            tag = "play.hint",
        )
        if (onClear != null) ActionButton("Clear", PuzzleIcons.Clear, onClear, canClear, "play.clear")
    }
}

/** [PlayActionBar] over the play seams: each control reads its game's state. */
@Composable
internal fun PlayActionBar(undo: PlayUndo, hints: PlayHints, clear: PlayClear?, modifier: Modifier = Modifier) {
    PlayActionBar(
        onUndo = undo::undo,
        onRedo = undo::redo,
        onHint = hints::requestHint,
        onClear = clear?.let { it::clear },
        canUndo = undo.canUndo,
        canRedo = undo.canRedo,
        hintEnabled = hints.hintEnabled,
        modifier = modifier,
        hintsUsed = hints.hintsUsed,
        canClear = clear?.canClear ?: false,
    )
}

@Composable
private fun ActionButton(label: String, icon: ImageVector, onClick: () -> Unit, enabled: Boolean, tag: String) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp).testTag(tag),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(ActionIconSize))
        Spacer(Modifier.width(Spacing.xs))
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// Preview: mid-play with one hint used, then a game with no Clear and nothing to undo.
@PuzzleBoxPreviews
@Composable
internal fun PlayActionBarPreview() {
    PuzzleBoxTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            PlayActionBar(
                onUndo = {},
                onRedo = {},
                onHint = {},
                onClear = {},
                canUndo = true,
                canRedo = false,
                hintEnabled = true,
                hintsUsed = 1,
            )
            PlayActionBar(
                onUndo = {},
                onRedo = {},
                onHint = {},
                onClear = null,
                canUndo = false,
                canRedo = false,
                hintEnabled = true,
            )
        }
    }
}
