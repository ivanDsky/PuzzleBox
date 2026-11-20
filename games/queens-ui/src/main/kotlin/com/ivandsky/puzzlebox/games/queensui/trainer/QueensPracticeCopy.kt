package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensRung
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensPracticeVerdict

/** The practice board's own sentences for a move it undid: one plain line why, as the play board's rule line speaks. */
internal object QueensPracticeCopy {

    fun why(why: QueensPracticeVerdict.Wrong.Why): String = when (why) {
        QueensPracticeVerdict.Wrong.Why.ATTACKED -> "Queens can't touch or share a row, column, or color."
        QueensPracticeVerdict.Wrong.Why.OFF_SOLUTION -> "A queen there can't be part of the answer."
        QueensPracticeVerdict.Wrong.Why.ON_SOLUTION -> "That square has to hold a queen."
    }

    /** The catalog's name for [rung], for the move that was undone because that technique applied. */
    fun technique(rung: QueensRung): String = rung.trainerId?.let(QueensTrainerCatalog::name) ?: rung.id
}
