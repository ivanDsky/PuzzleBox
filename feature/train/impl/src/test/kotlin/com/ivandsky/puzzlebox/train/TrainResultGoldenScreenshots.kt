package com.ivandsky.puzzlebox.train

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique
import com.ivandsky.puzzlebox.train.practice.BoardRow
import com.ivandsky.puzzlebox.train.practice.BoardRowKind
import com.ivandsky.puzzlebox.train.practice.ChapterOutcome
import com.ivandsky.puzzlebox.train.practice.ChapterResult
import com.ivandsky.puzzlebox.train.practice.PracticeState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class TrainPhoneResultGoldenScreenshots : TrainGoldenScreenshots("phone") {
    @Test fun `chapter result light`() = capture("chapter-result", dark = false) { GoldenPage { GoldenPractice(Result, showBack = true) } }
    @Test fun `chapter result dark`() = capture("chapter-result", dark = true) { GoldenPage { GoldenPractice(Result, showBack = true) } }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class TrainTabletResultGoldenScreenshots : TrainGoldenScreenshots("tablet") {
    @Test fun `chapter result light`() = capture("chapter-result", dark = false) { GoldenBeside(open = true) { GoldenPractice(Result, showBack = false) } }
    @Test fun `chapter result dark`() = capture("chapter-result", dark = true) { GoldenBeside(open = true) { GoldenPractice(Result, showBack = false) } }
}

private val Result = PracticeState.Result(
    ChapterResult(
        technique = TrainerTechnique(TechniqueId("line-lock"), "Line lock", "A region sits inside one line."),
        next = TrainerTechnique(TechniqueId("pointing-pair"), "Pointing pair", "A pair points."),
        outcome = ChapterOutcome.NewlyMastered,
        boards = 5,
        boardsDone = 5,
        rights = 14,
        shown = 1,
        slips = 1,
        undone = 3,
        best = 9,
        rows = listOf(
            BoardRow(1, BoardRowKind.Clean, 3),
            BoardRow(2, BoardRowKind.Undone, 3, undone = 3),
            BoardRow(3, BoardRowKind.Clean, 2),
            BoardRow(4, BoardRowKind.Shown, 3),
            BoardRow(5, BoardRowKind.Slip, 3),
        ),
        allMastered = false,
        techniques = 4,
    ),
)
