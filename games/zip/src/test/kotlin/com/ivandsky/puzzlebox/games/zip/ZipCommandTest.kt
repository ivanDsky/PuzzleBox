package com.ivandsky.puzzlebox.games.zip

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Command contract for Zip: revert(apply(s)) == s on every state a command is built against. */
class ZipCommandTest {

    private val solution = ZipTestSupport.n5Solution

    @Test
    fun `extend reverts to the path it extended`() {
        listOf(emptyList(), solution.take(1), solution.take(12), solution.take(24)).forEach { path ->
            val command = ZipCommand.Extend(solution[path.size])
            val applied = command.apply(path)
            assertEquals(path + solution[path.size], applied)
            assertEquals("size=${path.size}", path, command.revert(applied))
        }
    }

    @Test
    fun `retractTo reverts to the path with its removed tail back`() {
        val path = solution.take(10)
        // drag-back targets: one cell off the head, mid-trace, and the first cell
        listOf(8, 4, 0).forEach { targetIdx ->
            val command = ZipCommand.RetractTo(path[targetIdx], removedTail = path.drop(targetIdx + 1))
            val applied = command.apply(path)
            assertEquals(path.take(targetIdx + 1), applied)
            assertEquals("target=$targetIdx", path, command.revert(applied))
        }
    }

    @Test
    fun `reset reverts to the cleared path`() {
        listOf(solution.take(1), solution.take(7), solution).forEach { path ->
            val command = ZipCommand.Reset(removedPath = path)
            val applied = command.apply(path)
            assertEquals(emptyList<Cell>(), applied)
            assertEquals("size=${path.size}", path, command.revert(applied))
        }
    }
}
