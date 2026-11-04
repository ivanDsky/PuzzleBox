package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSolver
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Session codec v3 (command-pattern undo/redo history + banked clock; v2 still reads,
 * history dropped): zip ZipSessionCodecTest conventions — exact round trip (C1)
 * including the command history, pinned layout (C2), determinism (C3), version guard
 * (C5), the v2 read path, and malformed-input rejection.
 */
class QueensSessionCodecTest {

    private val completed = Instant.ofEpochMilli(1_700_000_000_000)

    private val solver = QueensSolver()

    private fun puzzle() = QueensTestSupport.puzzle(8) { r, _ -> r }

    private fun cell(row: Int, col: Int, size: Int = 8) = QueensTestSupport.cell(row, col, size)

    private fun ref() = QueensPuzzleRef(LocalDate.of(2026, 11, 2), 7)

    private val now = Instant.ofEpochMilli(completed.toEpochMilli() - 60_000)

    private fun roundTrip(s: QueensSession): QueensSession =
        (QueensSessionCodec.deserialize(QueensSessionCodec.serialize(s)) as DecodeResult.Ok).value

    private fun fresh() = QueensSession(ref = null, puzzle = puzzle())

    /** A session exercising every persisted feature: ref, queens, MANUAL+AUTO marks, history, completion. */
    private fun session(completedAt: Instant? = completed, ref: QueensPuzzleRef? = ref()): QueensSession {
        val p = puzzle()
        val solution = solver.solve(p) ?: error("fixture board must be solvable")
        var s = QueensSession(ref = ref, puzzle = p)
        s = s.withCrossToggled(cell(3, 3), now) // MANUAL mark outside all queen units of the solution prefix
        s = s.withQueenPlaced(solution[0], now)
        s = s.withQueenPlaced(solution[1], now)
        if (completedAt == null) return s
        for (q in solution.drop(2)) s = s.withQueenPlaced(q, completedAt)
        return s.copy(elapsedMs = 123_456L)
    }

    /** Every command kind in the done stack, a redo entry on top, plus MANUAL and AUTO marks standing. */
    private fun historied(): QueensSession {
        var s = fresh()
        s = s.withCrossToggled(cell(3, 3), now) // ToggleCross
        s = s.withQueenPlaced(cell(3, 3), now) // PlaceQueen over a MANUAL mark
        s = s.withQueenRemoved(cell(3, 3), now) // RemoveQueen that takes a hidden mark with it
        s = s.withQueenPlaced(cell(0, 0), now)
        s = s.withQueenPlaced(cell(1, 2), now)
        s = s.withCrossToggled(cell(6, 6), now)
        s = s.withCleared(now) // Clear
        s = s.withQueenPlaced(cell(5, 5), now)
        return s.withUndo() // the last placement lands on the redo stack
    }

    /** C1: deserialize∘serialize == identity for the persisted shape. */
    @Test
    fun `C1 - round trip preserves queens marks and the command history`() {
        val original = session(completedAt = null)
        assertTrue(original.marks.values.any { it.kind == MarkKind.MANUAL })
        assertTrue(original.marks.values.any { it.kind == MarkKind.AUTO })
        val restored = roundTrip(original)
        assertEquals(original, restored)
        assertEquals(original.history, restored.history)
        for ((c, m) in original.marks) assertEquals(m, restored.markAt(c))
    }

    @Test
    fun `C1 - every command kind and the redo stack survive`() {
        val original = historied()
        assertTrue(original.history.done.any { it is QueensCommand.Clear })
        assertTrue(original.history.done.any { it is QueensCommand.RemoveQueen && it.hadMark })
        assertTrue(original.history.done.any { it is QueensCommand.ToggleCross })
        assertTrue(original.history.done.any { it is QueensCommand.PlaceQueen })
        assertTrue(original.canRedo)

        val restored = roundTrip(original)

        assertEquals(original, restored)
        assertEquals(original.history, restored.history)
        // the restored history is live: undo/redo behave exactly as on the original
        assertEquals(original.withRedo(), restored.withRedo())
        assertEquals(original.withUndo(), restored.withUndo())
    }

    @Test
    fun `C1 - completed sessions round trip with the banked clock`() {
        val original = session()
        assertTrue(original.isComplete)
        val restored = roundTrip(original)
        assertEquals(original.completedAt, restored.completedAt)
        assertEquals(123_456L, restored.elapsedMs)
        assertEquals(original, restored)
    }

