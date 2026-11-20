package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrillKeepTest {

    private val single = TechniqueId("single")
    private val pointing = TechniqueId("pointing")
    private val pair = TechniqueId("pair")
    private val reveal = TechniqueId("reveal")
    private val families = mapOf(single to 0, pointing to 1, pair to 2)

    @Test
    fun `a puzzle that never uses the target is not kept`() {
        assertNull(DrillKeep.targetSteps(mapOf(single to 4), pointing, families))
        assertNull(DrillKeep.targetSteps(emptyMap(), pointing, families))
        assertNull(DrillKeep.targetSteps(mapOf(pointing to 0), pointing, families))
    }

    @Test
    fun `the target's steps are the drill's target steps`() {
        assertEquals(3, DrillKeep.targetSteps(mapOf(pointing to 3), pointing, families))
    }

    @Test
    fun `easier techniques beside the target do not matter`() {
        assertEquals(2, DrillKeep.targetSteps(mapOf(single to 9, pointing to 2), pointing, families))
    }

    @Test
    fun `a harder family beside the target rejects a pointing drill`() {
        assertNull(DrillKeep.targetSteps(mapOf(pointing to 2, pair to 1), pointing, families))
    }

    @Test
    fun `the same family may repeat and a pair drill may show pointing`() {
        assertEquals(1, DrillKeep.targetSteps(mapOf(pair to 1, pointing to 5, single to 2), pair, families))
    }

    @Test
    fun `a technique the families do not rank never rejects`() {
        assertEquals(2, DrillKeep.targetSteps(mapOf(pointing to 2, reveal to 7), pointing, families))
    }

    @Test
    fun `a target the families do not rank sets no ceiling`() {
        assertEquals(1, DrillKeep.targetSteps(mapOf(reveal to 1, pair to 3), reveal, families))
        assertEquals(1, DrillKeep.targetSteps(mapOf(reveal to 1, pair to 3), reveal, emptyMap()))
    }
}
