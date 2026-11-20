package com.ivandsky.puzzlebox.games.sudokuui.trainer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuPractice
import com.ivandsky.puzzlebox.games.sudokuui.SudokuStackedBoard
import com.ivandsky.puzzlebox.gameui.trainer.DrillSource
import com.ivandsky.puzzlebox.gameui.trainer.PracticeBoard
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.CoroutineScope

/**
 * The Mini Sudoku [TrainerGame]: the Compose half of [SudokuPractice] and the Sudoku technique walker. The technique list
 * shares its sentences with the hints ([SudokuTrainerCatalog]), and a practice board is the daily's own play board
 * ([SudokuPracticeBoard]) over a drill puzzle, its status Spotlight the hint's own ([com.ivandsky.puzzlebox.games.sudokuui.hint.SudokuSpotlightMapper]).
 *
 * [com.ivandsky.puzzlebox.games.sudokuui.miniSudokuUiModule] binds it while TrainSudoku is on. Only Mini Sudoku trains:
 * classic Sudoku has no daily archive and no trainer.
 */
internal class SudokuTrainer : TrainerGame {

    override val gameId: GameId = SudokuGames.MINI.id

    override fun catalog(): List<TrainerTechnique> = SudokuTrainerCatalog.techniques

    override val drillSources: List<DrillSource> = listOf(SudokuIndexDrillSource(), SudokuGeneratedDrillSource())

    /**
     * Null for another game's puzzle, one that does not decode, one whose stored solution is not its unique solution
     * (every move is judged against it), a technique the ladder does not know, and a technique that is never the
     * easiest rung on this puzzle. A move is judged on the board's own state, with no coroutine: [scope] goes unused.
     */
    override fun practice(
        puzzle: Puzzle,
        technique: TechniqueId,
        scope: CoroutineScope,
        onTrainedTurn: (TrainerVerdict) -> Unit,
    ): PracticeBoard? {
        if (puzzle.gameId != gameId) return null
        val decoded = (SudokuGames.MINI.decodePuzzle(puzzle.payload) as? DecodeResult.Ok)?.value ?: return null
        val start = SudokuPractice.start(decoded, technique) ?: return null
        return SudokuPracticeBoard(decoded, start, technique, onTrainedTurn)
    }

    /** The real play board's grid and keypad; the grid draws the status Spotlight, and the player's next input clears it. */
    @Composable
    override fun Board(board: PracticeBoard, modifier: Modifier) {
        val practice = board as? SudokuPracticeBoard ?: error("a Sudoku trainer was handed another game's practice board: $board")
        SudokuStackedBoard(practice.state, modifier, tuned = true, eraseBeside = true, spotlight = practice.shown)
    }
}
