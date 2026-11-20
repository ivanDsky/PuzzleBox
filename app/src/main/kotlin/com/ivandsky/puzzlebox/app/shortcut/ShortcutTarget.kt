package com.ivandsky.puzzlebox.app.shortcut

import com.ivandsky.puzzlebox.model.GameId

/** What a launcher shortcut asks for: the next puzzle to play, or one game. */
sealed interface ShortcutTarget {

    /** Today's next puzzle, picked when the shortcut is tapped (see [ResolveShortcutLanding]). */
    data object UpNext : ShortcutTarget

    /** One game's board, whatever its status. */
    data class Game(val id: GameId) : ShortcutTarget
}
