package com.ivandsky.puzzlebox.train

import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.train.practice.BoardHold
import com.ivandsky.puzzlebox.train.practice.PracticeState
import com.ivandsky.puzzlebox.train.practice.StandInPracticeBoard
import com.ivandsky.puzzlebox.train.practice.standInChapter
import com.ivandsky.puzzlebox.train.practice.standInSpotlight
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class TrainPhonePracticeGoldenScreenshots : TrainGoldenScreenshots("phone") {
    @Test fun `practice find next move light`() = capture("practice-find", dark = false) { GoldenPage { GoldenPractice(FindNext, showBack = true) } }
    @Test fun `practice find next move dark`() = capture("practice-find", dark = true) { GoldenPage { GoldenPractice(FindNext, showBack = true) } }

    @Test fun `practice right light`() = capture("practice-right", dark = false) { GoldenPage { GoldenPractice(Right, showBack = true, GoldenProgressThree) } }
    @Test fun `practice right dark`() = capture("practice-right", dark = true) { GoldenPage { GoldenPractice(Right, showBack = true, GoldenProgressThree) } }

    @Test fun `practice wrong light`() = capture("practice-wrong", dark = false) { GoldenPage { GoldenPractice(Wrong, showBack = true) } }
    @Test fun `practice wrong dark`() = capture("practice-wrong", dark = true) { GoldenPage { GoldenPractice(Wrong, showBack = true) } }

    @Test fun `practice done light`() = capture("practice-done", dark = false) { GoldenPage { GoldenPractice(Done, showBack = true, GoldenProgressThree, MidHold) } }
    @Test fun `practice done dark`() = capture("practice-done", dark = true) { GoldenPage { GoldenPractice(Done, showBack = true, GoldenProgressThree, MidHold) } }
    @Test fun `practice done unsolved light`() = capture("practice-done-unsolved", dark = false) { GoldenPage { GoldenPractice(DoneUnsolved, showBack = true, GoldenProgressThree, MidHold) } }
    @Test fun `practice keep going light`() = capture("practice-keep-going", dark = false) { GoldenPage { GoldenPractice(KeepGoing, showBack = true) } }
    @Test fun `practice easier light`() = capture("practice-easier", dark = false) { GoldenPage { GoldenPractice(Easier, showBack = true) } }
    @Test fun `practice not yet light`() = capture("practice-not-yet", dark = false) { GoldenPage { GoldenPractice(NotYet, showBack = true) } }
    @Test fun `practice fits light`() = capture("practice-fits", dark = false) { GoldenPage { GoldenPractice(Fits, showBack = true, GoldenProgressThree) } }
    @Test fun `practice review board light`() = capture("practice-review", dark = false) { GoldenPage { GoldenPractice(FindNext, showBack = true, chapter = standInChapter(board = 5)) } }
    @Test fun `empty light`() = capture("empty", dark = false) { GoldenPage { GoldenPractice(PracticeState.Empty, showBack = true) } }
    @Test fun `needs connection light`() = capture("needs-connection", dark = false) { GoldenPage { GoldenPractice(PracticeState.NeedsConnection, showBack = true) } }
    @Test fun `cant load light`() = capture("cant-load", dark = false) { GoldenPage { GoldenPractice(PracticeState.CantLoad(alternative = 918), showBack = true) } }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class TrainTabletPracticeGoldenScreenshots : TrainGoldenScreenshots("tablet") {
    @Test fun `practice find next move light`() = capture("practice-find", dark = false) { GoldenBeside(open = true) { GoldenPractice(FindNext, showBack = false) } }
    @Test fun `practice find next move dark`() = capture("practice-find", dark = true) { GoldenBeside(open = true) { GoldenPractice(FindNext, showBack = false) } }

    @Test fun `practice right light`() = capture("practice-right", dark = false) { GoldenBeside(open = true) { GoldenPractice(Right, showBack = false, GoldenProgressThree) } }
    @Test fun `practice right dark`() = capture("practice-right", dark = true) { GoldenBeside(open = true) { GoldenPractice(Right, showBack = false, GoldenProgressThree) } }

    @Test fun `practice done light`() = capture("practice-done", dark = false) { GoldenBeside(open = true) { GoldenPractice(Done, showBack = false, GoldenProgressThree, MidHold) } }
}

private val FindNext = PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.FindNextMove))
private val Right = PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.Right(standInSpotlight(), trained = true)))
private val Wrong = PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.Wrong("Two queens would touch.")))
private val Done = PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.Done(standInSpotlight())))
private val DoneUnsolved = PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.Done(standInSpotlight(), needsOtherTechnique = true)))
private val KeepGoing = PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.KeepGoing(left = 2, trained = true)))
private val Easier = PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.Easier("Naked single", standInSpotlight())))
private val NotYet = PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.Easier("Line lock", standInSpotlight())))
private val Fits = PracticeState.Practicing(StandInPracticeBoard(PracticeStatus.Right(standInSpotlight(), trained = false, technique = "Naked single")))
private val MidHold = BoardHold(remainingMs = 900, waitsForTap = false)
