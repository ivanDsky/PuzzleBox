package com.ivandsky.puzzlebox.gameui.trainer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTrainerGame(override val gameId: GameId) : TrainerGame {
    override fun catalog(): List<TrainerTechnique> = emptyList()

    override val drillSources: List<DrillSource> = emptyList()

    override fun practice(
        puzzle: Puzzle,
        technique: TechniqueId,
        scope: CoroutineScope,
        onTrainedTurn: (TrainerVerdict) -> Unit,
    ): PracticeBoard? = null

    @Composable
    override fun Board(board: PracticeBoard, modifier: Modifier) = Unit
}

class TrainerRegistryTest {

    private val queens = FakeTrainerGame(GameId("queens"))
    private val tango = FakeTrainerGame(GameId("tango"))
    private val sudoku = FakeTrainerGame(GameId("sudoku"))
    private val order = listOf(queens.gameId, tango.gameId, sudoku.gameId)

    @Test
    fun `the order list sets the order whatever order the games arrive in`() {
        val registry = TrainerRegistry(listOf(tango, sudoku, queens), order)

        assertEquals(listOf<TrainerGame>(queens, tango, sudoku), registry.all)
    }

    @Test
    fun `an order id with no game is skipped`() {
        val registry = TrainerRegistry(listOf(sudoku, queens), order)

        assertEquals(listOf<TrainerGame>(queens, sudoku), registry.all)
        assertNull(registry[tango.gameId])
    }

    @Test
    fun `games not in the order come last sorted by game id`() {
        val zip = FakeTrainerGame(GameId("zip"))
        val strands = FakeTrainerGame(GameId("strands"))

        val registry = TrainerRegistry(listOf(zip, sudoku, strands, queens), listOf(queens.gameId, tango.gameId))

        assertEquals(listOf<TrainerGame>(queens, strands, sudoku, zip), registry.all)
    }

    @Test
    fun `get finds a bound game and nothing else`() {
        val registry = TrainerRegistry(listOf(queens), order)

        assertSame(queens, registry[queens.gameId])
        assertNull(registry[sudoku.gameId])
    }

    @Test
    fun `no game bound means empty`() {
        assertTrue(TrainerRegistry(emptyList(), order).isEmpty)
        assertFalse(TrainerRegistry(listOf(queens), order).isEmpty)
    }

    @Test
    fun `a duplicate game id is a wiring bug and fails loudly`() {
        try {
            TrainerRegistry(listOf(FakeTrainerGame(GameId("fake")), FakeTrainerGame(GameId("fake"))), order)
            assertTrue("expected IllegalArgumentException", false)
        } catch (expected: IllegalArgumentException) {
            // duplicates never silently overwrite
        }
    }

    @Test
    fun `a game listed twice in the order is a wiring bug and fails loudly`() {
        try {
            TrainerRegistry(listOf(queens), listOf(queens.gameId, queens.gameId))
            assertTrue("expected IllegalArgumentException", false)
        } catch (expected: IllegalArgumentException) {
            // a repeated order id would list the game twice
        }
    }
}
