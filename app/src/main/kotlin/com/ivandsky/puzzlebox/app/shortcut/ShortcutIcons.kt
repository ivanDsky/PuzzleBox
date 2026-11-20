package com.ivandsky.puzzlebox.app.shortcut

import androidx.annotation.DrawableRes
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.model.GameId

/** Launcher shortcut icons: a game's glyph on its accent disc, a play triangle for Up next, a plain dot for a game with none. */
object ShortcutIcons {
    private val byGame: Map<GameId, Int> = mapOf(
        GameId("zip") to R.drawable.ic_shortcut_zip,
        GameId("tango") to R.drawable.ic_shortcut_tango,
        GameId("queens") to R.drawable.ic_shortcut_queens,
        GameId("minisudoku") to R.drawable.ic_shortcut_minisudoku,
        GameId("strands") to R.drawable.ic_shortcut_strands,
    )

    @DrawableRes
    fun forGame(id: GameId): Int = byGame[id] ?: R.drawable.ic_shortcut_game

    @get:DrawableRes
    val upNext: Int = R.drawable.ic_shortcut_up_next
}
