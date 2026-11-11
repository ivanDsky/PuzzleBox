package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.domain.ComputeCodename
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The rename dice's codename never equals this draft's title or another stored draft's. */
class DraftMetaCodenameTest {

    private val seeded = ComputeCodename()(Random(9), emptySet())

    @Test
    fun `the roll is the seeded codename when no title is taken`() = runTest {
        assertEquals(seeded, meta(otherTitles = emptySet()).rollCodename())
    }

    @Test
    fun `the roll skips another draft's title`() = runTest {
        assertNotEquals(seeded, meta(otherTitles = setOf(seeded)).rollCodename())
    }

    @Test
    fun `the roll skips the open draft's own title`() = runTest {
        val meta = meta(otherTitles = emptySet())
        meta.open(seeded)

        assertNotEquals(seeded, meta.rollCodename())
    }

    private fun TestScope.meta(otherTitles: Set<String>) = DraftMeta(
        draftId = "draft-1",
        copyDraft = CreateDraftCopy(FakeDraftRepository(), newId = { "copy" }, clock = { Instant.EPOCH }),
        scope = this,
        locked = { false },
        otherTitles = { otherTitles },
        random = Random(9),
        store = {},
    )
}
