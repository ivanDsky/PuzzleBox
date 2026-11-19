package com.ivandsky.puzzlebox.games.zipui.editor

import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The tabs and the "Next: ..." line follow the two-step flow when Numbers & walls is on, and today's three when it is off. */
class ZipEditorCombinedHintTest {

    private val full = SNAKE

    private fun draft(path: List<Int>, marks: List<Int> = listOf(0, 24), walls: Set<Wall> = emptySet()) =
        ZipDraft(5, emptySet(), path, marks).copy(walls = walls)

    private fun next(board: ZipDraft, step: ZipEditorStep) = ZipEditorHintState.of(board, step, combined = true).next

    @Test
    fun `the tabs are Path and Numbers and walls when on, Path Numbers Walls when off`() {
        assertEquals(listOf(ZipEditorStep.Path, ZipEditorStep.NumbersAndWalls), ZipEditorStep.tabs(combined = true))
        assertEquals(
            listOf(ZipEditorStep.Path, ZipEditorStep.Numbers, ZipEditorStep.Walls),
            ZipEditorStep.tabs(combined = false),
        )
        assertEquals("Numbers & walls", ZipEditorStep.NumbersAndWalls.label)
    }

    @Test
    fun `a short path names no next step`() {
        assertNull(next(draft(full.take(10), marks = listOf(0, full[9])), ZipEditorStep.Path))
    }

    @Test
    fun `a covered board with its ends only names Numbers and walls from Path, never from there`() {
        val board = draft(full)

        assertEquals(ZipEditorStep.NumbersAndWalls, next(board, ZipEditorStep.Path))
        assertNull(next(board, ZipEditorStep.NumbersAndWalls))
    }

    @Test
    fun `a middle number alone or walls alone still asks for the other half`() {
        val numbered = draft(full, marks = listOf(0, full[12], 24))
        val walled = draft(full, walls = setOf(Wall(Cell(0), WallDirection.DOWN)))

        assertEquals(ZipEditorStep.NumbersAndWalls, next(numbered, ZipEditorStep.Path))
        assertEquals(ZipEditorStep.NumbersAndWalls, next(walled, ZipEditorStep.Path))
    }

    @Test
    fun `with numbers and walls placed the hint names no next step`() {
        val board = draft(full, marks = listOf(0, full[12], 24), walls = setOf(Wall(Cell(0), WallDirection.DOWN)))

        assertNull(next(board, ZipEditorStep.Path))
    }

    @Test
    fun `off, the three-step hint is the one it always was`() {
        val board = draft(full)

        assertEquals(ZipEditorStep.Numbers, ZipEditorHintState.of(board, ZipEditorStep.Path).next)
        assertEquals(ZipEditorStep.Walls, ZipEditorHintState.of(draft(full, marks = listOf(0, full[12], 24)), ZipEditorStep.Path).next)
    }

    private companion object {
        val SNAKE: List<Int> = (0 until 5).flatMap { row ->
            (if (row % 2 == 0) 0 until 5 else 4 downTo 0).map { row * 5 + it }
        }
    }
}
