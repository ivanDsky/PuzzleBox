package com.ivandsky.puzzlebox.games.sudokuui.trainer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuIntro
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuPractice
import com.ivandsky.puzzlebox.games.sudokuui.SudokuStackedBoard
import com.ivandsky.puzzlebox.gameui.trainer.DrillSource
import com.ivandsky.puzzlebox.gameui.trainer.IntroDemo
import com.ivandsky.puzzlebox.gameui.trainer.IntroVariant
import com.ivandsky.puzzlebox.gameui.trainer.PracticeBoard
import com.ivandsky.puzzlebox.gameui.trainer.PracticeSpotlight
import com.ivandsky.puzzlebox.gameui.trainer.PracticeStatus
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
     * easiest rung on this puzzle (for either [kind]). A move is judged on the board's own state, with no coroutine: [scope] goes unused.
     */
    override fun practice(
        puzzle: Puzzle,
        technique: TechniqueId,
        kind: BoardKind,
        scope: CoroutineScope,
        onTrainedTurn: (TrainerVerdict) -> Unit,
    ): PracticeBoard? {
        if (puzzle.gameId != gameId) return null
        val decoded = (SudokuGames.MINI.decodePuzzle(puzzle.payload) as? DecodeResult.Ok)?.value ?: return null
        val start = SudokuPractice.start(decoded, technique, kind) ?: return null
        return SudokuPracticeBoard(decoded, start, technique, kind, onTrainedTurn)
    }

    /**
     * The real play board's grid and keypad. The grid draws [PracticeSpotlight] over its cells, with the Spotlight's digits
     * measured in the grid's own style; the keypad sits outside it, so [SudokuPracticeBoard] refuses the moves the keypad
     * sends while an explanation stands.
     */
    @Composable
    override fun Board(board: PracticeBoard, modifier: Modifier) {
        val practice = board as? SudokuPracticeBoard ?: error("a Sudoku trainer was handed another game's practice board: $board")
        val status by practice.status.collectAsState()
        SudokuStackedBoard(practice.state, modifier, tuned = true, eraseBeside = true) { restyle ->
            val styled = remember(status, restyle) { status.restyled(restyle) }
            PracticeSpotlight(practice, styled)
        }
    }

    override fun variants(technique: TechniqueId): List<IntroVariant> = SudokuIntroVariants.of(technique)

    /**
     * Null for another game's puzzle, one that does not decode, one whose stored solution is not its unique solution, a
     * technique that is never the easiest rung on this puzzle, and an opening step of another variant ([SudokuIntro.record]).
     */
    override fun demo(puzzle: Puzzle, technique: TechniqueId, variant: Int): IntroDemo? {
        if (puzzle.gameId != gameId) return null
        val decoded = (SudokuGames.MINI.decodePuzzle(puzzle.payload) as? DecodeResult.Ok)?.value ?: return null
        return SudokuIntro.record(decoded, technique, variant)?.let { SudokuIntroDemo.of(decoded, it) }
    }

    /** A frame of a demo on the real play board, as [Board] draws it, with every touch consumed ([SudokuDemoBoard]). */
    @Composable
    override fun Demo(demo: IntroDemo, frame: Int, modifier: Modifier) {
        val recorded = demo as? SudokuIntroDemo ?: error("a Sudoku trainer was handed another game's demo: $demo")
        SudokuDemoBoard(recorded, frame, modifier)
    }
}

/** [this] with its Spotlight, if it has one, passed through [restyle]. */
private fun PracticeStatus.restyled(restyle: (Spotlight) -> Spotlight): PracticeStatus = when (this) {
    is PracticeStatus.Right -> copy(spotlight = restyle(spotlight))
    is PracticeStatus.Easier -> copy(spotlight = restyle(spotlight))
    is PracticeStatus.Shown -> copy(spotlight = restyle(spotlight))
    is PracticeStatus.Done -> copy(last = last?.let(restyle))
    PracticeStatus.FindNextMove, is PracticeStatus.KeepGoing, is PracticeStatus.Wrong -> this
}
