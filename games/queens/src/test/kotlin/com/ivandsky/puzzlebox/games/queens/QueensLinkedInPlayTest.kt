package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlay
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlayValue
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSolve
import com.ivandsky.puzzlebox.game.replay.MoveKind
import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.ReplayRecord
import com.ivandsky.puzzlebox.game.replay.StampedMove
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.model.SolveReplay
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The Queens LinkedIn encoder on the captured #930 daily, directly and through [queensPlugin]'s replay fold. */
class QueensLinkedInPlayTest {

    private val page: String = checkNotNull(QueensLinkedInPlayTest::class.java.getResourceAsStream("/fixtures/queens-page-daily-unsolved.rsc"))
        .bufferedReader().readText()

    private val size = 7
    private val regions: List<Int> = Regex("\"colors\":\\[([0-9,]+)]").findAll(page).flatMap { row -> row.groupValues[1].split(',').map(String::toInt) }.toList()

    /** The captured win's seven queens, in the order the player placed the first three: cells 1, 16, 31. */
    private val placementOrder = listOf(1, 16, 31, 13, 26, 35, 46)

    private val puzzle = QueensPuzzle(size, regions, solution = placementOrder.sorted().map(::Cell))
    private val envelope = Puzzle(QueensDefinition.id, queensVariantId(size), QueensCodec.VERSION, QueensCodec.encode(puzzle))
    private val initial = QueensSession(ref = null, puzzle = puzzle)

    private fun boardsOf(vararg cells: Int): List<QueensSession> =
        cells.scan(initial) { session, idx -> session.withQueenPlaced(Cell(idx), Instant.EPOCH) }.drop(1)

    private fun solve(
        boards: List<QueensSession>,
        kinds: List<MoveKind> = boards.map { MoveKind.Normal },
        firstMoveMs: Long = 1_000,
    ) = LinkedInSolve(boards, kinds, listOf(firstMoveMs) + List(boards.size - 1) { firstMoveMs + 1_000L * (it + 1) })

    private fun encode(boards: List<QueensSession>, kinds: List<MoveKind> = boards.map { MoveKind.Normal }): LinkedInPlay =
        checkNotNull(QueensLinkedInPlay.encode(solve(boards, kinds)))

    private fun LinkedInPlay.strings(key: String): List<String> = (states.getValue(key) as LinkedInPlayValue.Strings).values

    private fun solveOf(vararg moves: QueensReplayMove): SolveReplay {
        val stamped = moves.mapIndexed { i, move -> StampedMove(1_000L * (i + 1), move.toReplayMove()) }
        val stored = checkNotNull(ReplayCodec.encode(ReplayRecord(QueensDefinition.id, ReplayOutcome.Solved, 80_000, stamped)))
        return checkNotNull(SolveReplay.of(stored))
    }

    private fun places(vararg cells: Int) = cells.map { QueensReplayMove.Place(Cell(it)) }.toTypedArray()

    @Test
    fun `the captured solve gives its solve order and a board with a queen on each of the seven solution cells`() {
        val play = encode(boardsOf(*placementOrder.toIntArray()))

        assertEquals(listOf("orange", "green", "red"), play.strings("queensSolveOrderBinding"))
        val board = play.strings("queensBoardStateBinding")
        assertEquals(size * size, board.size)
        assertEquals(placementOrder.sorted(), board.indices.filter { board[it] == "QUEEN" })
        assertEquals(setOf("queensBoardStateBinding", "queensSolveOrderBinding"), play.states.keys)
        assertEquals(mapOf("playRecord" to "queensBoardStateBinding", "solveOrder" to "queensSolveOrderBinding"), play.payloadKeys)
        assertEquals(true, play.mistakeFree)
    }

    @Test
    fun `a manual cross is CROSS on the board and a cell with no mark stays EMPTY`() {
        val bare = initial.withAutoCross(false).withCrossToggled(Cell(0), Instant.EPOCH)
        val boards = placementOrder.scan(bare) { session, idx -> session.withQueenPlaced(Cell(idx), Instant.EPOCH) }.drop(1)

        val board = encode(boards).strings("queensBoardStateBinding")

        assertEquals("CROSS", board[0])
        assertEquals(placementOrder.sorted().toSet(), board.indices.filter { board[it] == "QUEEN" }.toSet())
        assertEquals(size * size - 1 - placementOrder.size, board.count { it == "EMPTY" })
        assertEquals(1, board.count { it == "CROSS" })
    }

    @Test
    fun `the solve order is the first three queens placed on a solution cell, never more`() {
        val play = encode(boardsOf(*placementOrder.reversed().toIntArray()))

        assertEquals(listOf(colourOf(46), colourOf(35), colourOf(26)), play.strings("queensSolveOrderBinding"))
        assertEquals(3, play.strings("queensSolveOrderBinding").size)
    }

