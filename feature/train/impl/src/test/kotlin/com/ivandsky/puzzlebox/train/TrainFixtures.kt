package com.ivandsky.puzzlebox.train

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.DrillSource
import com.ivandsky.puzzlebox.gameui.trainer.PracticeBoard
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.train.practice.StandInGrid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.CoroutineContext

/**
 * A practice board a test drives from outside: [say] sets the status the band shows, and [turn] is the game reporting a
 * trained turn, as the real board does when a move is judged. [showMeCount] counts the Show me taps.
 */
internal class FakePracticeBoard(
    override val technique: TechniqueId,
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

/**
 * A trainer with the techniques [techniques]. [boards] holds every practice board the game cut, in order, so a test can
 * drive the one on screen; a puzzle whose last payload byte is in [silent] yields none, a puzzle where the technique is
 * never the easiest move. [drillSources] are what the game names to drill.
 */
internal class FakeTrainerGame(
    override val gameId: GameId,
    private val techniques: List<TrainerTechnique>,
    override val drillSources: List<DrillSource> = emptyList(),
) : TrainerGame {

    val boards = mutableListOf<FakePracticeBoard>()
    val silent = mutableSetOf<Byte>()

    override fun catalog(): List<TrainerTechnique> = techniques

    override fun practice(
        puzzle: Puzzle,
        technique: TechniqueId,
        scope: CoroutineScope,
        onTrainedTurn: (TrainerVerdict) -> Unit,
    ): PracticeBoard? {
        if (puzzle.payload.last() in silent) return null
        return FakePracticeBoard(technique, puzzle, scope, onTrainedTurn).also { boards += it }
    }

    @Composable
    override fun Board(board: PracticeBoard, modifier: Modifier) = StandInGrid(modifier)
}

/** Techniques named after their ids, for a catalog of [ids]. */
internal fun techniques(vararg ids: String): List<TrainerTechnique> =
    ids.map { TrainerTechnique(TechniqueId(it), it.replaceFirstChar(Char::uppercase), "The line of $it.") }

/** Every dispatcher runs its work at once on the caller's thread, so a test sees each step's result when the call returns. */
internal object InlineDispatcherProvider : DispatcherProvider {
    private val inline = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext) = false
        override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
    }
    override val main: CoroutineDispatcher = inline
    override val io: CoroutineDispatcher = inline
    override val default: CoroutineDispatcher = inline
}
