package com.ivandsky.puzzlebox.games.strands.engine

import com.ivandsky.puzzlebox.game.MoveVerdict
import com.ivandsky.puzzlebox.games.strands.StrandsWord

/** What a submitted trace spelled, and whether it counts. */
sealed interface StrandsVerdict : MoveVerdict {
    data object Empty : StrandsVerdict
    data object TooShort : StrandsVerdict
    data object TooLong : StrandsVerdict
    data object AlreadyFound : StrandsVerdict
    data object WordLimitReached : StrandsVerdict
    data object NotInWordList : StrandsVerdict

    /** Off-board, reused cell or non-adjacent step. An engine guard: the trace input never produces it. */
    data object InvalidPath : StrandsVerdict

    /** A [com.ivandsky.puzzlebox.game.Move] of another game. */
    data object Foreign : StrandsVerdict

    data class ThemeWord(val word: StrandsWord) : StrandsVerdict
    data class Spangram(val word: StrandsWord) : StrandsVerdict
    data class NonTheme(val word: String) : StrandsVerdict
}
