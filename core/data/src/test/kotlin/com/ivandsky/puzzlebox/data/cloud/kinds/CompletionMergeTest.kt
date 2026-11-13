package com.ivandsky.puzzlebox.data.cloud.kinds

import com.ivandsky.puzzlebox.data.cloud.CloudDoc
import com.ivandsky.puzzlebox.database.CompletionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure cloud-to-local merge: unknown sync ids only, original timestamps, idempotent, order-independent. */
class CompletionMergeTest {

    private val merge = CompletionMerge()

    private fun record(
        ref: String = "alpha/2026-03-01/1",
        timeMs: Long = 40_000L,
        completedAtMs: Long = 1_000L,
        firstCompletedAtMs: Long? = completedAtMs,
        extra: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> = mapOf(
        "puzzleRef" to ref,
        "timeMs" to timeMs,
        "hints" to 1L,
        "statsVersion" to 2L,
        "statsBlob" to """{"v":2}""",
        "completedAtMs" to completedAtMs,
        "firstCompletedAtMs" to firstCompletedAtMs,
        "matchCode" to null,
        "source" to "app",
    ) + extra

    private fun doc(id: String, vararg records: Pair<String, Map<String, Any?>>) =
        CloudDoc(id, mapOf("formatVersion" to 1L, "records" to mapOf(*records)))

    @Test
    fun `inserts unknown sync ids with the cloud timestamps and the uid as owner`() {
        val rows = merge.rowsToInsert(
            "ann",
            known = setOf("known"),
            docs = listOf(
                doc(
                    "2026-03",
                    "known" to record(completedAtMs = 5L),
                    "new" to record(timeMs = 41_000L, completedAtMs = 2_000L, firstCompletedAtMs = 2_000L),
                ),
            ),
        )

        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals("new", row.syncId)
        assertEquals("ann", row.ownerUid)
        assertEquals(2_000L, row.completedAtMs)
        assertEquals(2_000L, row.firstCompletedAtMs)
        assertEquals(41_000L, row.timeMs)
        assertEquals(1, row.hints)
        assertEquals(2, row.statsVersion)
        assertEquals("""{"v":2}""", row.statsBlob)
        assertEquals("app", row.source)
        assertTrue(row.uploaded)
        assertFalse(row.isRecord)
        assertEquals(0L, row.id)
    }

    @Test
    fun `a cloud isRecord never reaches the row`() {
        val rows = merge.rowsToInsert("ann", emptySet(), listOf(doc("2026-03", "a" to record(extra = mapOf("isRecord" to true)))))

        assertFalse(rows.single().isRecord)
    }

    @Test
    fun `merging twice inserts nothing the second time`() {
        val docs = listOf(doc("2026-03", "a" to record(), "b" to record(completedAtMs = 2L)))

        val first = merge.rowsToInsert("ann", emptySet(), docs)
        val second = merge.rowsToInsert("ann", first.map { it.syncId }.toSet(), docs)

        assertEquals(2, first.size)
        assertTrue(second.isEmpty())
    }

    @Test
    fun `the result does not depend on the order of docs or records`() {
        val a = doc("2026-03", "x" to record(completedAtMs = 30L), "y" to record(completedAtMs = 10L))
        val b = doc("2026-04", "z" to record(completedAtMs = 20L))
        val c = doc("2026-04-1", "w" to record(completedAtMs = 20L))

        val forward = merge.rowsToInsert("ann", emptySet(), listOf(a, b, c))
        val reversed = merge.rowsToInsert("ann", emptySet(), listOf(c, b, a))

        assertEquals(forward, reversed)
        assertEquals(listOf("y", "w", "z", "x"), forward.map { it.syncId })
    }

    @Test
    fun `a record present in two docs is inserted once`() {
        val rows = merge.rowsToInsert(
            "ann",
            emptySet(),
            listOf(doc("2026-03", "a" to record()), doc("2026-03-1", "a" to record())),
        )

        assertEquals(listOf("a"), rows.map { it.syncId })
    }

    @Test
    fun `malformed records and docs are skipped`() {
        val rows = merge.rowsToInsert(
            "ann",
            emptySet(),
            listOf(
                doc(
                    "2026-03",
                    "noRef" to record() - "puzzleRef",
                    "badTime" to record(extra = mapOf("timeMs" to "slow")),
                    "noWhen" to record() - "completedAtMs",
                    "ok" to record(),
                ),
                CloudDoc("2026-04", mapOf("formatVersion" to 1L)),
                CloudDoc("2026-05", mapOf("formatVersion" to 1L, "records" to "nope")),
            ),
        )

        assertEquals(listOf("ok"), rows.map { it.syncId })
    }

    @Test
    fun `a missing source reads as app and a match code is kept`() {
        val row = merge.rowsToInsert(
            "ann",
            emptySet(),
            listOf(doc("2026-03", "a" to (record() - "source") + mapOf("matchCode" to "ABCD"))),
        ).single()

        assertEquals("app", row.source)
        assertEquals("ABCD", row.matchCode)
    }

    @Test
    fun `encode carries the nine record keys and never isRecord`() {
        val row = CompletionEntity(
            puzzleRef = "alpha/2026-03-01/1",
            timeMs = 40_000L,
            hints = 1,
            statsVersion = 2,
            statsBlob = """{"v":2}""",
            completedAtMs = 1_000L,
            firstCompletedAtMs = 1_000L,
            isRecord = true,
            matchCode = "ABCD",
            source = "linkedin",
            ownerUid = "ann",
            syncId = "abc",
        )

        val encoded = merge.encode(row)

        assertEquals(
            setOf(
                "puzzleRef", "timeMs", "hints", "statsVersion", "statsBlob", "completedAtMs", "firstCompletedAtMs",
                "matchCode", "source",
            ),
            encoded.keys,
        )
        assertEquals(40_000L, encoded["timeMs"])
        assertEquals(1L, encoded["hints"])
        assertEquals(2L, encoded["statsVersion"])
    }

    @Test
    fun `the doc id is the UTC month and a bucket of the sync id alone`() {
        val endOfMarch = 1_775_001_599_999L // 2026-03-31T23:59:59.999Z
        val startOfApril = endOfMarch + 1

        assertEquals(merge.docId(endOfMarch, "a"), merge.docId(endOfMarch, "a"))
        assertTrue(merge.docId(endOfMarch, "a").matches(Regex("2026-03-100[0-7]")))
        assertTrue(merge.docId(startOfApril, "a").matches(Regex("2026-04-100[0-7]")))
        assertEquals("2026-03-1001", merge.docId(endOfMarch, "a"))
        assertEquals("2026-03-1002", merge.docId(endOfMarch, "b"))
    }

    @Test
    fun `a month spreads over every bucket and no doc nears the cap at 6000 records`() {
        val random = java.util.Random(5)
        val counts = (0 until 6_000)
            .map { "%016x%016x".format(random.nextLong(), random.nextLong()) }
            .groupingBy { merge.docId(1_000L, it) }
            .eachCount()

        assertEquals(CompletionMerge.BUCKETS, counts.size)
        assertTrue(counts.values.max() < 1_000)
    }

    @Test
    fun `a bucket id is above every block the ordinal scheme wrote`() {
        val ids = (0 until 200).map { merge.docId(1_000L, "%032x".format(it)) }.toSet()

        assertTrue(ids.all { it.substringAfterLast('-').toInt() >= CompletionMerge.BUCKET_BASE })
    }

    @Test
    fun `a full doc of the largest plausible records stays under 900 KB`() {
        val row = CompletionEntity(
            puzzleRef = "x".repeat(120),
            timeMs = Long.MAX_VALUE,
            hints = Int.MAX_VALUE,
            statsVersion = Int.MAX_VALUE,
            statsBlob = "y".repeat(200),
            completedAtMs = Long.MAX_VALUE,
            firstCompletedAtMs = Long.MAX_VALUE,
            isRecord = false,
            matchCode = "z".repeat(16),
            source = "linkedin",
            ownerUid = "ann",
            syncId = "0".repeat(32),
            uploaded = false,
        )
        val record = merge.encode(row)
        // Firestore sizes a field as its name, plus the value: a string's bytes, 8 for a number; 1 each for the tags.
        val bytes = record.entries.sumOf { (name, value) ->
            name.length + 1 + when (value) {
                is String -> value.length + 1
                is Number -> 8
                else -> 1
            }
        } + row.syncId.length + 1

        assertTrue("one record is $bytes bytes", bytes * 1_000 < 900_000)
    }
}
