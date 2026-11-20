package com.ivandsky.puzzlebox.app.widget

import com.ivandsky.puzzlebox.model.GameId

/** What the widget's Up next button names. */
sealed interface WidgetUpNext {

    /** The next puzzle to play. */
    data class Play(val game: GameId, val gameName: String) : WidgetUpNext

    /** Every game is solved. */
    data object Done : WidgetUpNext

    /** Today's set is empty: nothing to name yet. */
    data object Unknown : WidgetUpNext
}
