package com.ivandsky.puzzlebox.app.shortcut

import com.ivandsky.puzzlebox.model.GameId

/** What a shortcut intent asks for: the next puzzle to play, one game, or the Today tab. */
sealed interface ShortcutTarget {

    /** Today's next puzzle, picked when the shortcut is tapped (see [ResolveShortcutLanding]). */
    data object UpNext : ShortcutTarget

    /** One game's board, whatever its status. */
    data class Game(val id: GameId) : ShortcutTarget

    /** The Today tab at its root, whatever Today's set holds. */
    data object Today : ShortcutTarget
}
