package com.ivandsky.puzzlebox.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Undo/redo mechanics over a toy board (a string): the contract every game's commands lean on. */
class CommandHistoryTest {

    private data class Append(val text: String) : Command<String> {
        override fun apply(state: String) = state + text
        override fun revert(state: String) = state.removeSuffix(text)
    }

    private fun run(vararg texts: String): Pair<String, CommandHistory<String, Append>> {
        var board = ""
        var history = CommandHistory<String, Append>()
        for (t in texts) {
            val c = Append(t)
            board = c.apply(board)
            history = history.record(c)
        }
        return board to history
    }

    @Test
    fun `undo reverts the newest command and enables redo`() {
        val (board, history) = run("a", "b", "c")

        val step = history.undo(board)!!

        assertEquals("ab", step.state)
        assertTrue(step.history.canRedo)
        assertEquals(listOf(Append("a"), Append("b")), step.history.done)
    }

    @Test
    fun `redo re-applies in the original order`() {
        val (board, history) = run("a", "b", "c")
        val twice = history.undo(board)!!.let { it.history.undo(it.state)!! }
        val redone = twice.history.redo(twice.state)!!

        assertEquals("a", twice.state)
        assertEquals("ab", redone.state)
        assertEquals(listOf(Append("a"), Append("b")), redone.history.done)
        assertEquals(listOf(Append("c")), redone.history.undone)
    }

    @Test
    fun `a new command forks the timeline and drops redo`() {
        val (board, history) = run("a", "b")
        val undone = history.undo(board)!!

        val forked = undone.history.record(Append("x"))

        assertFalse(forked.canRedo)
        assertEquals(listOf(Append("a"), Append("x")), forked.done)
    }

    @Test
    fun `empty stacks yield null, never a partial step`() {
        val empty = CommandHistory<String, Append>()

        assertFalse(empty.canUndo)
        assertFalse(empty.canRedo)
        assertNull(empty.undo(""))
        assertNull(empty.redo(""))
    }

    @Test
    fun `undo all then redo all is the identity on board and history`() {
        val (board, history) = run("a", "b", "c", "d")

        var state = board
        var h = history
        while (h.canUndo) h.undo(state)!!.also { state = it.state; h = it.history }
        assertEquals("", state)
        while (h.canRedo) h.redo(state)!!.also { state = it.state; h = it.history }

        assertEquals(board, state)
        assertEquals(history, h)
    }
}
