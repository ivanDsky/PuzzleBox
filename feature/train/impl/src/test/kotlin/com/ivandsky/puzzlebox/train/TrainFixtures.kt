package com.ivandsky.puzzlebox.train

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightGlyphs
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.DrillSource
import com.ivandsky.puzzlebox.gameui.trainer.Snippet
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlin.coroutines.CoroutineContext

/** A snippet that is only its technique and a label; two of them are never the same snippet. */
internal class FakeSnippet(override val technique: TechniqueId, val label: String) : Snippet {
    override val prompt: String = "Find the step: $label"
}

/**
 * A trainer with the techniques [techniques]. Each puzzle yields [perPuzzle] snippets at most, and [requested] records
 * the count each call asked for, so a test sees how the round walked the puzzles.
 */
internal class FakeTrainerGame(
    override val gameId: GameId,
    private val techniques: List<TrainerTechnique>,
    private val perPuzzle: Int = 5,
) : TrainerGame {

    val requested = mutableListOf<Int>()

    override fun catalog(): List<TrainerTechnique> = techniques

    override val drillSources: List<DrillSource> = emptyList()

    override fun buildSnippets(puzzle: Puzzle, technique: TechniqueId, count: Int): List<Snippet> {
        requested += count
        return List(minOf(count, perPuzzle)) { FakeSnippet(technique, "${puzzle.payload.last()}-$it") }
    }

    @Composable
    override fun Board(snippet: Snippet, onVerdict: (TrainerVerdict) -> Unit, modifier: Modifier) = Unit

    override fun spotlight(snippet: Snippet): Spotlight =
        Spotlight(1, 1, emptyMap(), callout = null, glyphs = SpotlightGlyphs { _, _, _, _ -> }, playId = 0)
}

/** Techniques named after their ids, for a catalog of [ids]. */
internal fun techniques(vararg ids: String): List<TrainerTechnique> =
    ids.map { TrainerTechnique(TechniqueId(it), it.replaceFirstChar(Char::uppercase), "The line of $it.") }

/** Every dispatcher runs its work at once on the caller's thread, so a test sees each step's result when the call returns. */
internal object InlineDispatcherProvider : DispatcherProvider {
    private val inline = object : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext) = false
        override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
    }
    override val main: CoroutineDispatcher = inline
    override val io: CoroutineDispatcher = inline
    override val default: CoroutineDispatcher = inline
}
