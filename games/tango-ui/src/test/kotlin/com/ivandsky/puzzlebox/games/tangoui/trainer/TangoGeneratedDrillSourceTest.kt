package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.trainer.GeneratedDrillSet
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Test

class TangoGeneratedDrillSourceTest {

    private val first = TechniqueId("pair-fill")
    private val second = TechniqueId("balance")

    private fun puzzle(game: GameId, tag: Int) = Puzzle(game, VariantId("tango/6x6"), 1, byteArrayOf(tag.toByte(), 9))

    private fun drill(game: GameId, technique: TechniqueId, steps: Int, tag: Int): GeneratedDrillSet.Drill {
        val puzzle = puzzle(game, tag)
        return GeneratedDrillSet.Drill(GeneratedDrillSet.idOf(puzzle.payload), technique, steps, puzzle)
    }

    private fun text(game: GameId): String = GeneratedDrillSet.write(
        GeneratedDrillSet(
            game, 1,
            listOf(drill(game, first, 3, 1), drill(game, first, 7, 2), drill(game, first, 7, 3), drill(game, second, 2, 4)),
        ),
    )

    private fun candidate(drill: GeneratedDrillSet.Drill) = DrillCandidate.Bundled(drill.id, drill.puzzle, drill.targetSteps)

    @Test
    fun `candidates come most target steps first, ties by id, each carrying its puzzle`() {
        val source = TangoGeneratedDrillSource { text(TangoDefinition.id) }
        val game = TangoDefinition.id

        val sevens = listOf(drill(game, first, 7, 2), drill(game, first, 7, 3)).sortedBy { it.id }
        assertEquals((sevens + drill(game, first, 3, 1)).map(::candidate), source.candidates(first))
        assertEquals(listOf(candidate(drill(game, second, 2, 4))), source.candidates(second))
    }

    @Test
    fun `a technique no generated puzzle drills has no candidates`() {
        val source = TangoGeneratedDrillSource { text(TangoDefinition.id) }

        assertEquals(emptyList<DrillCandidate>(), source.candidates(TechniqueId("long-look-ahead")))
    }

    @Test
    fun `no set, a broken set and another game's set offer nothing`() {
        assertEquals(emptyList<DrillCandidate>(), TangoGeneratedDrillSource { null }.candidates(first))
        assertEquals(emptyList<DrillCandidate>(), TangoGeneratedDrillSource { "not a drill set" }.candidates(first))
        assertEquals(emptyList<DrillCandidate>(), TangoGeneratedDrillSource { text(GameId("queens")) }.candidates(first))
    }

    @Test
    fun `the shipped set is read once, on the first ask`() {
        var reads = 0
        val source = TangoGeneratedDrillSource { reads++; text(TangoDefinition.id) }

        assertEquals(0, reads)
        source.candidates(first)
        source.candidates(second)
        assertEquals(1, reads)
    }
}
