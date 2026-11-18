package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.graphics.vector.ImageVector
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.game.editor.Difficulty

/** The level's bar mark: Easy fills one of four bars, Expert all four. */
val Difficulty.Level.icon: ImageVector
    get() = when (this) {
        Difficulty.Level.Easy -> PuzzleIcons.LevelEasy
        Difficulty.Level.Medium -> PuzzleIcons.LevelMedium
        Difficulty.Level.Hard -> PuzzleIcons.LevelHard
        Difficulty.Level.Expert -> PuzzleIcons.LevelExpert
    }
