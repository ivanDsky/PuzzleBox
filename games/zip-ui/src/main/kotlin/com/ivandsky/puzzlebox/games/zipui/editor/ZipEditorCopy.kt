package com.ivandsky.puzzlebox.games.zipui.editor

import com.ivandsky.puzzlebox.games.zip.editor.ZipDraftAnalyzer

/**
 * The editor's fix-list words: one sentence per Incomplete reason id. A new id adds one entry
 * to [fixes]; ZipCopyCoverageTest fails while it is missing.
 */
internal object ZipEditorCopy {

    private val fixes: Map<String, String> = mapOf(
        ZipDraftAnalyzer.UNCOVERED to "Extend the path until it covers every cell",
        ZipDraftAnalyzer.WAYPOINTS to "Place at least two numbered waypoints, or draw the path",
    )

    /** The fix-list sentence for a reason [id]; null when it has no copy. */
    fun fix(id: String): String? = fixes[id]
}
