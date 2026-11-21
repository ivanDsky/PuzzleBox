package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.trainer.GeneratedDrillSet
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoTechniqueWalker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test

/**
 * The shipped Tango drill set (`/trainer/tango-generated.txt`) against the current walker. Set tests skip while the
 * file is absent (a build before the generation pass); the catalog test does not: with the real index and set in, every
 * technique the catalog lists has a puzzle on offer.
 */
class TangoGeneratedResourceTest {

    private val set: GeneratedDrillSet? by lazy {
        val text = TangoGeneratedDrillSource::class.java.getResourceAsStream(TangoGeneratedDrillSource.RESOURCE_PATH)
            ?.use { it.readBytes().decodeToString() }
        text?.let {
            when (val read = GeneratedDrillSet.read(it)) {
                is DecodeResult.Ok -> read.value
                is DecodeResult.Err -> throw AssertionError("the shipped Tango drill set does not read: ${read.error}")
            }
        }
    }

    @Test
    fun `the set is Tango and carries the walker's technique set`() {
        val shipped = set
        assumeNotNull(shipped)

        assertEquals(TangoDefinition.id, shipped!!.gameId)
        assertEquals(TangoTechniqueWalker.techniqueSetVersion, shipped.techniqueSetVersion)
    }

    @Test
    fun `every shipped drill shows its technique exactly its target steps`() {
        val shipped = set
        assumeNotNull(shipped)

        for (drill in shipped!!.drills) {
            val counts = TangoTechniqueWalker.techniqueCounts(drill.puzzle)
            assertEquals("drill ${drill.id} (${drill.technique.value})", drill.targetSteps, counts[drill.technique])
        }
    }

    @Test
    fun `every technique the catalog lists has a puzzle on offer across the drill sources`() {
        val trainer = TangoTrainerFixtures.trainer
        for (technique in trainer.catalog()) {
            val offered = trainer.drillSources.sumOf { it.candidates(technique.id).size }
            assertTrue("no drill source offers ${technique.id.value}", offered > 0)
        }
    }
}