    @Test
    fun `C1 - a fresh session round trips`() {
        val restored = roundTrip(fresh())
        assertEquals(fresh(), restored)
        assertNull(restored.ref)
    }

    @Test
    fun `C1 - hintsUsed elapsed and autoCross OFF round trip`() {
        val p = puzzle()
        var s = QueensSession(ref = null, puzzle = p).withAutoCross(false)
        s = s.withCrossToggled(cell(0, 0), now)
        s = s.withHintUsed().withHintUsed()
        s = s.withQueenPlaced(cell(1, 4), now)
        s = s.withHintUsed().copy(elapsedMs = 9_000L)
        val restored = roundTrip(s)
        assertEquals(s, restored)
        assertEquals(3, restored.hintsUsed)
        assertEquals(9_000L, restored.elapsedMs)
        assertEquals(false, restored.autoCross)
        // the default (fresh session) decodes ON
        assertEquals(true, roundTrip(fresh()).autoCross)
    }

    /** C2: the layout, pinned. */
    @Test
    fun `C2 - byte layout matches the pinned table`() {
        val puzzle = puzzle()
        val puzzleBytes = QueensCodec.encode(puzzle)
        val blob = QueensSessionCodec.serialize(QueensSession(ref = null, puzzle = puzzle))
        val expected = buildList {
            add(3) // version
            add((puzzleBytes.size shr 8) and 0xFF); add(puzzleBytes.size and 0xFF)
            addAll(puzzleBytes.map(Byte::toInt))
            add(0) // flags: no completedAt, no ref, autoCross ON
            add(0) // queensLen
            add(0); add(0) // manualLen u16
            add(0); add(0) // doneLen u16
            add(0); add(0) // undoneLen u16
            repeat(8) { add(0) } // elapsedMs i64
            add(0) // hintsUsed
        }.map(Int::toByte).toByteArray()
        assertEquals(3, QueensSessionCodec.VERSION)
        assertArrayEquals(expected, blob)
    }

    /** C3: canonical serialization — same session, same bytes. */
    @Test
    fun `C3 - serialization is deterministic`() {
        val s = historied()
        assertArrayEquals(QueensSessionCodec.serialize(s), QueensSessionCodec.serialize(s))
    }

    @Test
    fun `ref absence is a flag, not a sentinel`() {
        assertNull(roundTrip(session(ref = null)).ref)
    }

    // ------------------------------------------------------------- v2 compatibility

    /** A v2 blob: queens, provenance marks, a snapshot undo stack, completedAt/startedAt, hints. */
    private fun v2Blob(complete: Boolean): ByteArray {
        val puzzleBytes = QueensCodec.encode(puzzle())
        val out = ByteArrayOutputStream()
        fun u16(v: Int) { out.write(v shr 8); out.write(v and 0xFF) }
        fun i64(v: Long) { repeat(8) { out.write(((v shr (it * 8)) and 0xFF).toInt()) } }
        out.write(2)
        u16(puzzleBytes.size)
        out.write(puzzleBytes)
        out.write(if (complete) 1 or 4 else 4) // completedAt?, startedAt
        out.write(1); out.write(0) // one queen at cell (0,0)
        u16(2)
        out.write(1); out.write(1); out.write(0) // cell (0,1): AUTO, sourced by the queen at 0
        out.write(27); out.write(0) // cell (3,3): MANUAL
        u16(1) // one undo snapshot: no queens, only the MANUAL mark
        out.write(0)
        u16(1); out.write(27); out.write(0)
        if (complete) i64(1_700_000_050_000L)
        i64(1_700_000_000_000L) // startedAt
        out.write(2) // hintsUsed
        return out.toByteArray()
    }

    @Test
    fun `v2 saves still read - snapshots dropped, only MANUAL marks kept, AUTO re-derived`() {
        val restored = (QueensSessionCodec.deserialize(v2Blob(complete = false)) as DecodeResult.Ok).value

        assertEquals(listOf(Cell(0)), restored.queens)
        assertEquals(MarkKind.MANUAL, restored.markAt(Cell(27))?.kind)
        assertEquals(MarkKind.AUTO, restored.markAt(Cell(1))?.kind)
        assertEquals(CommandHistory<QueensBoard, QueensCommand>(), restored.history)
        assertEquals(2, restored.hintsUsed)
        assertEquals(0L, restored.elapsedMs) // wall-clock start is meaningless across a restart
        assertNull(restored.completedAt)
    }

