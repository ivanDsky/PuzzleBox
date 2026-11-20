package com.ivandsky.puzzlebox.games.queensui.trainer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.DrillSource
import com.ivandsky.puzzlebox.gameui.trainer.Snippet
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensSnippets
import com.ivandsky.puzzlebox.games.queensui.hint.QueensSpotlightMapper
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The Queens [TrainerGame]: the Compose half of [QueensSnippets] and the Queens technique walker. The technique list
 * shares its sentences with the hints ([QueensTrainerCatalog]), a snippet board is the live board's face over a board
 * the walk reached ([QueensSnippetBoard]), and the reveal after a verdict is the hint's own Spotlight.
 *
 * [com.ivandsky.puzzlebox.games.queensui.queensUiModule] binds it while TrainQueens is on. [compute] runs the move
 * check, which a long look-ahead makes take up to 90 ms.
 */
internal class QueensTrainer(private val compute: CoroutineDispatcher) : TrainerGame {

    override val gameId: GameId = QueensDefinition.id

    override fun catalog(): List<TrainerTechnique> = QueensTrainerCatalog.techniques

    override val drillSources: List<DrillSource> = listOf(QueensIndexDrillSource(), QueensGeneratedDrillSource())

    /** Empty for another game's puzzle, one that does not decode, one without a true solution, and a technique it never uses. */
    override fun buildSnippets(puzzle: Puzzle, technique: TechniqueId, count: Int): List<Snippet> {
        if (puzzle.gameId != gameId) return emptyList()
        val decoded = (QueensCodec.decode(puzzle.payload) as? DecodeResult.Ok)?.value ?: return emptyList()
        return QueensSnippets.cut(decoded, technique, count).map { QueensSnippet(decoded, it) }
    }

    @Composable
    override fun Board(snippet: Snippet, onVerdict: (TrainerVerdict) -> Unit, modifier: Modifier) {
        QueensSnippetBoard(snippet.asQueens(), compute, onVerdict, modifier)
    }

    override fun spotlight(snippet: Snippet): Spotlight {
        val queens = snippet.asQueens()
        return QueensSpotlightMapper.map(queens.cut.step, queens.puzzle, playId = 0)
    }

    private fun Snippet.asQueens(): QueensSnippet =
        this as? QueensSnippet ?: error("a Queens trainer was handed another game's snippet: $this")
}
