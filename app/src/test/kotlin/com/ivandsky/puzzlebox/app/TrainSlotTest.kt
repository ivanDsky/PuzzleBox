package com.ivandsky.puzzlebox.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.trainer.DrillSource
import com.ivandsky.puzzlebox.gameui.trainer.IntroDemo
import com.ivandsky.puzzlebox.gameui.trainer.IntroVariant
import com.ivandsky.puzzlebox.gameui.trainer.PracticeBoard
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import com.ivandsky.puzzlebox.gameui.trainer.TrainerRegistry
import com.ivandsky.puzzlebox.gameui.trainer.TrainerTechnique
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeTrainerGame(override val gameId: GameId) : TrainerGame {
    override fun catalog(): List<TrainerTechnique> = emptyList()

    override val drillSources: List<DrillSource> = emptyList()

    override fun practice(
        puzzle: Puzzle,
        technique: TechniqueId,
        kind: BoardKind,
        scope: CoroutineScope,
        onTrainedTurn: (TrainerVerdict) -> Unit,
    ): PracticeBoard? = null

    @Composable
    override fun Board(board: PracticeBoard, modifier: Modifier) = Unit

    override fun variants(technique: TechniqueId): List<IntroVariant> = listOf(IntroVariant("Fake", "Rule.", "Spot it.", "Do it."))

    override fun demo(puzzle: Puzzle, technique: TechniqueId, variant: Int): IntroDemo? = null

    @Composable
    override fun Demo(demo: IntroDemo, frame: Int, modifier: Modifier) = Unit
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
