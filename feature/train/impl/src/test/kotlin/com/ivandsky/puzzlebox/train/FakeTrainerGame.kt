package com.ivandsky.puzzlebox.train

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.DrillSource
import com.ivandsky.puzzlebox.gameui.trainer.IntroDemo
import com.ivandsky.puzzlebox.gameui.trainer.IntroVariant
import com.ivandsky.puzzlebox.gameui.trainer.PracticeBoard
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.train.practice.StandInGrid
import kotlinx.coroutines.CoroutineScope

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

    /** The variants the technique has, and the demo of a puzzle for one of them (none by default, so a technique has no intro). */
    var variantList = listOf(IntroVariant("Fake", "Rule.", "Spot it.", "Do it."))
    var demoFor: (Puzzle, TechniqueId, Int) -> IntroDemo? = { _, _, _ -> null }

    /** The trained-move count a puzzle reports for a technique; unknown (null) by default, as a game that does not say. */
    var reports = false
    override val reportsTrainedMoves: Boolean get() = reports
    var trainedMovesOf: (Puzzle, TechniqueId) -> Int? = { _, _ -> null }

    override fun catalog(): List<TrainerTechnique> = techniques

    override fun trainedMoves(puzzle: Puzzle, technique: TechniqueId): Int? = trainedMovesOf(puzzle, technique)

    override fun practice(
        puzzle: Puzzle,
        technique: TechniqueId,
        kind: BoardKind,
        scope: CoroutineScope,
        onTrainedTurn: (TrainerVerdict) -> Unit,
    ): PracticeBoard? {
        if (puzzle.payload.last() in silent) return null
        return FakePracticeBoard(technique, kind, puzzle, scope, onTrainedTurn).also { boards += it }
    }

    @Composable
    override fun Board(board: PracticeBoard, modifier: Modifier) = StandInGrid(modifier)

    override fun variants(technique: TechniqueId): List<IntroVariant> = variantList

    override fun demo(puzzle: Puzzle, technique: TechniqueId, variant: Int): IntroDemo? = demoFor(puzzle, technique, variant)

    @Composable
    override fun Demo(demo: IntroDemo, frame: Int, modifier: Modifier) = Unit
}
