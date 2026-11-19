package com.ivandsky.puzzlebox.games.queens.engine.trainer

import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.AttackRung
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.LookAheadRung
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Trainer ids are wire and index keys (spec data-3): pinned so a rename or reorder fails here. */
class QueensTechniqueIdsTest {

    private val ids = QueensLadder.Stepwise.rungs.mapNotNull { it.trainerId?.value }

    @Test
    fun `the ten technique ids follow the Stepwise order`() {
        assertEquals(
            listOf(
                "singles", "line-lock", "shared-squares", "pigeonhole-regions", "pigeonhole-lines",
                "leftover-queen", "squeeze", "count-squeeze", "short-look-ahead", "long-look-ahead",
            ),
            ids,
        )
    }

    @Test
    fun `attack and the legacy look-ahead are no techniques`() {
        assertNull(AttackRung.trainerId)
        val legacy = QueensLadder.Default.rungs.last()
        assertTrue("$legacy", legacy is LookAheadRung)
        assertNull(legacy.trainerId)
        assertEquals(listOf(AttackRung), QueensLadder.Stepwise.rungs.filter { it.trainerId == null })
    }

    @Test
    fun `ids are unique and fit the stored key shape`() {
        assertEquals(ids, ids.distinct())
        val shape = Regex("^[a-z0-9-]{1,40}$")
        ids.forEach { assertTrue(it, shape.matches(it)) }
    }
}
