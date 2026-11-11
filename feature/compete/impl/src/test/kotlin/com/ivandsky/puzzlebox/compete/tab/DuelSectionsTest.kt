package com.ivandsky.puzzlebox.compete.tab

import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.domain.DuelRow
import com.ivandsky.puzzlebox.domain.DuelRowKind
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the Duels list groups its rows: the sections, what they leave out, the Finished tally, and the sub line. */
class DuelSectionsTest {

    private fun row(code: String, kind: DuelRowKind, done: Int = 0, theirDone: Int = 0, legs: Int = 3) = DuelRow(
        code = checkNotNull(ShareCode.parse(code)),
        opponent = Player(uid = "uid-$code", name = "Sam", photoUrl = null),
        kind = kind,
        legsDone = done,
        theirDone = theirDone,
        legs = legs,
        deadline = Instant.parse("2026-11-10T12:00:00Z"),
    )

    private fun finished(code: String, mine: Int?, theirs: Int?) = row(code, DuelRowKind.Finished(mine, theirs))

    @Test
    fun `each kind lands in its section, in section order`() {
        val rows = listOf(
            finished("FNS222", 2, 1),
            row("WTG222", DuelRowKind.Waiting),
            row("RVW222", DuelRowKind.RevealReady),
            row("EMP222", DuelRowKind.Unclaimed),
            row("YMV222", DuelRowKind.YourMove),
            row("ABC222", DuelRowKind.Invited),
        )

        val sections = sectionRows(rows)

        assertEquals(DuelSection.entries.toList(), sections.map { it.section })
        assertEquals(
            listOf(
                listOf("ABC222"),
                listOf("YMV222"),
                listOf("RVW222"),
                listOf("WTG222", "EMP222"),
                listOf("FNS222"),
            ),
            sections.map { group -> group.rows.map { it.code.value } },
        )
    }

    @Test
    fun `a section with no row is left out`() {
        val sections = sectionRows(listOf(row("YMV222", DuelRowKind.YourMove), finished("FNS222", 1, 0)))

        assertEquals(listOf(DuelSection.YourMove, DuelSection.Finished), sections.map { it.section })
        assertTrue(sectionRows(emptyList()).isEmpty())
    }

    @Test
    fun `the tally counts won, lost and drawn finished duels and skips rows without scores`() {
        val tally = tallyOf(
            listOf(
                finished("FNS222", 2, 1),
                finished("FNS333", 3, 0),
                finished("FNS444", 0, 2),
                finished("FNS555", 1, 1),
                finished("FNS666", null, null),
                row("YMV222", DuelRowKind.YourMove),
            ),
        )

        assertEquals(DuelTally(won = 2, lost = 1, drawn = 1), tally)
        assertTrue(tallyOf(listOf(finished("FNS222", null, null))).isEmpty)
    }

    @Test
    fun `a match in play counts both sides' puzzles in its sub line`() {
        assertEquals("Best of 3 · You 1/3 · Them 2/3", duelSub(row("YMV222", DuelRowKind.YourMove, done = 1, theirDone = 2)))
        assertEquals("Best of 5 · You 5/5 · Them 0/5", duelSub(row("WTG222", DuelRowKind.Waiting, done = 5, legs = 5)))
        assertEquals("Best of 3 · both finished", duelSub(row("RVW222", DuelRowKind.RevealReady, done = 3, theirDone = 3)))
        assertEquals("Best of 3", duelSub(finished("FNS222", 2, 1)))
        assertEquals("You 1/3 · Them 2/3", duelCounts(row("YMV222", DuelRowKind.YourMove, done = 1, theirDone = 2)))
    }
}
