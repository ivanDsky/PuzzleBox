package com.ivandsky.puzzlebox.gameui.trainer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.model.GameId

/**
 * One game's Trainer adapter: the Compose half of its technique walker. The Train feature draws
 * any game's technique list and snippet rounds through this seam and never names a game. A game
 * has a trainer iff its UI module binds one; the [TrainerRegistry] holds the bound adapters.
 */
interface TrainerGame {
    /** The game this adapter trains. */
    val gameId: GameId

    /** The game's techniques with their plain copy, easiest first (the stepper's own order). */
    fun catalog(): List<TrainerTechnique>

    /** How this game finds drill puzzles, one strategy per source (the index, the generated set). */
    val drillSources: List<DrillSource>

    /**
     * Up to [count] snippets of [technique] built from [puzzle]. Each snippet stops just before a
     * step of the target technique, never before a reveal fallback. Empty when [puzzle] has no such
     * step.
     */
    fun buildSnippets(puzzle: Puzzle, technique: TechniqueId, count: Int): List<Snippet>

    /**
     * The board of [snippet]. It owns the tap: the player's one move is judged and reported once
     * through [onVerdict].
     */
    @Composable
    fun Board(snippet: Snippet, onVerdict: (TrainerVerdict) -> Unit, modifier: Modifier)

    /** The step [snippet] stops before, as a Spotlight: the reveal after a verdict, or on Show me. */
    fun spotlight(snippet: Snippet): Spotlight
}
