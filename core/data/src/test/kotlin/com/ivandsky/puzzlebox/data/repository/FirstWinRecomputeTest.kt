package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.database.CompletionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The record flag per puzzle ref: the earliest finish wins, ties by sync id; live order and merged order agree. */
class FirstWinRecomputeTest {

    private val recompute = FirstWinRecompute()
    private val policy = FirstWinPolicy()

    private fun row(
        syncId: String,
        at: Long,
        ref: String = "alpha/2026-03-01/1",
        isRecord: Boolean = false,
        first: Long? = if (isRecord) at else null,
    ) = CompletionEntity(
        puzzleRef = ref,
        timeMs = 1_000L,
        hints = 0,
        statsVersion = 1,
        statsBlob = "{}",
        completedAtMs = at,
        firstCompletedAtMs = first,
        isRecord = isRecord,
        ownerUid = "ann",
        syncId = syncId,
    )

    /** The rows after the corrections are applied. */
    private fun settled(rows: List<CompletionEntity>): List<CompletionEntity> {
        val fixes = recompute.corrected(rows).associateBy { it.syncId }
        return rows.map { fixes[it.syncId] ?: it }
    }

    @Test
    fun `the earliest finish becomes the record and later ones play-agains`() {
        val rows = settled(listOf(row("c", 30), row("a", 10), row("b", 20)))

        assertEquals(setOf("a"), rows.filter { it.isRecord }.map { it.syncId }.toSet())
        assertEquals(10L, rows.single { it.syncId == "a" }.firstCompletedAtMs)
        assertTrue(rows.filter { !it.isRecord }.all { it.firstCompletedAtMs == null })
    }

    @Test
    fun `a tie on the finish time goes to the smaller sync id`() {
        val rows = settled(listOf(row("b", 10), row("a", 10)))

        assertEquals(listOf("a"), rows.filter { it.isRecord }.map { it.syncId })
    }

    @Test
    fun `exactly one record per puzzle ref`() {
        val rows = settled(
            listOf(
                row("a1", 10, ref = "alpha/x"), row("a2", 20, ref = "alpha/x"),
                row("b1", 5, ref = "alpha/y"), row("b2", 6, ref = "alpha/y", isRecord = true),
            ),
        )

        val records = rows.filter { it.isRecord }
        assertEquals(setOf("a1", "b1"), records.map { it.syncId }.toSet())
        assertEquals(2, records.size)
    }

    @Test
    fun `a settled set corrects nothing`() {
        val rows = listOf(row("a", 10, isRecord = true), row("b", 20))

        assertTrue(recompute.corrected(rows).isEmpty())
    }

    @Test
    fun `only the rows that change come back`() {
        val rows = listOf(row("late", 20, isRecord = true), row("early", 10), row("other", 5, ref = "alpha/y", isRecord = true))

        val fixes = recompute.corrected(rows)

        assertEquals(setOf("late", "early"), fixes.map { it.syncId }.toSet())
        assertEquals(false, fixes.single { it.syncId == "late" }.isRecord)
        assertNull(fixes.single { it.syncId == "late" }.firstCompletedAtMs)
        assertEquals(true, fixes.single { it.syncId == "early" }.isRecord)
        assertEquals(10L, fixes.single { it.syncId == "early" }.firstCompletedAtMs)
    }

    @Test
    fun `recomputing twice changes nothing the second time`() {
        val once = settled(listOf(row("b", 20, isRecord = true), row("a", 10, isRecord = true), row("c", 30)))

        assertTrue(recompute.corrected(once).isEmpty())
    }

    @Test
    fun `live order and merged order give the same record set`() {
        val finishes = listOf("p" to 10L, "q" to 20L, "r" to 30L, "s" to 40L)

        // Live: each finish is stamped by the policy against the rows already there, oldest first.
        val live = mutableListOf<CompletionEntity>()
        finishes.forEach { (syncId, at) ->
            val decision = policy.decide(hasRecord = live.any { it.isRecord }, nowMs = at)
            val stamped = if (decision is FirstWinPolicy.Decision.Record) row(syncId, at, isRecord = true) else row(syncId, at)
            live += stamped
        }

        // Merged: the same rows arrive newest first, none of them a record yet.
        val merged = settled(finishes.reversed().map { (syncId, at) -> row(syncId, at) })

        assertEquals(live.filter { it.isRecord }.map { it.syncId }, merged.filter { it.isRecord }.map { it.syncId })
        assertEquals(live.single { it.isRecord }.firstCompletedAtMs, merged.single { it.isRecord }.firstCompletedAtMs)
    }

    @Test
    fun `any arrival order gives the same record set`() {
        val rows = listOf(row("a", 10), row("b", 10), row("c", 5), row("d", 7, ref = "alpha/y"))

        val forward = settled(rows).filter { it.isRecord }.map { it.syncId }.toSet()
        val backward = settled(rows.reversed()).filter { it.isRecord }.map { it.syncId }.toSet()

        assertEquals(setOf("c", "d"), forward)
        assertEquals(forward, backward)
    }
}
