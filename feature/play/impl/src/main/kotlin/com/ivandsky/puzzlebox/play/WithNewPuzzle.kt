package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable

/**
 * Runs the Community screen's [content] with the create action: [CustomSlot.newPuzzle]'s host around it when [enabled]
 * (the create FAB flag) and the host gave one, [content] given the host's "open the New puzzle sheet" call. Otherwise
 * [content] runs as it always did with null, and the screen draws no FAB.
 */
@Composable
internal fun WithNewPuzzle(custom: CustomSlot, enabled: Boolean, content: @Composable (onNewPuzzle: (() -> Unit)?) -> Unit) {
    val host = custom.newPuzzle
    if (enabled && host != null) host(CreateFabClearance) { open -> content(open) } else content(null)
}
