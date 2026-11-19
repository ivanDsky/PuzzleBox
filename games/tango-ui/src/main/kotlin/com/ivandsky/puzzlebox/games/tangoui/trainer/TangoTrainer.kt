package com.ivandsky.puzzlebox.games.tangoui.trainer

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
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoSnippets
import com.ivandsky.puzzlebox.games.tangoui.hint.TangoSpotlightMapper
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The Tango [TrainerGame]: the Compose half of [TangoSnippets] and the Tango technique walker. The technique list
 * shares its sentences with the hints ([TangoTrainerCatalog]), a snippet board is the live board's renderer over a
 * grid the walk reached ([TangoSnippetBoard]), and the reveal after a verdict is the hint's own Spotlight.
 *
 * [com.ivandsky.puzzlebox.games.tangoui.tangoUiModule] binds it while TrainTango is on. [compute] runs the move
 * check off the main thread.
 */
internal class TangoTrainer(private val compute: CoroutineDispatcher) : TrainerGame {

    override val gameId: GameId = TangoDefinition.id

    override fun catalog(): List<TrainerTechnique> = TangoTrainerCatalog.techniques

    override val drillSources: List<DrillSource> = listOf(TangoIndexDrillSource())

    /**
     * Empty for another game's puzzle, one that does not decode, one without a unique solution (the move check judges
     * against it), and a technique it never uses. The puzzle is solved once here; a cut never solves.
     */
    override fun buildSnippets(puzzle: Puzzle, technique: TechniqueId, count: Int): List<Snippet> {
        if (puzzle.gameId != gameId) return emptyList()
        val decoded = (TangoCodec.decode(puzzle.payload) as? DecodeResult.Ok)?.value ?: return emptyList()
        val solution = TangoUniqueness.solve(decoded) ?: return emptyList()
        return TangoSnippets.cut(decoded, solution, technique, count).map { TangoSnippet(decoded, solution, it) }
    }

    @Composable
    override fun Board(snippet: Snippet, onVerdict: (TrainerVerdict) -> Unit, modifier: Modifier) {
        TangoSnippetBoard(snippet.asTango(), compute, onVerdict, modifier)
    }

    override fun spotlight(snippet: Snippet): Spotlight {
        val tango = snippet.asTango()
        return TangoSpotlightMapper.map(tango.cut.step, tango.puzzle, tango.cut.gridBefore, playId = 0)
    }

    private fun Snippet.asTango(): TangoSnippet =
        this as? TangoSnippet ?: error("a Tango trainer was handed another game's snippet: $this")
}
