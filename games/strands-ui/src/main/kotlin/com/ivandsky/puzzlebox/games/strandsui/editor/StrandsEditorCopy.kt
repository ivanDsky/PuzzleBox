package com.ivandsky.puzzlebox.games.strandsui.editor

import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftAnalyzer
import com.ivandsky.puzzlebox.games.strands.editor.StrandsPublishGates

/**
 * Fix-list copy for the Strands reason and block ids. Adding an id to
 * [StrandsDraftAnalyzer.reasonIds] or [StrandsPublishGates.blockIds] needs a line here, or for
 * a block the publish fields fix, a readiness phrase like [CLUE_READINESS];
 * StrandsCopyCoverageTest fails while it is missing.
 */
internal object StrandsEditorCopy {

    /** The word field's label and resting placeholder, so the two boxes tell themselves apart. */
    const val WORD_FIELD = "Word for this path…"

    /** Under the off word field: typing needs a selected path, so there is nothing to wait for. */
    const val TRACE_FIRST = "Trace a word first"

    /**
     * The word field's warning line for text the dictionary doesn't have and can't grow into: it
     * explains the amber border and says the text is still allowed, since theme words and the
     * spangram may be any letters.
     */
    const val NOT_IN_DICTIONARY = "Not in the dictionary, but you can still use it"

    /** Under the word field when [letters] outrun the selected path's [cells]: the extra ones are kept, not written. */
    fun overflow(letters: Int, cells: Int): String {
        val extra = letters - cells
        return "$letters letters, path has $cells: the extra $extra ${if (extra == 1) "isn't" else "aren't"} written"
    }

    /** The clue field's label, so the two boxes tell themselves apart. */
    const val CLUE_FIELD = "Theme clue"

    /** Under a blank clue field: no error yet, but publishing waits for it. */
    const val CLUE_NEEDED = "Needed to publish"

    /** Under a clue past its cap. */
    const val CLUE_TOO_LONG = "Up to ${StrandsPublishGates.MAX_CLUE} characters"

    /** The readiness line's phrase for [StrandsPublishGates.CLUE_LENGTH]: a field to fill, not the board. */
    const val CLUE_READINESS = "Add a theme clue"

    /**
     * The row for [id] with its [count] and the [word] a word block is about; null for an id
     * Strands has no copy for.
     */
    fun fix(id: String, count: Int, word: String): String? = when (id) {
        StrandsDraftAnalyzer.BLANK_CELLS -> if (count == 1) "1 cell has no letter yet" else "$count cells have no letter yet"
        StrandsDraftAnalyzer.UNNAMED -> if (count == 1) "1 path still needs its word" else "$count paths still need their words"
        StrandsDraftAnalyzer.NO_SPANGRAM -> "Mark one word as the spangram"
        StrandsPublishGates.WORD_SHAPE -> "$word needs 4 to 24 letters, A to Z only"
        PublishBlock.BLOCKLISTED -> "$word can't be used"
        StrandsPublishGates.DUPLICATE_WORD -> "$word is in the puzzle twice"
        StrandsPublishGates.SPANGRAM_EDGES -> "The spangram must touch two opposite edges"
        else -> null
    }
}
