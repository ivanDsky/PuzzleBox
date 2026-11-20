package com.ivandsky.puzzlebox.train.drill

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.Restored
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.model.GameId

/**
 * A play UI with no board, for the drill view model, which only creates a controller, adopts a puzzle on it and reads
 * its [PlayEnvironment]. [controllers] are the ones created, in order; [rejectAdopt] makes every later one refuse its
 * puzzle with that message.
 */
internal class FakeGamePlayUi(override val gameId: GameId) : GamePlayUi {

    val controllers = mutableListOf<FakeGamePlayController>()
    var rejectAdopt: String? = null
    override val tutorial: List<TutorialStep> = emptyList()

    override fun createController(env: PlayEnvironment): GamePlayController =
        FakeGamePlayController(env, rejectAdopt).also { controllers += it }

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) = Unit

    @Composable
    override fun Screen(
        controller: GamePlayController,
        status: PlayBoardStatus,
        layout: PlayLayout,
        sidePanel: (@Composable ColumnScope.() -> Unit)?,
        onRetry: () -> Unit,
        onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
        modifier: Modifier,
    ) = Unit
}

/** A controller that records what it adopted and, on [win] or [change], calls back through [env] as a game does. */
internal class FakeGamePlayController(val env: PlayEnvironment, private val reject: String?) : GamePlayController {

    val adopted = mutableListOf<Pair<Puzzle, PuzzleRef>>()
    override var isComplete = false
        private set

    override fun adoptToday(puzzle: Puzzle, ref: PuzzleRef): Adopt {
        adopted += puzzle to ref
        return reject?.let(Adopt::Rejected) ?: Adopt.Adopted
    }

    override fun adoptRestored(bytes: ByteArray): Restored? = null

    override fun playAgain() = Unit

    /** A committed move: the session blob goes to the environment. */
    fun change() = env.onSessionChanged(byteArrayOf(1))

    /** The win transition: a completion record for the adopted identity. */
    fun win() {
        isComplete = true
        change()
        env.onWon(CompletionRecord(adopted.last().second, 42_000L, 0, RecordStats(1, "{}")))
    }
}
