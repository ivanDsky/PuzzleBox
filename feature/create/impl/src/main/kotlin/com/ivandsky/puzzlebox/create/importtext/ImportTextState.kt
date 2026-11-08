package com.ivandsky.puzzlebox.create.importtext

import com.ivandsky.puzzlebox.domain.ImportDraftFromText
import com.ivandsky.puzzlebox.domain.ImportResult
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
 * draft's id once, after the sheet has hidden.
 */
class ImportTextState(
    private val importDraft: ImportDraftFromText,
    private val scope: CoroutineScope,
    private val onImported: (draftId: String) -> Unit,
) {
    private val state = MutableStateFlow(ImportSheetUi())
    private var job: Job? = null

    val ui: StateFlow<ImportSheetUi> = state.asStateFlow()

    fun open() {
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

    fun dismiss() {
        job?.cancel()
        job = null
        state.value = ImportSheetUi()
    }
}