    @Test
    fun `v2 completed saves keep their duration`() {
        val restored = (QueensSessionCodec.deserialize(v2Blob(complete = true)) as DecodeResult.Ok).value
        assertTrue(restored.isComplete)
        assertEquals(50_000L, restored.elapsedMs)
        // and re-serializes as v3
        assertEquals(restored, roundTrip(restored))
    }

    @Test
    fun `v1 and future blobs are rejected as UnsupportedVersion`() {
        for (version in listOf(1, 4, 99)) {
            val blob = QueensSessionCodec.serialize(session()).also { it[0] = version.toByte() }
            val err = QueensSessionCodec.deserialize(blob)
            assertTrue("v$version", err is DecodeResult.Err && err.error is DecodeError.UnsupportedVersion)
        }
    }

    // ------------------------------------------------------------------- malformed

    @Test
    fun `malformed - empty blob`() {
        assertTrue(QueensSessionCodec.deserialize(ByteArray(0)) is DecodeResult.Err)
    }

    @Test
    fun `malformed - truncated after the history`() {
        val full = QueensSessionCodec.serialize(historied())
        assertTrue(QueensSessionCodec.deserialize(full.copyOfRange(0, full.size - 3)) is DecodeResult.Err)
    }

    /** A fresh-session blob with [doneTail] spliced in as the done-list (count + commands). */
    private fun withDone(count: Int, vararg commandBytes: Int): ByteArray {
        val base = QueensSessionCodec.serialize(fresh())
        val tailLen = 2 + 2 + 8 + 1 // done, undone, elapsed, hints
        val head = base.copyOf(base.size - tailLen)
        return head + byteArrayOf((count shr 8).toByte(), count.toByte()) +
            commandBytes.map(Int::toByte).toByteArray() +
            ByteArray(2 + 8 + 1)
    }

    private fun isMalformed(blob: ByteArray) =
        (QueensSessionCodec.deserialize(blob) as? DecodeResult.Err)?.error is DecodeError.Malformed

    @Test
    fun `malformed - history commands`() {
        assertTrue("well-formed control", QueensSessionCodec.deserialize(withDone(1, 0, 5)) is DecodeResult.Ok)
        assertTrue("unknown tag", isMalformed(withDone(1, 9, 0)))
        assertTrue("cell outside the 8x8 board", isMalformed(withDone(1, 0, 64)))
        assertTrue("non-boolean hadMark", isMalformed(withDone(1, 1, 5, 0, 2)))
    }

    @Test
    fun `malformed - history past the decode bound`() {
        assertTrue(isMalformed(withDone(QueensSessionCodec.MAX_HISTORY + 1)))
    }

    @Test
    fun `serialize keeps only the newest commands past the cap`() {
        val toggles = List(QueensSessionCodec.MAX_HISTORY + 5) { n ->
            QueensCommand.ToggleCross(Cell(n % 64), wasMarked = n % 2 == 0)
        }
        val restored = roundTrip(fresh().copy(history = CommandHistory(done = toggles)))
        assertEquals(QueensSessionCodec.MAX_HISTORY, restored.history.done.size)
        assertEquals(toggles.takeLast(QueensSessionCodec.MAX_HISTORY), restored.history.done)
    }

    @Test
    fun `malformed - queen cell outside the board`() {
        val s = QueensSession(ref = null, puzzle = puzzle()).withQueenPlaced(cell(1, 1), now)
        val blob = QueensSessionCodec.serialize(s)
        val puzzleLen = ((blob[1].toInt() and 0xFF) shl 8) or (blob[2].toInt() and 0xFF)
        val queenIdxPos = 3 + puzzleLen + 1 /*flags*/ + 1 /*queensLen*/
        assertEquals(9, blob[queenIdxPos].toInt()) // cell (1,1)
        assertTrue(isMalformed(blob.copyOf().also { it[queenIdxPos] = 200.toByte() }))
    }

    @Test
    fun `malformed - v2 AUTO mark sourced by a non-standing queen`() {
        val blob = v2Blob(complete = false)
        val puzzleLen = ((blob[1].toInt() and 0xFF) shl 8) or (blob[2].toInt() and 0xFF)
        // after: version+len(3) puzzle flags queensLen queen marksLen(2) idx kind -> source
        val sourcePos = 3 + puzzleLen + 1 + 1 + 1 + 2 + 2
        assertEquals(0, blob[sourcePos].toInt())
        assertTrue(isMalformed(blob.copyOf().also { it[sourcePos] = 5 }))
    }
}
