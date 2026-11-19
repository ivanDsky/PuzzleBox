package com.ivandsky.puzzlebox.compete.tab

import com.ivandsky.puzzlebox.compete.domain.DuelRow
import com.ivandsky.puzzlebox.compete.domain.DuelRowKind
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** The sub-line a Past you row reads, the one thing in it that differs from a friend row's. */
class SelfDuelRowTest {

    private fun row(kind: DuelRowKind, done: Int, legs: Int) = DuelRow(
        code = checkNotNull(ShareCode.parse("K7Q2MX")),
        opponent = null,
        kind = kind,
        legsDone = done,
        theirDone = legs,
        legs = legs,
        deadline = Instant.EPOCH,
        isSelf = true,
    )

    @Test
    fun `a duel in play says how many of mine are played`() =
        assertEquals("You 1/3 · Best of 3", selfDuelSub(row(DuelRowKind.YourMove, 1, 3)))

    @Test
    fun `results ready says all of mine are played`() =
        assertEquals("You 3/3 · Best of 3", selfDuelSub(row(DuelRowKind.RevealReady, 3, 3)))

    @Test
    fun `a won duel reads You won with my score first`() =
        assertEquals("You won 2–1 · Best of 3", selfDuelSub(row(DuelRowKind.Finished(2, 1), 3, 3)))

    @Test
    fun `a lost duel reads You lost`() =
        assertEquals("You lost 1–2 · Best of 3", selfDuelSub(row(DuelRowKind.Finished(1, 2), 3, 3)))

    @Test
    fun `a drawn duel reads You drew`() =
        assertEquals("You drew 1–1 · Best of 2", selfDuelSub(row(DuelRowKind.Finished(1, 1), 2, 2)))

    @Test
    fun `a finished duel without kept scores shows only the best of`() =
        assertEquals("Best of 3", selfDuelSub(row(DuelRowKind.Finished(null, null), 3, 3)))
}