    @Test
    fun `a queen off the solution that was removed does not enter the solve order`() {
        val stray = 0
        val withStray = initial.withQueenPlaced(Cell(stray), Instant.EPOCH)
        val removed = withStray.withQueenRemoved(Cell(stray), Instant.EPOCH)
        val rest = placementOrder.scan(removed) { session, idx -> session.withQueenPlaced(Cell(idx), Instant.EPOCH) }.drop(1)

        val play = encode(listOf(withStray, removed) + rest)

        assertEquals(listOf("orange", "green", "red"), play.strings("queensSolveOrderBinding"))
    }

    @Test
    fun `a cell queened again after a removal is not counted twice`() {
        val a = initial.withQueenPlaced(Cell(1), Instant.EPOCH)
        val removed = a.withQueenRemoved(Cell(1), Instant.EPOCH)
        val b = removed.withQueenPlaced(Cell(16), Instant.EPOCH)
        val again = b.withQueenPlaced(Cell(1), Instant.EPOCH)
        val c = again.withQueenPlaced(Cell(31), Instant.EPOCH)
        val rest = listOf(13, 26, 35, 46).scan(c) { session, idx -> session.withQueenPlaced(Cell(idx), Instant.EPOCH) }.drop(1)

        val play = encode(listOf(a, removed, b, again, c) + rest)

        assertEquals(listOf("orange", "green", "red"), play.strings("queensSolveOrderBinding"))
    }

    @Test
    fun `a mistake, a hint or an undo in the solve is not mistake-free`() {
        val boards = boardsOf(*placementOrder.toIntArray())
        for (flaw in listOf(MoveKind.Mistake, MoveKind.Hint, MoveKind.Undo)) {
            val kinds = boards.map { MoveKind.Normal }.toMutableList().also { it[2] = flaw }

            assertEquals("$flaw", false, encode(boards, kinds).mistakeFree)
        }
    }

    @Test
    fun `the opening is an empty board and an empty solve order`() {
        val opening = checkNotNull(encode(boardsOf(*placementOrder.toIntArray())).opening)

        assertEquals(
            mapOf(
                "queensBoardStateBinding" to LinkedInPlayValue.Strings(List(size * size) { "EMPTY" }),
                "queensSolveOrderBinding" to LinkedInPlayValue.Strings(emptyList()),
            ),
            opening.states,
        )
    }

    @Test
    fun `the opening time is the first move's time, at most one second and at least one millisecond`() {
        fun openingAt(firstMoveMs: Long) =
            QueensLinkedInPlay.encode(solve(boardsOf(*placementOrder.toIntArray()), firstMoveMs = firstMoveMs))?.opening?.timeMs

        assertEquals(1L, openingAt(0))
        assertEquals(400L, openingAt(400))
        assertEquals(1_000L, openingAt(1_000))
        assertEquals(1_000L, openingAt(7_500))
    }

    @Test
    fun `a final board that is not solved gives null`() {
        assertNull(QueensLinkedInPlay.encode(solve(boardsOf(*placementOrder.take(6).toIntArray()))))
    }

    @Test
    fun `a replay of the solve gives the same record through linkedInPlayOf`() {
        val direct = encode(boardsOf(*placementOrder.toIntArray()))

        val viaReplay = queensPlugin.linkedInPlayOf(envelope, solveOf(*places(*placementOrder.toIntArray())))

        assertNotNull(viaReplay)
        assertEquals(direct.states, viaReplay?.states)
        assertEquals(direct.payloadKeys, viaReplay?.payloadKeys)
        assertEquals(true, viaReplay?.mistakeFree)
    }

    @Test
    fun `a hint in the replayed log makes the record not mistake-free`() {
        val moves = arrayOf(QueensReplayMove.Hint, *places(*placementOrder.toIntArray()))

        assertEquals(false, queensPlugin.linkedInPlayOf(envelope, solveOf(*moves))?.mistakeFree)
    }

    @Test
    fun `corrupt bytes give null and never throw`() {
        assertNull(queensPlugin.linkedInPlayOf(envelope, checkNotNull(SolveReplay.of(byteArrayOf(9, 9, 9, 9)))))
    }

    private fun colourOf(cell: Int): String = when (regions[cell]) {
        0 -> "purple"; 1 -> "orange"; 3 -> "green"; 5 -> "red"; 6 -> "yellow"; 2 -> "blue"; 4 -> "gray"
        else -> error("region ${regions[cell]} not in this board")
    }
}
