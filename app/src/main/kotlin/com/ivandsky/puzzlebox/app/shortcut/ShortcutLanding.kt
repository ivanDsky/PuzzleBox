package com.ivandsky.puzzlebox.app.shortcut

import com.ivandsky.puzzlebox.model.GameId

/** Where a resolved shortcut puts the player. */
sealed interface ShortcutLanding {

    /** The Today tab. */
    data object Today : ShortcutLanding

    /** [game]'s board. */
    data class Board(val game: GameId) : ShortcutLanding
}
