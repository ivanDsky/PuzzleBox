package com.ivandsky.puzzlebox.play.community.shelves

import com.ivandsky.puzzlebox.game.editor.Difficulty

/** What a shelf card reads from a puzzle besides what the plan holds: its [title], its [author] and its [difficulty] level, if it has one. */
internal data class ShelfPuzzleModel(val title: String, val author: String, val difficulty: Difficulty.Level?)
