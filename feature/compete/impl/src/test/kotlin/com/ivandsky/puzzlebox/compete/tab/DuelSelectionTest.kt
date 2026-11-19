package com.ivandsky.puzzlebox.compete.tab

import com.ivandsky.puzzlebox.compete.domain.DuelRow
import com.ivandsky.puzzlebox.compete.domain.DuelRowKind
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which duel the tablet's pane shows: the first that needs the player, else the latest running, and a pick until it leaves. */
class DuelSelectionTest {

    private fun row(code: String, kind: DuelRowKind, deadline: String = "2026-11-10T12:00:00Z") = DuelRow(
        code = checkNotNull(ShareCode.parse(code)),
        opponent = Player(uid = "uid-$code", name = "Sam", photoUrl = null),
        kind = kind,
        legsDone = 1,
        theirDone = 1,
        legs = 3,
        deadline = Instant.parse(deadline),
    )

    private fun code(value: String) = checkNotNull(ShareCode.parse(value))

    @Test
    fun `the first duel that needs the player wins`() {
        val rows = listOf(
            row("WTG222", DuelRowKind.Waiting, "2026-11-20T12:00:00Z"),
            row("YMV222", DuelRowKind.YourMove),
            row("ABC222", DuelRowKind.Invited),
        )

        assertEquals(code("YMV222"), defaultSelection(rows))
    }

    @Test
    fun `an invite, a move and a reveal each count as needing the player`() {
        listOf(DuelRowKind.Invited, DuelRowKind.YourMove, DuelRowKind.RevealReady).forEach { kind ->
            val rows = listOf(row("WTG222", DuelRowKind.Waiting, "2026-11-20T12:00:00Z"), row("NED222", kind))

            assertEquals(kind.toString(), code("NED222"), defaultSelection(rows))
        }
    }

    @Test
    fun `with none needing the player the running duel with the latest deadline wins`() {
        val rows = listOf(
            row("OLD222", DuelRowKind.Waiting, "2026-11-10T12:00:00Z"),
            row("NEW222", DuelRowKind.Unclaimed, "2026-11-12T12:00:00Z"),
            row("FNS222", DuelRowKind.Finished(2, 1), "2026-11-30T12:00:00Z"),
        )

        assertEquals(code("NEW222"), defaultSelection(rows))
    }

    @Test
    fun `with only finished duels the first row wins`() {
        val rows = listOf(row("FNS222", DuelRowKind.Finished(2, 1)), row("FNS333", DuelRowKind.Finished(0, 3)))

        assertEquals(code("FNS222"), defaultSelection(rows))
    }

    @Test
    fun `no rows select nothing`() {
        assertNull(defaultSelection(emptyList()))
        assertNull(resolveSelection(emptyList(), code("YMV222")))
    }

    @Test
    fun `a pick holds while its duel is listed, even when another now needs the player`() {
        val rows = listOf(row("YMV222", DuelRowKind.YourMove), row("WTG222", DuelRowKind.Waiting))

        assertEquals(code("WTG222"), resolveSelection(rows, code("WTG222")))
    }

    @Test
    fun `a pick whose duel left the list falls back to the default`() {
        val rows = listOf(row("YMV222", DuelRowKind.YourMove))

        assertEquals(code("YMV222"), resolveSelection(rows, code("GON222")))
    }
}
