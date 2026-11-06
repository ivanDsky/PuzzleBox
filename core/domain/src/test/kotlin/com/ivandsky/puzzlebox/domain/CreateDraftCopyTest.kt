package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Duplicate: the board and history under "{title} 2" (smallest free), never the test solve or code. */
class CreateDraftCopyTest {

    private val drafts = FakeDraftRepository()
    private var ids = 0
    private val now = Instant.parse("2026-11-06T10:00:00Z")
    private val copyDraft = CreateDraftCopy(drafts = drafts, newId = { "copy-${++ids}" }, clock = { now })

    @Test
    fun `the copy keeps the board and drops the test solve and reserved code`() = runTest {
        val source = draft("src", "Castle")
        drafts.upsert(source)

        val copy = copyDraft("src")

        val expected = source.copy(
            id = "copy-1",
            title = "Castle 2",
            updatedAt = now,
            testSolve = null,
            reservedCode = null,
        )
        assertEquals(expected, copy)
        assertEquals(expected, drafts.rows["copy-1"])
        assertEquals(source, drafts.rows["src"])
    }

    @Test
    fun `titles take the smallest free number`() = runTest {
        drafts.upsert(draft("src", "Castle"))
        drafts.upsert(draft("other", "Castle 3"))

        assertEquals("Castle 2", copyDraft("src")?.title)
        assertEquals("Castle 4", copyDraft("src")?.title)
    }

    @Test
    fun `a long title is cut so the copy stays within the title limit`() = runTest {
        val long = "x".repeat(Draft.TITLE_MAX)
        drafts.upsert(draft("src", long))

        assertEquals("x".repeat(Draft.TITLE_MAX - 2) + " 2", copyDraft("src")?.title)
    }

    @Test
    fun `a missing source copies nothing`() = runTest {
        assertNull(copyDraft("gone"))
        assertEquals(emptyMap<String, Draft>(), drafts.rows)
    }

    private fun draft(id: String, title: String) = Draft(
        id = id,
        gameId = GameId("alpha"),
        variant = VariantId("alpha/8"),
        draftFormatVersion = 3,
        blob = byteArrayOf(1, 2, 3, 4),
        title = title,
        updatedAt = Instant.parse("2026-11-01T08:00:00Z"),
        testSolve = TestSolve(layoutKey = "k", timeMs = 61_000, hints = 1),
        reservedCode = "K7Q2MX",
        imported = true,
        authorName = "Ivan",
        note = "for friends",
    )
}
