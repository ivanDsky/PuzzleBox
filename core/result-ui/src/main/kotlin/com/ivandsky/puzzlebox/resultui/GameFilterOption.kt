package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.FilterOption
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.gameui.GameBrand

private val IconSize = 24.dp

/** A row for [value] with the game's glyph at 24dp in its accent's `onContainer`. The row's own tint is not used: a game keeps its colour. */
fun <T> gameFilterOption(value: T, label: String, brand: GameBrand, tag: String? = null): FilterOption<T> =
    FilterOption(value, label, icon = { Icon(brand.glyph, contentDescription = null, modifier = Modifier.size(IconSize), tint = brand.accent.colors().onContainer) }, tag = tag)

/** A row for [value] with the bar mark of [level] at 24dp in the tint the row asks for. */
fun <T> levelFilterOption(value: T, label: String, level: Difficulty.Level, tag: String? = null): FilterOption<T> =
    FilterOption(value, label, icon = { tint -> DifficultyIcon(level, Modifier.size(IconSize), tint) }, tag = tag)
