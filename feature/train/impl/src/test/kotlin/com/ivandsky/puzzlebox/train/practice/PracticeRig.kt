package com.ivandsky.puzzlebox.train.practice

import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.data.trainer.TrainerPuzzleSource
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate
import com.ivandsky.puzzlebox.gameui.trainer.DrillSource
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeTrainerProgressRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import com.ivandsky.puzzlebox.train.FakePracticeBoard
import com.ivandsky.puzzlebox.train.FakeTrainerArchive
import com.ivandsky.puzzlebox.train.FakeTrainerGame
import com.ivandsky.puzzlebox.train.InlineDispatcherProvider
import com.ivandsky.puzzlebox.train.domain.RecordAnswer
import com.ivandsky.puzzlebox.train.techniques
import kotlinx.coroutines.flow.flow

/**
 * A practice view model with its fakes. [candidates] are the archive numbers the technique's drill sources name,
 * [device] the puzzles on the device (newest first; a fetched puzzle is added), [attempt] the attempt it is started with,
 * or none. A puzzle's tag, the last byte of its payload, is its archive number for a fetched one. The game's [catalog]
 * is two techniques by default and [opens] the second, so the chapter has the four focus boards and a review; opening
 * the first has the focus boards only.
 */
internal class PracticeRig(
    candidates: List<DrillCandidate>,
    device: List<Puzzle> = emptyList(),
    attempt: Int? = 1,
    val progress: FakeTrainerProgressRepository = FakeTrainerProgressRepository(),
    catalog: List<String> = listOf("naked-single", "line-lock"),
    opens: TechniqueId = LINE_LOCK,
    screenReaderOn: Boolean = false,
) {
    val game = FakeTrainerGame(QUEENS, techniques(*catalog.toTypedArray()), drillSources = listOf(DrillSource { candidates }))
    val archive = FakeTrainerArchive()
    val onDevice = device.toMutableList()
    val vm: PracticeViewModel
    val screenReader = FakeScreenReaderCheck(screenReaderOn)

    init {
        archive.onLoaded = { onDevice += it }
        val source = TrainerPuzzleSource { flow { onDevice.toList().forEach { emit(it) } } }
        vm = PracticeViewModel(game, opens, source, archive, progress, RecordAnswer(progress), InlineDispatcherProvider, screenReader)
        attempt?.let(vm::start)
    }

    val state: PracticeState get() = vm.state.value

    /** The board on screen. */
    val board: FakePracticeBoard get() = (state as PracticeState.Practicing).board as FakePracticeBoard

    /** What the trainer's store holds for the technique, or nothing if it was never written. */
    val stored: TechniqueProgress? get() = progress.stored[QUEENS]?.get(LINE_LOCK)

    companion object {
        val QUEENS = GameId("queens")
        val LINE_LOCK = TechniqueId("line-lock")
        val NAKED_SINGLE = TechniqueId("naked-single")

        /** Archive numbers 10, 20 and 30 with the steps that order them 20, 30, 10. */
        val named = listOf(
            DrillCandidate.Archive(number = 10, targetSteps = 5),
            DrillCandidate.Archive(number = 20, targetSteps = 9),
            DrillCandidate.Archive(number = 30, targetSteps = 7),
        )

        fun puzzle(tag: Int): Puzzle = TestGames.puzzle(QUEENS, tag.toByte())
    }
}

/** The tag of the puzzle the board was cut from. */
internal val FakePracticeBoard.tag: Int get() = puzzle.payload.last().toInt()

/** The board on screen ends ([PracticeStatus.Done]) and its hold passes, so the next board has opened when this returns. */
internal fun PracticeRig.finishBoard(main: MainDispatcherRule, last: Spotlight? = null) {
    board.say(PracticeStatus.Done(last))
    main.testDispatcher.scheduler.advanceTimeBy(BoardEndHoldMillis)
    main.testDispatcher.scheduler.runCurrent()
}

/** A screen reader that is on or off as the test sets it. */
internal class FakeScreenReaderCheck(var enabled: Boolean = false) : ScreenReaderCheck {
    override fun on() = enabled
}
