package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.model.GameId

/**
 * The one filled action [ResultScreen] offers (spec A7, SC10). Today's result offers
 * [Next] (the next unsolved daily, named) or, when every daily is solved, [AllDone];
 * a replay's result offers [Done].
 */
sealed interface ResultPrimaryAction {

    /** "Next: [name]": open [gameId]'s today. */
    data class Next(val gameId: GameId, val name: String) : ResultPrimaryAction

    /** Every daily of today is solved: "All done for today" with Done. */
    data object AllDone : ResultPrimaryAction

    /** A replay's result: Done returns to the origin. */
    data object Done : ResultPrimaryAction
}
