package com.ivandsky.puzzlebox.games.tangoui.editor

import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraftAnalyzer

/**
 * The editor's words: the tool hint, the tap-zones label and one fix-list sentence per
 * reason and block id. A new id adds one entry to [fixes]; TangoCopyCoverageTest fails while
 * it is missing.
 */
internal object TangoEditorCopy {

    const val TOOL_HINT: String =
        "Press a square or an edge to preview it, lift to set it. Slide away to cancel."

    const val TAP_ZONES: String = "Tap zones"

    /** The forced-cells card heading. */
    const val FORCED_CELLS: String = "Forced cells"

    private val fixes: Map<String, String> = mapOf(
        TangoDraftAnalyzer.EMPTY to "Add a sun, a moon or an edge mark to start",
        PublishBlock.NEEDS_GUESSING to "Logic alone can't finish it — players would have to guess",
    )

    /** The fix-list sentence for a reason or block [id]; null when it has no copy. */
    fun fix(id: String): String? = fixes[id]
}
