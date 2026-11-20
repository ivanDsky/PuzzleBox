package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.trainer.GeneratedDrillSet
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensTechniqueWalker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test

/**
 * The shipped Queens drill set (`/trainer/queens-generated.txt`) against the current walker. Set tests skip while the
 * file is absent (a build before the generation pass); the catalog test does not: with the real index and set in, every
 * technique the catalog lists has a puzzle on offer.
 */
class QueensGeneratedResourceTest {

    private val set: GeneratedDrillSet? by lazy {
        val text = QueensGeneratedDrillSource::class.java.getResourceAsStream(QueensGeneratedDrillSource.RESOURCE_PATH)
            ?.use { it.readBytes().decodeToString() }
        text?.let {
            when (val read = GeneratedDrillSet.read(it)) {
                is DecodeResult.Ok -> read.value
                is DecodeResult.Err -> throw AssertionError("the shipped Queens drill set does not read: ${read.error}")
            }
        }
    }

    @Test
    fun `the set is Queens and carries the walker's technique set`() {
        val shipped = set
        assumeNotNull(shipped)

        assertEquals(QueensDefinition.id, shipped!!.gameId)
        assertEquals(QueensTechniqueWalker.techniqueSetVersion, shipped.techniqueSetVersion)
    }

    @Test
    fun `every shipped drill shows its technique exactly its target steps`() {
        val shipped = set
        assumeNotNull(shipped)

        for (drill in shipped!!.drills) {
            val counts = QueensTechniqueWalker.techniqueCounts(drill.puzzle)
            assertEquals("drill ${drill.id} (${drill.technique.value})", drill.targetSteps, counts[drill.technique])
        }
    }

    @Test
    fun `every technique the catalog lists has a puzzle on offer across the drill sources`() {
        val trainer = QueensTrainerFixtures.trainer
        for (technique in trainer.catalog()) {
            val offered = trainer.drillSources.sumOf { it.candidates(technique.id).size }
            assertTrue("no drill source offers ${technique.id.value}", offered > 0)
        }
    }
}
