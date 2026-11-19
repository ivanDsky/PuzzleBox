package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameButton
import com.ivandsky.puzzlebox.designsystem.GameButtonSize
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.cooldownRing

/**
 * True where the scaffold stacks the controls in a column beside the board (a landscape
 * tablet's side column); [PlayActionBar] reads it, so a game passes the same bar everywhere.
 */
internal val LocalPlayControlsVertical = staticCompositionLocalOf { false }

/** The side column's one width: fixed, so a growing hint count never resizes the board beside it. */
private val ControlsColumnWidth = 96.dp

/**
 * The action bar every game shares (A14, A15): Undo · Redo · Hint · Clear, each a tall
 * chunky [GameButton] sharing the row equally. All four stay neutral, Hint included (a peer of
 * Undo); only its cooldown ring draws in the game's [accent].
 * The hint half is CORE only as far as the button and the running count ([hintsUsed], the session's A3 count); what a press does is the game's.
 * [onClear] null hides Clear (Sudoku keeps its own in the keypad); Clear is never disabled,
 * the rest are always shown, disabled when they cannot act. Games pass it as
 * [AdaptivePlayScaffold]'s actions, which places it (see there). Under
 * [LocalPlayControlsVertical] the same buttons stack in a column [ControlsColumnWidth] wide,
 * where [sideColumnExtra] (a game's own control, given the button's modifier) joins them
 * between Hint and Clear; a row never draws it.
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
    hintRing: (() -> Float)? = null,
    sideColumnExtra: (@Composable (Modifier) -> Unit)? = null,
) {
    val vertical = LocalPlayControlsVertical.current
    val buttons: @Composable (Modifier) -> Unit = { action ->
        GameButton(onUndo, PuzzleIcons.Undo, "Undo", action.testTag("play.undo"), canUndo, size = GameButtonSize.Tall)
        GameButton(onRedo, PuzzleIcons.Redo, "Redo", action.testTag("play.redo"), canRedo, size = GameButtonSize.Tall)
        GameButton(
            onClick = onHint,
            icon = PuzzleIcons.Hint,
            label = if (hintsUsed == 0) "Hint" else "Hint · $hintsUsed",
            modifier = action.testTag("play.hint").cooldownRing(hintRing, accent),
            enabled = hintEnabled,
            size = GameButtonSize.Tall,
        )
        if (vertical) sideColumnExtra?.invoke(action)
        if (onClear != null) {
            GameButton(onClear, PuzzleIcons.Clear, "Clear", action.testTag("play.clear"), size = GameButtonSize.Tall)
        }
    }
    if (vertical) {
        val column = modifier.width(ControlsColumnWidth).testTag("play.action_bar")
        Column(column, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            buttons(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier.fillMaxWidth().testTag("play.action_bar"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            buttons(Modifier.weight(1f))
        }
    }
}

/**
 * [PlayActionBar] over the play seams: each control reads its game's state. Under
 * [LocalHintCooldown] every Hint press starts the wait: Hint stays disabled and its ring fills
 * until it is over, whatever the press did; the game's own [PlayHints.hintEnabled] still wins.
 */
@Composable
fun PlayActionBar(
    undo: PlayUndo,
    hints: PlayHints,
    clear: PlayClear?,
    accent: AccentSlot,
    modifier: Modifier = Modifier,
    sideColumnExtra: (@Composable (Modifier) -> Unit)? = null,
) {
    PlayActionBar(undo, hints, clear, accent, modifier, rememberHintCooldown(board = hints), sideColumnExtra)
}

@Composable
internal fun PlayActionBar(
    undo: PlayUndo,
    hints: PlayHints,
    clear: PlayClear?,
    accent: AccentSlot,
    modifier: Modifier,
    cooldown: HintCooldown,
    sideColumnExtra: (@Composable (Modifier) -> Unit)? = null,
) {
    PlayActionBar(
        onUndo = undo::undo,
        onRedo = undo::redo,
        onHint = { cooldown.press(hints::requestHint) },
        onClear = clear?.let { it::clear },
        canUndo = undo.canUndo,
        canRedo = undo.canRedo,
        hintEnabled = cooldown.gate(hints.hintEnabled),
        accent = accent,
        modifier = modifier,
        hintsUsed = hints.hintsUsed,
        hintRing = cooldown.ring,
        sideColumnExtra = sideColumnExtra,
    )
}

// Preview: mid-play with one hint used, then a game with no Clear and nothing to undo
// (its accent differs to show the Hint ring follows the game).
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
