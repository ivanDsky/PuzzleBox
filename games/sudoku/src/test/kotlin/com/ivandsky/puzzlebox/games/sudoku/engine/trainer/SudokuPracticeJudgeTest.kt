package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.digit
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudoku.engine.rules.SudokuConflicts
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuPracticeJudgement.Reason
import com.ivandsky.puzzlebox.games.sudoku.notesMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuPracticeJudgeTest {

    private val starts = SudokuTrainerFixtures.starts

    private fun SudokuTrainerFixtures.PracticeStart.rung() = checkNotNull(SudokuLadder.easiest(puzzle, board))

    private fun SudokuTrainerFixtures.PracticeStart.judge(move: SudokuTrainerMove) = SudokuPractice.judge(puzzle, board, move)

    /** The squares [step] changes: the placement of a single, each struck note of an elimination. */
    private fun movesOf(step: SudokuStep, size: Int): List<SudokuTrainerMove> = when (step) {
        is SudokuStep.Single -> listOf(SudokuTrainerMove.Place(step.place.cell, step.place.digit))
        is SudokuStep.Elimination -> step.struck.flatMap { (cell, mask) ->
            (1..size).filter { mask and (1 shl (it - 1)) != 0 }.map { SudokuTrainerMove.RemoveNote(cell, it) }
        }
        else -> error("$step")
    }

    @Test
    fun `a move of any deduction of the easiest rung is Right, whichever instance and square the player found`() {
        var otherInstances = 0
        var severalSquares = 0
        for (start in starts) {
            val rung = start.rung()
            if (rung.steps.size > 1) otherInstances++
            for (step in rung.steps) {
                val moves = movesOf(step, start.puzzle.geometry.size)
                if (moves.size > 1) severalSquares++
                for (move in moves) {
                    val judged = start.judge(move)
                    assertTrue("${start.name} ${start.technique.value} $move: $judged", judged is SudokuPracticeJudgement.Right)
                    assertEquals(start.technique, judged.rung)
                    assertTrue("$move is a square of the step that proves it", move in movesOf((judged as SudokuPracticeJudgement.Right).step, start.puzzle.geometry.size))
                }
            }
        }
        assertTrue("some start holds a second instance of its rung", otherInstances > 0)
        assertTrue("some deduction changes several squares", severalSquares > 0)
    }

    @Test
    fun `a move that fits the solution but no easiest-rung deduction makes is Easier, naming that rung`() {
        var placed = 0
        var struck = 0
        for (start in starts) {
            val rung = start.rung()
            val covered = rung.steps.flatMap { movesOf(it, start.puzzle.geometry.size) }.toSet()
            val size = start.puzzle.geometry.size
            val fitting = start.board.cells.indices.filter { start.board.digitAt(it) == 0 }.flatMap { idx ->
                val notes = start.board.cells[idx].notesMask
                listOf<SudokuTrainerMove>(SudokuTrainerMove.Place(idx, start.puzzle.solution[idx])) +
                    (1..size).filter { notes and (1 shl (it - 1)) != 0 && it != start.puzzle.solution[idx] }.map { SudokuTrainerMove.RemoveNote(idx, it) }
            }
            for (move in fitting.filter { it !in covered }) {
                val judged = start.judge(move)
                assertEquals("${start.name} ${start.technique.value} $move", SudokuPracticeJudgement.Easier(start.technique, rung.steps.first()), judged)
                if (move is SudokuTrainerMove.Place) placed++ else struck++
            }
        }
        assertTrue("a placement is Easier on an elimination's board", placed > 0)
        assertTrue("a note removal is Easier on a single's board", struck > 0)
    }

    @Test
    fun `a wrong digit and the solution digit's note taken off are Wrong, and so is anything that is no move`() {
        for (start in starts) {
            val label = "${start.name} ${start.technique.value}"
            val idx = start.board.cells.indices.first { start.board.digitAt(it) == 0 }
            val solution = start.puzzle.solution[idx]
            val wrong = solution % start.puzzle.geometry.size + 1

            assertEquals(label, SudokuPracticeJudgement.Wrong(start.technique, Reason.WrongDigit), start.judge(SudokuTrainerMove.Place(idx, wrong)))
            assertEquals(label, SudokuPracticeJudgement.Wrong(start.technique, Reason.SolutionNote), start.judge(SudokuTrainerMove.RemoveNote(idx, solution)))
            assertEquals(label, SudokuPracticeJudgement.Wrong(start.technique, Reason.NotAMove), start.judge(SudokuTrainerMove.AddNote(idx, wrong)))
            assertEquals(label, SudokuPracticeJudgement.Wrong(start.technique, Reason.NotAMove), start.judge(SudokuTrainerMove.Other))
        }
    }

    @Test
    fun `Show me is the first deduction of the easiest rung, made whole`() {
        for (start in starts) {
            val label = "${start.name} ${start.technique.value}"
            val shown = checkNotNull(SudokuPractice.showMe(start.puzzle, start.board))
            assertEquals(label, SudokuPracticeJudgement.Right(start.technique, start.rung().steps.first()), shown)

            val after = SudokuPractice.commands(start.board, shown.step).fold(start.board) { board, command -> command.apply(board) }
            when (val step = shown.step) {
                is SudokuStep.Single -> {
                    assertEquals(label, step.place.digit, after.digitAt(step.place.cell))
                    assertTrue("$label placed nothing else", start.board.cells.indices.count { after.digitAt(it) != start.board.digitAt(it) } == 1)
                }
                is SudokuStep.Elimination -> after.cells.forEachIndexed { idx, cell ->
                    val struck = step.struck[idx] ?: 0
                    assertEquals("$label cell $idx", start.board.cells[idx].notesMask and struck.inv(), cell.notesMask)
                }
                else -> error("$step")
            }
        }
    }

    @Test
    fun `playing Show me from any start solves the board, every shown step changing it`() {
        for (start in starts) {
            var board: SudokuBoard = start.board
            var turns = 0
            while (!board.isFull) {
                val shown = checkNotNull(SudokuPractice.showMe(start.puzzle, board))
                val commands = SudokuPractice.commands(board, shown.step)
                assertTrue("${start.name}: a shown step changes the board", commands.isNotEmpty())
                board = commands.fold(board) { acc, command -> command.apply(acc) }
                check(++turns < 300) { "${start.name} did not finish" }
            }
            assertTrue("${start.name} ${start.technique.value}", SudokuConflicts.isSolved(board))
            assertEquals(start.puzzle.solution, board.cells.map { it.digit })
        }
    }
}
