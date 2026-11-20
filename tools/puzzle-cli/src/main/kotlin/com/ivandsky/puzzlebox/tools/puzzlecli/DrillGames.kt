package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuRung
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueIds
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung
import com.ivandsky.puzzlebox.model.GameId

/**
 * The games `generate-drills` covers, each with the technique ids its Trainer catalog lists (reached from the engines,
 * so a new technique is covered the day the engine names it), its generator and the walker of the registry's plugin
 * for the same game id. A game whose plugin has no technique walker is left out, as in [IndexTargets].
 */
object DrillGames {

    /** Mini Sudoku's technique ids ranked by [SudokuRung], so a pointing drill may hold nothing of the pairs. */
    private val miniFamilies: Map<TechniqueId, Int> =
        SudokuTechniqueIds.byTechnique.entries.associate { (technique, id) -> id to SudokuRung.of(technique).ordinal } +
            (SudokuTechniqueIds.lastCell to SudokuRung.SINGLES.ordinal)

    /** The games of [registry] that have a walker, in a fixed order: Queens, Tango, Mini Sudoku. */
    fun of(registry: GameRegistry): List<DrillGame> = listOfNotNull(
        game(
            registry, QueensDefinition.id,
            QueensLadder.Stepwise.rungs.mapNotNull { it.trainerId }, QueensDrillGenerator,
        ),
        game(
            registry, TangoDefinition.id,
            TangoStepRung.entries.map { it.trainerId }, TangoDrillGenerator,
        ),
        game(
            registry, SudokuGames.MINI.id,
            SudokuTechniqueIds.all, MiniSudokuGenerator, families = miniFamilies, accepts = { puzzle, _ -> MiniSudokuWalk.staysListed(puzzle) },
        ),
    )

    private fun game(
        registry: GameRegistry,
        id: GameId,
        techniques: List<TechniqueId>,
        generator: DrillGenerator,
        families: Map<TechniqueId, Int> = emptyMap(),
        accepts: (Puzzle, TechniqueId) -> Boolean = { _, _ -> true },
    ): DrillGame? {
        val walker = registry[id]?.techniqueWalker ?: return null
        return DrillGame(id, techniques, walker, generator, families, accepts)
    }
}
