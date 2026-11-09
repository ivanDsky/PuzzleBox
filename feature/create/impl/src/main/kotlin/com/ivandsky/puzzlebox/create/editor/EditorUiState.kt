package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi

/** The Studio's state: the draft loading, open for editing, or not openable. */
sealed interface EditorUiState {

    data object Loading : EditorUiState

    /** No stored draft has the key's id, its game has no editor, or its bytes don't decode. */
    data object Missing : EditorUiState

    /**
     * The open draft: its [subtitle] ("Queens 4×4"), the live [verdict], and the game's
     * [editor] UI drawing the board over [controller]. The title lives in [DraftMeta]. An
     * [imported] draft was copied in from text: its Studio carries the Imported chip.
     */
    data class Ready(
        val subtitle: String,
        val verdict: Verdict,
        val editor: GameEditorUi,
        val controller: GameEditorController,
        val imported: Boolean = false,
    ) : EditorUiState
}
