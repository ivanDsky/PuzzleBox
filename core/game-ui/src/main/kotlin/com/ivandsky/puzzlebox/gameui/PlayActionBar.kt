package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameButton
import com.ivandsky.puzzlebox.designsystem.GameButtonSize
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * The action bar every game shares (A14, A15): Undo · Redo · Hint · Clear, each a tall
 * chunky [GameButton] sharing the row equally. Hint fills with the game's [accent]; the
 * rest stay neutral. The hint half is CORE only as far as the button and the running
 * count ([hintsUsed], the session's A3 count); what a press does is the game's.
 * [onClear] null hides Clear (Sudoku keeps its own in the keypad); Clear is never disabled,
 * the rest are always shown, disabled when they cannot act. Games pass it as [AdaptivePlayScaffold]'s actions: under the caption on
 * a phone, a toolbar above the board on a landscape tablet.
 */
@Composable
internal fun PlayActionBar(
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onHint: () -> Unit,
    onClear: (() -> Unit)?,
    canUndo: Boolean,
    canRedo: Boolean,
    hintEnabled: Boolean,
    accent: AccentSlot,
    modifier: Modifier = Modifier,
    hintsUsed: Int = 0,
) {
    Row(
        modifier.fillMaxWidth().testTag("play.action_bar"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val action = Modifier.weight(1f)
        GameButton(onUndo, PuzzleIcons.Undo, "Undo", action.testTag("play.undo"), canUndo, size = GameButtonSize.Tall)
        GameButton(onRedo, PuzzleIcons.Redo, "Redo", action.testTag("play.redo"), canRedo, size = GameButtonSize.Tall)
        GameButton(
            onClick = onHint,
            icon = PuzzleIcons.Hint,
            label = if (hintsUsed == 0) "Hint" else "Hint · $hintsUsed",
            modifier = action.testTag("play.hint"),
            enabled = hintEnabled,
            accent = accent,
            size = GameButtonSize.Tall,
        )
        if (onClear != null) {
            GameButton(onClear, PuzzleIcons.Clear, "Clear", action.testTag("play.clear"), size = GameButtonSize.Tall)
        }
    }
}

/** [PlayActionBar] over the play seams: each control reads its game's state. */
@Composable
fun PlayActionBar(undo: PlayUndo, hints: PlayHints, clear: PlayClear?, accent: AccentSlot, modifier: Modifier = Modifier) {
    PlayActionBar(
        onUndo = undo::undo,
        onRedo = undo::redo,
        onHint = hints::requestHint,
        onClear = clear?.let { it::clear },
        canUndo = undo.canUndo,
        canRedo = undo.canRedo,
        hintEnabled = hints.hintEnabled,
        accent = accent,
        modifier = modifier,
        hintsUsed = hints.hintsUsed,
    )
}

// Preview: mid-play with one hint used, then a game with no Clear and nothing to undo
// (its accent differs to show Hint follows the game).
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
                accent = AccentSlot.Teal,
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
                accent = AccentSlot.Orange,
            )
        }
    }
}
