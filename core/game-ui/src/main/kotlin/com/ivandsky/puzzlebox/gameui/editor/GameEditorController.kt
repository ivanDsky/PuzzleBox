package com.ivandsky.puzzlebox.gameui.editor

import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the live draft and the transient UI selection for one editor screen. Every edit
 * goes out through the `onEdit` callback the controller was created with; the VM owns the
 * draft and hands replacements back through [adopt].
 */
interface GameEditorController {
    val draft: StateFlow<EditorDraft<*>>

    /** The VM replaces the draft after undo/redo/resize/clear; this clears the transient selection. */
    fun adopt(draft: EditorDraft<*>)

    /** Verdict-card witness choice; the default ignores it. */
    fun adoptOverlay(overlay: EditorOverlay) {}

    /**
     * The overlay the verdict card opens on for a new settled [verdict] (a 2+ solutions one);
     * the default is EditorOverlay(). A game whose author marks an answer opens on the other witness.
     */
    fun openingOverlay(verdict: Verdict): EditorOverlay = EditorOverlay()
}
