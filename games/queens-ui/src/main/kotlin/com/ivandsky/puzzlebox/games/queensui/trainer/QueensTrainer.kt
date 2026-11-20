package com.ivandsky.puzzlebox.games.queensui.trainer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.DrillSource
import com.ivandsky.puzzlebox.gameui.trainer.PracticeBoard
import com.ivandsky.puzzlebox.gameui.trainer.PracticeSpotlight
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensPractice
import com.ivandsky.puzzlebox.games.queensui.QueensPlayBoard
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * The Queens [TrainerGame]: the Compose half of [QueensPractice] and the Queens technique walker. The technique list
 * shares its sentences with the hints ([QueensTrainerCatalog]), a practice board is the real play board over a board
 * the walk reached ([QueensPracticeBoard]), and the Spotlight over it is the hint's own.
 *
 * [com.ivandsky.puzzlebox.games.queensui.queensUiModule] binds it while TrainQueens is on. [compute] is the dispatcher
 * the play board's hint stepper is given; a practice board has no hint button, so it never runs on it.
 */
internal class QueensTrainer(private val compute: CoroutineDispatcher) : TrainerGame {

    override val gameId: GameId = QueensDefinition.id

    override fun catalog(): List<TrainerTechnique> = QueensTrainerCatalog.techniques

    override val drillSources: List<DrillSource> = listOf(QueensIndexDrillSource(), QueensGeneratedDrillSource())

    /** Null for another game's puzzle, one that does not decode, one without a true solution, and a technique it never uses. */
    override fun practice(
        puzzle: Puzzle,
        technique: TechniqueId,
        scope: CoroutineScope,
        onTrainedTurn: (TrainerVerdict) -> Unit,
    ): PracticeBoard? {
        if (puzzle.gameId != gameId) return null
        val decoded = (QueensCodec.decode(puzzle.payload) as? DecodeResult.Ok)?.value ?: return null
        val start = QueensPractice.start(decoded, technique) ?: return null
        return QueensPracticeBoard(decoded, technique, start, compute, scope, onTrainedTurn)
    }

    @Composable
    override fun Board(board: PracticeBoard, modifier: Modifier) {
        val practice = board as? QueensPracticeBoard ?: error("a Queens trainer was handed another game's board: $board")
        val status by practice.status.collectAsState()
        Box(modifier.aspectRatio(1f)) {
            QueensPlayBoard(practice.state, Modifier.matchParentSize())
            PracticeSpotlight(practice, status)
        }
    }
}
