package com.ivandsky.puzzlebox.create.importtext

import com.ivandsky.puzzlebox.domain.ImportResult

/**
 * The Import from text sheet's state (spec A23): whether it shows, the box's text, whether an
 * import is running, and the last failure. [error] is never `ImportResult.Imported`.
 */
data class ImportSheetUi(
    val visible: Boolean = false,
    val text: String = "",
    val checking: Boolean = false,
    /** Never ImportResult.Imported. */
    val error: ImportResult? = null,
)
