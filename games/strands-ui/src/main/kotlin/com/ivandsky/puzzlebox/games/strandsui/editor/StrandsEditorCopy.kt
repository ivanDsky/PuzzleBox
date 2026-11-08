package com.ivandsky.puzzlebox.games.strandsui.editor

import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftAnalyzer
import com.ivandsky.puzzlebox.games.strands.editor.StrandsPublishGates

/**
 * Fix-list copy for the Strands reason and block ids. Adding an id to
 * [StrandsDraftAnalyzer.reasonIds] or [StrandsPublishGates.blockIds] needs a line here;
 * StrandsCopyCoverageTest fails while it is missing.
 */
internal object StrandsEditorCopy {

    /**
     * The row for [id] with its [count] and the [word] a word block is about; null for an id
     * Strands has no copy for.
     */
    fun fix(id: String, count: Int, word: String): String? = when (id) {
        StrandsDraftAnalyzer.BLANK_CELLS -> if (count == 1) "1 cell has no letter yet" else "$count cells have no letter yet"
        StrandsDraftAnalyzer.UNNAMED -> if (count == 1) "1 path still needs its word" else "$count paths still need their words"
        StrandsDraftAnalyzer.NO_SPANGRAM -> "Mark one word as the spangram"
        StrandsPublishGates.CLUE_LENGTH -> "The theme clue needs 1 to 40 characters"
        StrandsPublishGates.WORD_SHAPE -> "$word needs 4 to 24 letters, A to Z only"
        PublishBlock.BLOCKLISTED -> "$word can't be used"
        StrandsPublishGates.DUPLICATE_WORD -> "$word is in the puzzle twice"
        else -> null
    }
}
