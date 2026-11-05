package com.ivandsky.puzzlebox.games.strandsui

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

    const val HINT_ON_BOARD = "Your hint's on the board. Use your current hint to create a word before getting a new hint."
    const val HINT_NONE_AVAILABLE =
        "Need a hint? Find non-theme words to get hints. For every 3 non-theme words you find, you earn a hint."
    const val DISMISS = "Got it"

    const val TUTORIAL_TRACE = "Tap letters next to each other to spell a word, then tap the last letter again to submit. You can drag instead."
    const val TUTORIAL_SPANGRAM = "Theme words fill the board. The spangram touches two opposite sides."

    fun found(count: Int, total: Int): String = "$count of $total theme words found"

    fun number(puzzleNumber: Int): String = "No. $puzzleNumber"

    fun credit(constructors: String): String = "by $constructors"
}
