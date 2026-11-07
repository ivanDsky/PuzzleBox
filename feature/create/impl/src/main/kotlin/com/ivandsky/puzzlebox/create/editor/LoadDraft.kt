package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi

/**
 * Opens a stored draft for the Studio: its row, the board its game's editor decodes and that
 * game's editor UI. Null when the row, the editor or the UI is missing, or the blob does not
 * decode (a draft this build cannot open).
 */
internal class LoadDraft(
    private val drafts: DraftRepository,
    private val games: GameRegistry,
    private val editors: GameEditorRegistry,
) {

    /** An opened draft: the [stored] row, its decoded [draft] and the editor [ui] that shows it. */
    data class Loaded(val stored: Draft, val draft: EditorDraft<*>, val ui: GameEditorUi)

    suspend operator fun invoke(draftId: String): Loaded? {
        val stored = drafts.get(draftId) ?: return null
        val editor = games[stored.gameId]?.editor ?: return null
        val ui = editors[stored.gameId] ?: return null
        val draft = EditorDraft.open(editor, stored.blob).getOrNull() ?: return null
        return Loaded(stored, draft, ui)
    }
}
