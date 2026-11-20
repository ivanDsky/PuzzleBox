package com.ivandsky.puzzlebox.games.sudokuui.trainer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuSnippets
import com.ivandsky.puzzlebox.games.sudokuui.hint.SudokuSpotlightMapper
import com.ivandsky.puzzlebox.gameui.trainer.DrillSource
import com.ivandsky.puzzlebox.gameui.trainer.Snippet
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The Mini Sudoku [TrainerGame]: the Compose half of [SudokuSnippets] and the Sudoku technique walker. The technique
 * list shares its sentences with the hints ([SudokuTrainerCatalog]), a snippet board is the live grid over a board the
 * walk reached with a digit picker for the one move ([SudokuSnippetBoard]), and the reveal after a verdict is the
 * hint's own Spotlight with its glyphs drawn in the board's digit style.
 *
 * [com.ivandsky.puzzlebox.games.sudokuui.miniSudokuUiModule] binds it while TrainSudoku is on. [compute] runs the move
 * check off the main thread. Only Mini Sudoku trains: classic Sudoku has no daily archive and no trainer.
 */
internal class SudokuTrainer(private val compute: CoroutineDispatcher) : TrainerGame {

    override val gameId: GameId = SudokuGames.MINI.id

    override fun catalog(): List<TrainerTechnique> = SudokuTrainerCatalog.techniques

    override val drillSources: List<DrillSource> = listOf(SudokuIndexDrillSource(), SudokuGeneratedDrillSource())

    /**
     * Empty for another game's puzzle, one that does not decode, one whose stored solution is not its unique solution
     * (the move check judges against it), and a technique it never uses.
     */
    override fun buildSnippets(puzzle: Puzzle, technique: TechniqueId, count: Int): List<Snippet> {
        if (puzzle.gameId != gameId) return emptyList()
        val decoded = (SudokuGames.MINI.decodePuzzle(puzzle.payload) as? DecodeResult.Ok)?.value ?: return emptyList()
        return SudokuSnippets.cut(decoded, technique, count).map { SudokuSnippet(decoded, it) }
    }

    @Composable
    override fun Board(snippet: Snippet, onVerdict: (TrainerVerdict) -> Unit, modifier: Modifier) {
        SudokuSnippetBoard(snippet.asSudoku(), compute, onVerdict, modifier)
    }

    /**
     * The hint's Spotlight for the step that proved the player's own Right move when it was another instance than the
     * cut's, else for the cut's own step, its glyphs redrawn in the board's digit style. The mapper reads the step's own
     * struck digits and evidence, not the walk's earlier strikes ([SudokuSnippet.cut]'s `struckBefore`), so the reveal
     * shows what the technique proves on the board's real candidates.
     */
    override fun spotlight(snippet: Snippet): Spotlight {
        val sudoku = snippet.asSudoku()
        val cut = sudoku.cut
        return SudokuSpotlightMapper.map(sudoku.proving.step ?: cut.step, sudoku.puzzle, cut.boardBefore, playId = 0).revealedIn(sudoku.digits)
    }

    private fun Snippet.asSudoku(): SudokuSnippet =
        this as? SudokuSnippet ?: error("a Sudoku trainer was handed another game's snippet: $this")
}
