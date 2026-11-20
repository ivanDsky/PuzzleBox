package com.ivandsky.puzzlebox.app.widget

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.model.GameId

/**
 * The home widget's per-game art: a game's glyph and ring colors, and a plain dot on the neutral colors for a
 * game with none. A future game needs one glyph drawable and one slot here.
 */
object WidgetArt {
    private class Slot(@DrawableRes val glyph: Int, @ColorRes val accent: Int, @ColorRes val onAccent: Int)

    private val byGame: Map<GameId, Slot> = mapOf(
        GameId("zip") to Slot(R.drawable.widget_glyph_zip, R.color.widget_accent_blue, R.color.widget_on_accent_blue),
        GameId("tango") to Slot(R.drawable.widget_glyph_tango, R.color.widget_accent_orange, R.color.widget_on_accent_orange),
        GameId("queens") to Slot(R.drawable.widget_glyph_queens, R.color.widget_accent_violet, R.color.widget_on_accent_violet),
        GameId("minisudoku") to Slot(R.drawable.widget_glyph_minisudoku, R.color.widget_accent_teal, R.color.widget_on_accent_teal),
        GameId("strands") to Slot(R.drawable.widget_glyph_strands, R.color.widget_accent_pink, R.color.widget_on_accent_pink),
    )

    private val fallback = Slot(R.drawable.widget_glyph_dot, R.color.widget_accent_neutral, R.color.widget_on_accent_neutral)

    @DrawableRes
    fun glyph(id: GameId): Int = (byGame[id] ?: fallback).glyph

    @ColorRes
    fun accent(id: GameId): Int = (byGame[id] ?: fallback).accent

    @ColorRes
    fun onAccent(id: GameId): Int = (byGame[id] ?: fallback).onAccent
}
