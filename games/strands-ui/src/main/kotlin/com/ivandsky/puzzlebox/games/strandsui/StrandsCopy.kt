package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.games.strands.StrandsRating

/** Every string Strands shows (A3, A7, A10, A14), plain sentence case with no emoji (A42). */
internal object StrandsCopy {
    const val TITLE = "Strands"
    const val THEME_LABEL = "Today's theme"
    const val HINT = "Hint"

    const val EMPTY = "Enter a word to submit"
    const val TOO_SHORT = "Too short"
    const val TOO_LONG = "Too long"
    const val ALREADY_FOUND = "Already found"
    const val WORD_LIMIT = "Word limit reached"
    const val NOT_IN_WORD_LIST = "Not in word list"
    const val SPANGRAM = "Spangram!"

    // A7 verbatim; NYT's modal sets the first sentence as its title and the rest as its body.
    const val HINT_ON_BOARD_TITLE = "Your hint's on the board."
    const val HINT_ON_BOARD_BODY = "Use your current hint to create a word before getting a new hint."
    const val HINT_ON_BOARD = "$HINT_ON_BOARD_TITLE $HINT_ON_BOARD_BODY"
    const val HINT_NONE_AVAILABLE_TITLE = "Need a hint?"
    const val HINT_NONE_AVAILABLE_BODY =
        "Find non-theme words to get hints. For every 3 non-theme words you find, you earn a hint."
    const val HINT_NONE_AVAILABLE = "$HINT_NONE_AVAILABLE_TITLE $HINT_NONE_AVAILABLE_BODY"
    const val DISMISS = "Got it"

    // Chrome: rating word (A36), hint control state for TalkBack, the header line.
    const val RATING_PERFECT = "Perfect!"
    const val RATING_GREAT = "Great!"
    const val RATING_WELL_DONE = "Well done!"
    const val HINT_AVAILABLE = "available"

    fun hintProgress(thirds: Int): String = "$thirds of 3 words toward the next hint"

    /** The unavailable pill stays pressable (it opens "Need a hint?"), so TalkBack must say no hint is waiting. */
    fun hintUnavailable(thirds: Int): String = "No hint available, ${hintProgress(thirds)}"

    fun headerLine(puzzleNumber: Int?, credit: String): String =
        puzzleNumber?.let { "${number(it)} · $credit" } ?: credit

    /** The slot's live-region line (SC12): the verdict copy, then the counter. */
    fun announcement(verdict: String, count: Int, total: Int): String {
        val stop = if (verdict.lastOrNull() in SENTENCE_ENDS) " " else ". "
        return verdict + stop + found(count, total)
    }

    fun rating(rating: StrandsRating): String = when (rating) {
        StrandsRating.Perfect -> RATING_PERFECT
        StrandsRating.Great -> RATING_GREAT
        StrandsRating.WellDone -> RATING_WELL_DONE
    }

    private val SENTENCE_ENDS = setOf('.', '!', '?')

    const val TUTORIAL_TRACE = "Tap letters next to each other to spell a word, then tap the last letter again to submit. You can drag instead."
    const val TUTORIAL_SPANGRAM = "Theme words fill the board. The spangram touches two opposite sides."

    fun found(count: Int, total: Int): String = "$count of $total theme words found"

    fun number(puzzleNumber: Int): String = "No. $puzzleNumber"

    fun credit(constructors: String): String = "by $constructors"

    // Board (TalkBack, SC12): "<letter>[, selected], <state>".
    const val SELECTED = "selected"
    const val STATE_THEME = "theme"
    const val STATE_SPANGRAM = "spangram"
    const val STATE_HINTED = "hinted"
}
