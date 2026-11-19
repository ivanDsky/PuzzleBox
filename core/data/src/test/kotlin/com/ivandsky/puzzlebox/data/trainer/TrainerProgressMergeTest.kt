package com.ivandsky.puzzlebox.data.trainer

import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress.Mark
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainerProgressMergeTest {

    private val x = TechniqueId("x")
    private val y = TechniqueId("y")
    private val z = TechniqueId("z")

    private fun p(mark: Mark, row: Int, at: Long) = TechniqueProgress(mark, row, at)

    private val maps = listOf(
        emptyMap(),
        mapOf(x to p(Mark.Practicing, 2, 10)),
        mapOf(x to p(Mark.Mastered, 5, 5)),
        mapOf(x to p(Mark.Practicing, 3, 10)),
        mapOf(x to p(Mark.Practicing, 1, 20), y to p(Mark.New, 0, 0)),
        mapOf(y to p(Mark.Mastered, 6, 7), z to p(Mark.Practicing, 1, 1)),
    )

    @Test
    fun `merge is commutative including ties`() {
        for (a in maps) for (b in maps) {
            assertEquals("$a $b", TrainerProgressMerge.merge(a, b), TrainerProgressMerge.merge(b, a))
        }
    }

    @Test
    fun `merge is idempotent`() {
        for (a in maps) assertEquals(a, TrainerProgressMerge.merge(a, a))
    }

    @Test
    fun `merge is associative`() {
        for (a in maps) for (b in maps) for (c in maps) {
            assertEquals(
                TrainerProgressMerge.merge(TrainerProgressMerge.merge(a, b), c),
                TrainerProgressMerge.merge(a, TrainerProgressMerge.merge(b, c)),
            )
        }
    }

    @Test
    fun `the higher mark wins while inRow follows the later answer`() {
        val older = mapOf(x to p(Mark.Mastered, 5, 5))
        val newer = mapOf(x to p(Mark.Practicing, 1, 20))
        assertEquals(mapOf(x to p(Mark.Mastered, 1, 20)), TrainerProgressMerge.merge(older, newer))
    }

    @Test
    fun `a tie takes the higher inRow`() {
        val merged = TrainerProgressMerge.merge(
            mapOf(x to p(Mark.Practicing, 2, 10)),
            mapOf(x to p(Mark.Practicing, 3, 10)),
        )
        assertEquals(mapOf(x to p(Mark.Practicing, 3, 10)), merged)
    }

    @Test
    fun `an id on one side only is kept`() {
        val a = mapOf(x to p(Mark.Practicing, 2, 10))
        val b = mapOf(y to p(Mark.Mastered, 5, 3))
        assertEquals(a + b, TrainerProgressMerge.merge(a, b))
    }
}
