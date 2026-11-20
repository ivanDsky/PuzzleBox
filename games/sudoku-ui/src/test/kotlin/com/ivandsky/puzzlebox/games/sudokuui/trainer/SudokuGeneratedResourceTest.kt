package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.trainer.GeneratedDrillSet
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuRung
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueIds
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueWalker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test

/**
 * The shipped Mini Sudoku drill set (`/trainer/minisudoku-generated.txt`) against the current walker. Set tests skip
 * while the file is absent (a build before the generation pass); the catalog test does not: with the real index and
 * set in, every technique the catalog lists has a puzzle on offer.
 */
class SudokuGeneratedResourceTest {

    private val set: GeneratedDrillSet? by lazy {
        val text = SudokuGeneratedDrillSource::class.java.getResourceAsStream(SudokuGeneratedDrillSource.RESOURCE_PATH)
            ?.use { it.readBytes().decodeToString() }
        text?.let {
            when (val read = GeneratedDrillSet.read(it)) {
                is DecodeResult.Ok -> read.value
                is DecodeResult.Err -> throw AssertionError("the shipped Mini Sudoku drill set does not read: ${read.error}")
            }
        }
    }

    /** The ladder rung of each listed technique id: the family a drill for it may not exceed. */
    private val family: Map<TechniqueId, Int> =
        SudokuTechniqueIds.byTechnique.entries.associate { (technique, id) -> id to SudokuRung.of(technique).ordinal } +
            (SudokuTechniqueIds.lastCell to SudokuRung.SINGLES.ordinal)

    @Test
    fun `the set is Mini Sudoku and carries the walker's technique set`() {
        val shipped = set
        assumeNotNull(shipped)

        assertEquals(SudokuGames.MINI.id, shipped!!.gameId)
        assertEquals(SudokuTechniqueWalker.techniqueSetVersion, shipped.techniqueSetVersion)
    }

    @Test
    fun `every shipped drill shows its technique exactly its target steps and nothing harder`() {
        val shipped = set
        assumeNotNull(shipped)

        for (drill in shipped!!.drills) {
            val counts = SudokuTechniqueWalker.techniqueCounts(drill.puzzle)
            val label = "drill ${drill.id} (${drill.technique.value})"
            assertEquals(label, drill.targetSteps, counts[drill.technique])
            val ceiling = family.getValue(drill.technique)
            for (used in counts.keys) {
                assertTrue("$label also needs ${used.value}", family.getValue(used) <= ceiling)
            }
        }
    }

    @Test
    fun `every technique the catalog lists has a puzzle on offer across the drill sources`() {
        val trainer = SudokuTrainerFixtures.trainer
        for (technique in trainer.catalog()) {
            val offered = trainer.drillSources.sumOf { it.candidates(technique.id).size }
            assertTrue("no drill source offers ${technique.id.value}", offered > 0)
        }
    }
}
