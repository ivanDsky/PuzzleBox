package com.ivandsky.puzzlebox.train

import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.train.practice.BoardHold
import com.ivandsky.puzzlebox.train.practice.PracticeChapter
import com.ivandsky.puzzlebox.train.practice.PracticePane
import com.ivandsky.puzzlebox.train.practice.PracticeState
import com.ivandsky.puzzlebox.train.practice.StandInGrid
import com.ivandsky.puzzlebox.train.practice.standInChapter

internal val GoldenProgressThree = TechniqueProgress(TechniqueProgress.Mark.Practicing, inRow = 3, answeredAtMs = 1L)
internal val GoldenProgressTwo = TechniqueProgress(TechniqueProgress.Mark.Practicing, inRow = 2, answeredAtMs = 1L)

/** The practice pane on a stand-in board, in [state] (the pane the practice and result goldens share). */
@Composable
internal fun GoldenPractice(
    state: PracticeState,
    showBack: Boolean,
    progress: TechniqueProgress = GoldenProgressTwo,
    hold: BoardHold? = null,
    chapter: PracticeChapter = standInChapter(board = 2, boards = 5),
) = PracticePane(
    chapter = chapter,
    state = state,
    progress = progress,
    showBack = showBack,
    onClose = {},
    board = { _, modifier -> StandInGrid(modifier) },
    onTryAgain = {},
    onTryAnother = {},
    hold = hold,
    onSkip = {},
    onNextChapter = {},
    onPracticeAgain = {},
)
