package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tango draft history on disk: the frozen v1 bytes and the commands the codec stores by tag. */
class TangoDraftCommandTest {

    private val blank = TangoDraft(6, emptyMap(), emptyList())
    private val seeded = TangoDraft(
        6,
        givens = mapOf(0 to TangoCell.SUN, 35 to TangoCell.MOON),
        markers = listOf(
            TangoMarker(2, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
            TangoMarker(7, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
        ),
    )

    private fun fixture(name: String): ByteArray =
        TangoDraftCommandTest::class.java.getResourceAsStream("/fixtures/$name")!!.use { it.readBytes() }

    private fun decodeOk(bytes: ByteArray): DraftState<TangoDraft> = (TangoDraftCodec.decode(bytes) as DecodeResult.Ok).value

    /** What `tango-draft-v1.bin` holds: two replace steps done, one undone, markers in (cell, side) order. */
    private fun fixtureState(): DraftState<TangoDraft> {
        val one = TangoDraft(6, mapOf(0 to TangoCell.SUN), emptyList())
        val board = TangoDraft(
            6,
            mapOf(0 to TangoCell.SUN, 35 to TangoCell.MOON),
            listOf(TangoMarker(2, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE), TangoMarker(7, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)),
        )
        val redo = ReplaceDraft(board, board.copy(markers = board.markers.drop(1)))
        return DraftState(board, CommandHistory(listOf(ReplaceDraft(blank, one), ReplaceDraft(one, board)), listOf(redo)))
    }

    @Test
    fun `frozen v1 fixture decodes to the expected draft and both stacks`() {
        val decoded = decodeOk(fixture("tango-draft-v1.bin"))

        assertEquals(fixtureState(), decoded)
        assertTrue(decoded.history.done.all { it is ReplaceDraft<*> })
        assertTrue(decoded.history.undone.single() is ReplaceDraft<*>)
    }

    @Test
    fun `encoding the fixture state reproduces the frozen bytes`() {
        assertArrayEquals(fixture("tango-draft-v1.bin"), TangoDraftCodec.encode(fixtureState()))
    }

    @Test
    fun `cell cycles empty to sun to moon to empty`() {
        val sun = blank.cycleCell(4)
        assertEquals(TangoDraftCommand.SetCell(4, TangoCell.SUN, null), sun)
        val afterSun = sun.apply(blank)
        val moon = afterSun.cycleCell(4)
        assertEquals(TangoDraftCommand.SetCell(4, TangoCell.MOON, TangoCell.SUN), moon)
        val afterMoon = moon.apply(afterSun)
        val cleared = afterMoon.cycleCell(4)
        assertEquals(TangoDraftCommand.SetCell(4, null, TangoCell.MOON), cleared)
        assertNull(cleared.apply(afterMoon).givens[4])
    }

    @Test
    fun `marker cycles none to same to opposite to none`() {
        val same = blank.cycleMarker(8, TangoEdgeSide.RIGHT)
        assertEquals(TangoDraftCommand.SetMarker(8, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME, null), same)
        val afterSame = same.apply(blank)
        assertEquals(listOf(TangoMarker(8, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)), afterSame.markers)
        val opposite = afterSame.cycleMarker(8, TangoEdgeSide.RIGHT)
        assertEquals(TangoDraftCommand.SetMarker(8, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE, TangoMarkerKind.SAME), opposite)
        val afterOpposite = opposite.apply(afterSame)
        assertEquals(listOf(TangoMarker(8, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE)), afterOpposite.markers)
        val cleared = afterOpposite.cycleMarker(8, TangoEdgeSide.RIGHT)
        assertEquals(TangoDraftCommand.SetMarker(8, TangoEdgeSide.RIGHT, null, TangoMarkerKind.OPPOSITE), cleared)
        assertEquals(emptyList<TangoMarker>(), cleared.apply(afterOpposite).markers)
    }

    @Test
    fun `a marker on another edge of the same cell is untouched`() {
        val both = seeded.copy(markers = seeded.markers + TangoMarker(2, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME))

        val next = both.cycleMarker(2, TangoEdgeSide.RIGHT).apply(both)

        assertEquals(TangoMarker(2, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE), next.markers.first { it.cell == 2 && it.side == TangoEdgeSide.DOWN })
        assertEquals(TangoMarkerKind.OPPOSITE, next.markers.first { it.cell == 2 && it.side == TangoEdgeSide.RIGHT }.kind)
    }

    @Test
    fun `revert undoes apply for every cell and marker cycle step`() {
        for (draft in listOf(blank, seeded)) {
            for (cell in listOf(0, 7, 35)) {
                var current = draft
                repeat(3) {
                    val command = current.cycleCell(cell)
                    val applied = command.apply(current)
                    assertEquals(current, command.revert(applied))
                    current = applied
                }
            }
            for (side in TangoEdgeSide.entries) {
                var current = draft
                repeat(3) {
                    val command = current.cycleMarker(2, side)
                    val applied = command.apply(current)
                    assertEquals(current, command.revert(applied))
                    current = applied
                }
            }
        }
    }

    @Test
    fun `history of both commands round-trips through the codec`() {
        val cell = blank.cycleCell(3)
        val afterCell = cell.apply(blank)
        val marker = afterCell.cycleMarker(3, TangoEdgeSide.RIGHT)
        val afterMarker = marker.apply(afterCell)
        val replace = ReplaceDraft(afterMarker, seeded)
        val redo = afterMarker.cycleCell(9)
        val state = DraftState(afterMarker, CommandHistory(listOf(cell, marker, replace), listOf(redo)))

        val decoded = decodeOk(TangoDraftCodec.encode(state))

        assertEquals(state, decoded)
        val undone = decoded.history.undo(decoded.draft)!!.let { it.history.undo(it.state)!! }
        assertEquals(afterCell, undone.state)
    }

    @Test
    fun `a ReplaceDraft-only history still decodes`() {
        val state = DraftState(seeded, CommandHistory(listOf(ReplaceDraft(blank, seeded))))
        assertEquals(DecodeResult.Ok(state), TangoDraftCodec.decode(TangoDraftCodec.encode(state)))
    }

    @Test
    fun `off-board or unknown-byte command steps are Malformed`() {
        val cellStep = TangoDraftCodec.encode(DraftState(blank, CommandHistory(listOf(blank.cycleCell(3)))))
        val markerStep = TangoDraftCodec.encode(DraftState(blank, CommandHistory(listOf(blank.cycleMarker(3, TangoEdgeSide.RIGHT)))))
        // Layout: version, 2 empty-board bytes, u16 count, tag, then the step's fields.
        val cellAt = 1 + 2 + 2 + 1
        val markerAt = cellAt

        listOf(
            cellStep.copyOf().also { it[cellAt] = 36 },
            cellStep.copyOf().also { it[cellAt + 1] = 3 },
            markerStep.copyOf().also { it[markerAt] = 5 },
            markerStep.copyOf().also { it[markerAt + 2] = 2 },
        ).forEach { bytes ->
            val result = TangoDraftCodec.decode(bytes)
            assertTrue("expected Malformed, got $result", result is DecodeResult.Err && result.error is DecodeError.Malformed)
        }
    }

    @Test
    fun `removing a middle marker reverts to the same marker order`() {
        val three = seeded.copy(markers = seeded.markers + TangoMarker(20, TangoEdgeSide.DOWN, TangoMarkerKind.SAME))
        val toOpposite = three.cycleMarker(7, TangoEdgeSide.RIGHT).apply(three)
        val removal = toOpposite.cycleMarker(7, TangoEdgeSide.RIGHT)
        val cleared = removal.apply(toOpposite)

        assertEquals(listOf(three.markers[0], three.markers[2]), cleared.markers)
        assertEquals(toOpposite, removal.revert(cleared))
    }

    @Test
    fun `a new marker lands in cell then side order`() {
        val next = seeded.cycleMarker(3, TangoEdgeSide.DOWN).apply(seeded)

        assertEquals(listOf(2, 3, 7), next.markers.map { it.cell })
    }

    /** `[VERSION][0 givens][markers…][0 done][0 undone]`, each marker `(cell, side, kind)`. */
    private fun bytesWithMarkers(vararg markers: Triple<Int, Int, Int>): ByteArray =
        byteArrayOf(1, 0, markers.size.toByte()) +
            markers.flatMap { listOf(it.first.toByte(), it.second.toByte(), it.third.toByte()) } +
            byteArrayOf(0, 0, 0, 0)

    @Test
    fun `v1 bytes holding unordered markers decode in cell then side order`() {
        val decoded = decodeOk(bytesWithMarkers(Triple(7, 0, 0), Triple(2, 1, 1), Triple(2, 0, 0)))

        assertEquals(
            listOf(
                TangoMarker(2, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
                TangoMarker(2, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
                TangoMarker(7, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
            ),
            decoded.draft.markers,
        )
    }

    @Test
    fun `v1 bytes repeating an edge keep its last kind`() {
        val decoded = decodeOk(bytesWithMarkers(Triple(2, 0, 0), Triple(2, 0, 1)))

        assertEquals(listOf(TangoMarker(2, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE)), decoded.draft.markers)
    }

    @Test
    fun `revert undoes apply on every shuffled-marker draft a decode can produce`() {
        val edges = (0 until 35).flatMap { cell -> TangoEdgeSide.entries.map { cell to it } }
            .filter { (cell, side) -> if (side == TangoEdgeSide.RIGHT) cell % 6 < 5 else cell / 6 < 5 }
        val random = java.util.Random(7)
        val drafts = listOf(fixtureState().draft) + List(40) {
            val markers = edges.shuffled(random).take(random.nextInt(9)).map { (cell, side) ->
                TangoMarker(cell, side, TangoMarkerKind.entries[random.nextInt(2)])
            }
            decodeOk(TangoDraftCodec.encode(DraftState(TangoDraft(6, emptyMap(), markers)))).draft
        }
        for (draft in drafts) {
            for ((cell, side) in edges.shuffled(random).take(6)) {
                var current = draft
                repeat(3) {
                    val command = current.cycleMarker(cell, side)
                    val applied = command.apply(current)
                    assertEquals(current, command.revert(applied))
                    current = applied
                }
            }
        }
    }
}
