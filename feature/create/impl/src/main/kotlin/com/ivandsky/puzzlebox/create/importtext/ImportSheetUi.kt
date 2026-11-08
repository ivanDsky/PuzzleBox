package com.ivandsky.puzzlebox.create.importtext

import com.ivandsky.puzzlebox.domain.ImportResult

/**
 * The Import from text sheet's state (spec A23): whether it shows, the box's text, whether an
 * import is running, the last failure, and the clipboard's notation offered as a banner.
 * [error] is never `ImportResult.Imported`.
 */
data class ImportSheetUi(
    val visible: Boolean = false,
    val text: String = "",
    val checking: Boolean = false,
    /** Never ImportResult.Imported. */
    val error: ImportResult? = null,
    /** The clipboard's text when it starts with a notation header; null shows no banner. */
    val clipboard: ClipboardNotation? = null,
) {
    /** [lines] lines of [gameName] puzzle [text], offered to paste. */
    data class ClipboardNotation(val gameName: String, val lines: Int, val text: String)
}
