package com.ivandsky.puzzlebox.create.importtext

import com.ivandsky.puzzlebox.domain.ImportDraftFromText
import com.ivandsky.puzzlebox.domain.ImportResult
import com.ivandsky.puzzlebox.game.editor.NotationHeader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Import from text sheet's holder (spec A23): sheet state, submit, navigate on import.
 * One concern, so every screen that offers the sheet owns one. [onImported] gets the new
 * draft's id once, after the sheet has hidden. [gameName] is the registry's display name for
 * the clipboard banner.
 */
class ImportTextState(
    private val importDraft: ImportDraftFromText,
    private val scope: CoroutineScope,
    private val gameName: (gameId: String) -> String,
    private val onImported: (draftId: String) -> Unit,
) {
    private val state = MutableStateFlow(ImportSheetUi())
    private var job: Job? = null

    /** True once this open has had its clipboard offer (or while closed), so it is read once. */
    private var offered = true

    val ui: StateFlow<ImportSheetUi> = state.asStateFlow()

    fun open() {
        offered = false
        state.value = ImportSheetUi(visible = true)
    }

    fun edit(text: String) {
        state.update { it.copy(text = text, error = null) }
    }

    /**
     * checking = true → importDraft(text) → Imported: hide + [onImported]; else the error (text
     * kept, cleared for TooLarge). A result never changes `visible`. A submit while one is
     * running is dropped; [dismiss] cancels the running one, so a check dismissed before it stores
     * anything never imports.
     */
    fun submit(text: String) {
        if (state.value.checking) return
        state.update { it.copy(text = text, checking = true, error = null) }
        job = scope.launch {
            when (val result = importDraft(text)) {
                is ImportResult.Imported -> {
                    state.value = ImportSheetUi()
                    onImported(result.draftId)
                }
                is ImportResult.TooLarge -> state.update { it.copy(text = "", checking = false, error = result) }
                else -> state.update { it.copy(checking = false, error = result) }
            }
        }
    }

    /**
     * Called once per sheet open with the clipboard's text, which the sheet reads only from this
     * user-initiated surface. Sets `ui.clipboard` when [NotationHeader.find] matches; a null,
     * non-notation, later or closed-sheet offer changes nothing.
     */
    fun offerClipboard(text: String?) {
        if (offered || !state.value.visible) return
        offered = true
        val header = text?.let(NotationHeader::find) ?: return
        val lines = text.trimEnd().lines().size
        state.update { it.copy(clipboard = ImportSheetUi.ClipboardNotation(gameName(header.gameId), lines, text)) }
    }

    /** Copies the offered clipboard text into the box, clears the banner and the old error. */
    fun pasteClipboard() {
        state.update {
            val offer = it.clipboard
            if (offer == null || it.checking) it else it.copy(text = offer.text, error = null, clipboard = null)
        }
    }

    /** Hides the banner without touching the box. */
    fun dismissClipboard() {
        state.update { it.copy(clipboard = null) }
    }

    fun dismiss() {
        job?.cancel()
        job = null
        offered = true
        state.value = ImportSheetUi()
    }
}
