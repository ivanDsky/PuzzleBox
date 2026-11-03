package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSolver
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Session codec v2 (fix round 6 adds startedAt/hintsUsed/autoCross; v1 blobs are
 * UnsupportedVersion — pre-release, no shipped saves): zip ZipSessionCodecTest
 * conventions — exact round trip (C1) including mark PROVENANCE and the undo stack,
 * pinned layout (C2), determinism (C3), version guard (C5), and malformed-input
 * rejection for every provenance invariant.
 */
class QueensSessionCodecTest {

    private val completed = Instant.ofEpochMilli(1_700_000_000_000)

    private val solver = QueensSolver()

    private fun puzzle() = QueensTestSupport.puzzle(8) { r, _ -> r }

    private fun cell(row: Int, col: Int, size: Int = 8) = QueensTestSupport.cell(row, col, size)

    private fun ref() = QueensPuzzleRef(LocalDate.of(2026, 11, 2), 7)

    /** A session exercising every persisted feature: ref, 2 queens, MANUAL+AUTO marks, undo depth 3, completion. */
    private fun session(completedAt: Instant? = completed, ref: QueensPuzzleRef? = ref()): QueensSession {
        val p = puzzle()
        val solution = solver.solve(p) ?: error("fixture board must be solvable")
        var s = QueensSession(ref = ref, puzzle = p)
        s = s.withCrossToggled(cell(3, 3), now) // MANUAL mark outside all queen units of the solution prefix
        s = s.withQueenPlaced(solution[0], now)
        s = s.withQueenPlaced(solution[1], now)
        return if (completedAt != null) {
            var full = QueensSession(ref = ref, puzzle = p)
            full = full.withCrossToggled(cell(3, 3), now)
            for (q in solution) full = full.withQueenPlaced(q, now)
            full
        } else {
            s
        }
    }

    private val now = Instant.ofEpochMilli(completed.toEpochMilli() - 60_000)

    /** C1: deserialize∘serialize == identity for the persisted shape. */
    @Test
    fun `C1 - round trip preserves queens marks with provenance and the undo stack`() {
        val original = session(completedAt = null)
        assertTrue(original.marks.values.any { it.kind == MarkKind.MANUAL })
        assertTrue(original.marks.values.any { it.kind == MarkKind.AUTO })
        assertTrue(original.undo.size >= 3)
        val restored = (QueensSessionCodec.deserialize(QueensSessionCodec.serialize(original)) as DecodeResult.Ok).value
        assertEquals(original, restored)
        // provenance is structural, not just equals-accidental
        for ((c, m) in original.marks) assertEquals(m, restored.markAt(c))
        assertEquals(original.undo, restored.undo)
    }

    @Test
    fun `C1 - completed sessions round trip`() {
        val original = session()
        val restored = (QueensSessionCodec.deserialize(QueensSessionCodec.serialize(original)) as DecodeResult.Ok).value
        assertEquals(original.completedAt, restored.completedAt)
        assertEquals(original, restored)
    }

    @Test
    fun `C1 - a fresh session round trips`() {
        val fresh = QueensSession(ref = null, puzzle = puzzle())
        val restored = (QueensSessionCodec.deserialize(QueensSessionCodec.serialize(fresh)) as DecodeResult.Ok).value
        assertEquals(fresh, restored)
        assertNull(restored.ref)
    }

    /** C2: the layout, pinned: version, u16 puzzle length, puzzle bytes, flags, ref, queens, marks, undo, completed, started, hints. */
    @Test
    fun `C2 - byte layout matches the pinned table`() {
        val puzzle = puzzle()
        val puzzleBytes = QueensCodec.encode(puzzle)
        val blob = QueensSessionCodec.serialize(QueensSession(ref = null, puzzle = puzzle))
        val expected = buildList {
            add(2) // version (fix round 6: v2 adds startedAt/hintsUsed/autoCross)
            add((puzzleBytes.size shr 8) and 0xFF); add(puzzleBytes.size and 0xFF)
            addAll(puzzleBytes.map(Byte::toInt))
            add(0) // flags: no completedAt, no ref, no startedAt, autoCross ON
            add(0) // queensLen
            add(0); add(0) // marksLen u16
            add(0); add(0) // undoLen u16
            add(0) // hintsUsed
        }.map(Int::toByte).toByteArray()
        assertEquals(2, QueensSessionCodec.VERSION)
        assertArrayEquals(expected, blob)
    }

    /** Fix round 6: the v2 fields round trip — play clock, hint counter, Auto-cross OFF. */
    @Test
    fun `C1 - v2 fields round trip - startedAt hintsUsed and autoCross`() {
        val p = puzzle()
        var s = QueensSession(ref = null, puzzle = p).withAutoCross(false)
        s = s.withCrossToggled(cell(0, 0), now)
        s = s.withHintUsed().withHintUsed()
        s = s.withQueenPlaced(cell(1, 4), now)
        s = s.withHintUsed()
        val restored = (QueensSessionCodec.deserialize(QueensSessionCodec.serialize(s)) as DecodeResult.Ok).value
        assertEquals(s, restored)
        assertEquals(3, restored.hintsUsed)
        assertEquals(now, restored.startedAt)
        assertEquals(false, restored.autoCross)
    }

