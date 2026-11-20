package com.ivandsky.puzzlebox.app.shortcut

import com.ivandsky.puzzlebox.model.GameId

/** A listed game's id and display name, as the shortcut publisher is given it. */
data class ShortcutGame(val id: GameId, val displayName: String)
