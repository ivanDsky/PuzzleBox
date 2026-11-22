package com.ivandsky.puzzlebox.train

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.PracticeBoard
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A practice board a test drives from outside: [say] sets the status the band shows, and [turn] is the game reporting a
 * trained turn, as the real board does when a move is judged. [showMeCount] counts the Show me taps.
 */
internal class FakePracticeBoard(
    override val technique: TechniqueId,
    val kind: BoardKind,
    val puzzle: Puzzle,
    val scope: CoroutineScope,
    private val onTrainedTurn: (TrainerVerdict) -> Unit,
    first: PracticeStatus = PracticeStatus.FindNextMove,
) : PracticeBoard {

    private val mutableStatus = MutableStateFlow(first)
    override val status: StateFlow<PracticeStatus> = mutableStatus

    var showMeCount = 0
        private set

    override fun showMe() {
        showMeCount += 1
    }

    var gotItCount = 0
        private set

    override fun gotIt() {
        gotItCount += 1
    }

    fun say(status: PracticeStatus) {
        mutableStatus.value = status
    }

    fun turn(verdict: TrainerVerdict) = onTrainedTurn(verdict)
}
