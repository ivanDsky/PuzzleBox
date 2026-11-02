package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.model.GameId

/**
 * The games the play flow can drive (spec A14: the game picker; no new navigation
 * pattern — the picker is the play tab's fresh-entry state). A [PlayGame] is one
 * play-state holder + one session codec; the plugin list only feeds display names.
 */
enum class PlayGame(val id: GameId) {
    Zip(ZipDefinition.id),
    Tango(TangoDefinition.id);

    companion object {
        fun byId(id: GameId): PlayGame? = entries.firstOrNull { it.id == id }
    }
}
