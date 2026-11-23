package com.ivandsky.puzzlebox.games.tangoui.trainer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.DrillSource
import com.ivandsky.puzzlebox.gameui.trainer.PracticeBoard
import com.ivandsky.puzzlebox.gameui.trainer.PracticeSpotlight
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique
import com.ivandsky.puzzlebox.gameui.trainer.IntroDemo
import com.ivandsky.puzzlebox.gameui.trainer.IntroVariant
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoPractice
import com.ivandsky.puzzlebox.games.tangoui.TangoBoard
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * The Tango [TrainerGame]: the Compose half of [TangoPractice] and the Tango technique walker. The technique list
 * shares its sentences with the hints ([TangoTrainerCatalog]); a practice board ([TangoPracticeBoard]) is the daily's
 * own board and controller over a drill puzzle, and its Spotlights are the hint's own.
 *
 * [com.ivandsky.puzzlebox.games.tangoui.tangoUiModule] binds it. [compute] is the dispatcher
 * of the controller's Stepwise hint search, which a practice board never presses.
 */
internal class TangoTrainer(private val compute: CoroutineDispatcher) : TrainerGame {

    override val gameId: GameId = TangoDefinition.id

    override fun catalog(): List<TrainerTechnique> = TangoTrainerCatalog.techniques

    override val drillSources: List<DrillSource> = listOf(TangoIndexDrillSource(), TangoGeneratedDrillSource())

    /**
     * Null for another game's puzzle, one that does not decode, one without a unique solution (the judge reads it), and
     * a technique that is never the easiest rung on the puzzle (neither kind opens). The puzzle is solved once here.
     */
    override fun practice(
        puzzle: Puzzle,
        technique: TechniqueId,
        kind: BoardKind,
        scope: CoroutineScope,
        onTrainedTurn: (TrainerVerdict) -> Unit,
    ): PracticeBoard? {
        if (puzzle.gameId != gameId) return null
        val decoded = (TangoCodec.decode(puzzle.payload) as? DecodeResult.Ok)?.value ?: return null
        val solution = TangoUniqueness.solve(decoded) ?: return null
        val start = TangoPractice.start(decoded, solution, technique, kind) ?: return null
        return TangoPracticeBoard(decoded, solution, technique, start, kind, scope, compute, onTrainedTurn)
    }

    @Composable
    override fun Board(board: PracticeBoard, modifier: Modifier) {
        val practice = board as? TangoPracticeBoard ?: error("a Tango trainer was handed another game's board: $board")
        TangoBoard(
            controller = practice.controller,
            paneCap = null,
            modifier = modifier,
            onTap = practice::onTap,
            onPlacement = practice::onPlacement,
            overlay = {
                val status by practice.status.collectAsState()
                PracticeSpotlight(practice, status)
            },
        )
    }

    override fun variants(technique: TechniqueId): List<IntroVariant> = TangoIntroVariants.of(technique)

    /**
     * Null for another game's puzzle, one that does not decode, one without a unique solution, a variant the technique
     * has no such index for, and a technique that is never the easiest rung on the puzzle. Otherwise the step that opens
     * the puzzle's focus board ([TangoPractice.opening]), so frame 0 is exactly the board practice opens on.
     */
    override fun demo(puzzle: Puzzle, technique: TechniqueId, variant: Int): IntroDemo? {
        if (puzzle.gameId != gameId) return null
        val decoded = (TangoCodec.decode(puzzle.payload) as? DecodeResult.Ok)?.value ?: return null
        val solution = TangoUniqueness.solve(decoded) ?: return null
        val opening = TangoPractice.opening(decoded, solution, technique, BoardKind.Focus) ?: return null
        if (variant !in variants(technique).indices) return null
        return TangoIntroDemo.of(decoded, opening, variant)
    }

    @Composable
    override fun Demo(demo: IntroDemo, frame: Int, modifier: Modifier) {
        val tango = demo as? TangoIntroDemo ?: error("a Tango trainer was handed another game's demo: $demo")
        TangoDemoBoard(tango, frame, compute, modifier)
    }
}
