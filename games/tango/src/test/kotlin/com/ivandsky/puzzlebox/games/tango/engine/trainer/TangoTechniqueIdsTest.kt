package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Trainer ids are wire and index keys (spec data-3): pinned so a rename or reorder fails here. */
class TangoTechniqueIdsTest {

    private val ids = TangoStepRung.entries.map { it.trainerId.value }

    @Test
    fun `the nine technique ids follow the stepper order`() {
        assertEquals(
            listOf(
                "sign", "pair", "sandwich", "full-line", "equal-pair",
                "sign-count", "split-pair", "count-squeeze", "line-options",
            ),
            ids,
        )
    }

    @Test
    fun `ids are unique and fit the stored key shape`() {
        assertEquals(ids, ids.distinct())
        val shape = Regex("^[a-z0-9-]{1,40}$")
        ids.forEach { assertTrue(it, shape.matches(it)) }
    }
}
