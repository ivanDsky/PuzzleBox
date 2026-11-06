package com.ivandsky.puzzlebox.create

/** The Create root: the stored drafts, newest edit first. */
data class CreateUiState(val drafts: List<DraftRow> = emptyList()) {

    /** One draft's row: its [title] over "{Game} · {size}" [meta]. */
    data class DraftRow(val id: String, val title: String, val meta: String)
}