    @Test
    fun `C1 - completed sessions round trip with the elapsed clock`() {
        val original = session()
        assertTrue(original.isComplete)
        val restored = (QueensSessionCodec.deserialize(QueensSessionCodec.serialize(original)) as DecodeResult.Ok).value
        assertEquals(original.startedAt, restored.startedAt)
        assertEquals(original.elapsedMs(), restored.elapsedMs())
        assertEquals(original, restored)
    }

    @Test
    fun `autoCross OFF rides a flag, not a sentinel`() {
        val blob = QueensSessionCodec.serialize(session(completedAt = null).withAutoCross(false))
        val restored = (QueensSessionCodec.deserialize(blob) as DecodeResult.Ok).value
        assertEquals(false, restored.autoCross)
        // and the default (fresh v2 session) decodes ON
        val onBlob = QueensSessionCodec.serialize(QueensSession(ref = null, puzzle = puzzle()))
        assertEquals(true, (QueensSessionCodec.deserialize(onBlob) as DecodeResult.Ok).value.autoCross)
    }

    @Test
    fun `v1 blobs are rejected as UnsupportedVersion (pre-release, no migrations)`() {
        // The v2 reader hits byte0=1 where it expects 2 — a stale pre-release save.
        val blob = QueensSessionCodec.serialize(session()).also { it[0] = 1 }
        val err = QueensSessionCodec.deserialize(blob)
        assertTrue(err is DecodeResult.Err && err.error is DecodeError.UnsupportedVersion)
    }

    /** C3: canonical serialization — same session, same bytes. */
    @Test
    fun `C3 - serialization is deterministic`() {
        val s = session()
        assertArrayEquals(QueensSessionCodec.serialize(s), QueensSessionCodec.serialize(s))
    }

    @Test
    fun `ref absence is a flag, not a sentinel`() {
        val blob = QueensSessionCodec.serialize(session(ref = null))
        val restored = (QueensSessionCodec.deserialize(blob) as DecodeResult.Ok).value
        assertNull(restored.ref)
    }

    @Test
    fun `C5 - unknown version is rejected with UnsupportedVersion`() {
        val blob = QueensSessionCodec.serialize(session()).also { it[0] = 99 }
        val err = QueensSessionCodec.deserialize(blob)
        assertTrue(err is DecodeResult.Err && err.error is DecodeError.UnsupportedVersion)
    }

    @Test
    fun `malformed - empty blob`() {
        assertTrue(QueensSessionCodec.deserialize(ByteArray(0)) is DecodeResult.Err)
    }

    @Test
    fun `malformed - truncated after the marks header`() {
        val full = QueensSessionCodec.serialize(session(completedAt = null))
        assertTrue(QueensSessionCodec.deserialize(full.copyOfRange(0, full.size - 3)) is DecodeResult.Err)
    }

    /** Offset of the FIRST mark triple, walking the pinned front layout (v2 tail shifts the end). */
    private fun firstMarkPos(blob: ByteArray): Int {
        val puzzleLen = ((blob[1].toInt() and 0xFF) shl 8) or (blob[2].toInt() and 0xFF)
        val flagsPos = 3 + puzzleLen
        val queensLen = blob[flagsPos + 1].toInt()
        return flagsPos + 2 /* queensLen */ + queensLen /* cells */ + 2 /* marksLen u16 */
    }

    @Test
    fun `malformed - unknown mark kind`() {
        val minimal = QueensSession(ref = null, puzzle = puzzle()).withQueenPlaced(cell(0, 0), now)
        val blob = QueensSessionCodec.serialize(minimal)
        val kindPos = firstMarkPos(blob) + 1
        assertEquals(1, blob[kindPos].toInt()) // kind = AUTO before corruption
        val err = QueensSessionCodec.deserialize(blob.copyOf().also { it[kindPos] = 9 })
        assertTrue(err is DecodeResult.Err && err.error is DecodeError.Malformed)
    }

    @Test
    fun `malformed - AUTO mark sourced by a non-standing queen`() {
        val s = QueensSession(ref = null, puzzle = puzzle()).withQueenPlaced(cell(0, 0), now)
        val blob = QueensSessionCodec.serialize(s)
        val sourcePos = firstMarkPos(blob) + 2
        assertEquals(0, blob[sourcePos].toInt()) // source = the standing queen at cell 0
        val err = QueensSessionCodec.deserialize(blob.copyOf().also { it[sourcePos] = 5 })
        assertTrue(err is DecodeResult.Err && err.error is DecodeError.Malformed)
    }

    @Test
    fun `malformed - queen cell outside the board`() {
        // one-queen session: locate the queen index by walking the pinned layout header
        val s = QueensSession(ref = null, puzzle = puzzle()).withQueenPlaced(cell(1, 1), now)
        val blob = QueensSessionCodec.serialize(s)
        val puzzleLen = ((blob[1].toInt() and 0xFF) shl 8) or (blob[2].toInt() and 0xFF)
        val queenIdxPos = 3 + puzzleLen + 1 /*flags*/ + 1 /*queensLen*/
        assertEquals(9, blob[queenIdxPos].toInt()) // cell (1,1)
        val err = QueensSessionCodec.deserialize(blob.copyOf().also { it[queenIdxPos] = 200.toByte() })
        assertTrue(err is DecodeResult.Err && err.error is DecodeError.Malformed)
    }
}
