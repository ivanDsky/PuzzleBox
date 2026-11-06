package com.ivandsky.puzzlebox.games.strands.feed.gate

import com.ivandsky.puzzlebox.games.strands.feed.StrandsWire

/** Every field the puzzle is built from is present; the one check that turns a wire into a [CompleteWire]. */
object FieldsCheck {

    sealed interface Outcome {
        data class Complete(val wire: CompleteWire) : Outcome
        data class Missing(val field: String) : Outcome
    }

    fun check(wire: StrandsWire): Outcome {
        val printDate = wire.printDate ?: return Outcome.Missing("printDate")
        wire.clue ?: return Outcome.Missing("clue")
        wire.constructors ?: return Outcome.Missing("constructors")
        val spangram = wire.spangram ?: return Outcome.Missing("spangram")
        val themeWords = wire.themeWords ?: return Outcome.Missing("themeWords")
        val board = wire.startingBoard ?: return Outcome.Missing("startingBoard")
        wire.solutions ?: return Outcome.Missing("solutions")
        val themeCoords = wire.themeCoords ?: return Outcome.Missing("themeCoords")
        val spangramCoords = wire.spangramCoords ?: return Outcome.Missing("spangramCoords")
        return Outcome.Complete(CompleteWire(printDate, board, spangram, spangramCoords, themeWords, themeCoords))
    }
}
