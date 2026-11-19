package com.ivandsky.puzzlebox.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.trainer.DrillSource
import com.ivandsky.puzzlebox.gameui.trainer.Snippet
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import com.ivandsky.puzzlebox.gameui.trainer.TrainerRegistry
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeTrainerGame(override val gameId: GameId) : TrainerGame {
    override fun catalog(): List<TrainerTechnique> = emptyList()

    override val drillSources: List<DrillSource> = emptyList()

    override fun buildSnippets(puzzle: Puzzle, technique: TechniqueId, count: Int): List<Snippet> = emptyList()

    @Composable
    override fun Board(snippet: Snippet, onVerdict: (TrainerVerdict) -> Unit, modifier: Modifier) = Unit

    override fun spotlight(snippet: Snippet): Spotlight = error("unused")
}

/** Games' Train tab follows the Trainer registry (R3): no adapter bound is no slot, any adapter is the Train slot, with its body and its right pane. */
class TrainSlotTest {

    private val order = listOf(GameId("queens"))

    @Test
    fun `an empty registry builds no Train slot`() {
        PlayLayout.entries.forEach { layout ->
            assertNull(trainSlotOrNull(TrainerRegistry(emptyList(), order), layout))
        }
    }

    @Test
    fun `a registry with one trainer builds the Train slot on either layout`() {
        PlayLayout.entries.forEach { layout ->
            assertNotNull(trainSlotOrNull(TrainerRegistry(listOf(FakeTrainerGame(GameId("queens"))), order), layout))
        }
    }
}
