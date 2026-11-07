package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription

/**
 * The published board: drawn as it was published, but a layer over it takes every touch and
 * TalkBack hears one read-only description, so no gesture or action reaches the game's board.
 */
@Composable
internal fun ReadOnlyBoard(board: @Composable (Modifier) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .testTag("create.studio.board.readOnly")
            .clearAndSetSemantics { contentDescription = "Published board, read-only" },
        propagateMinConstraints = true,
    ) {
        board(Modifier)
        Box(Modifier.matchParentSize().pointerInput(Unit) { swallowTouches() })
    }
}

private suspend fun PointerInputScope.swallowTouches() {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}
