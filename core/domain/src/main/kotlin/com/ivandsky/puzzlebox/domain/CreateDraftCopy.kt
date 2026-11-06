package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import java.time.Instant
import kotlinx.coroutines.flow.first

/**
 * Duplicate (01 handoff "Draft actions sheet"): stores a new draft with the source's board and
 * history blob, variant and details, but no test solve and no reserved code, so the copy must
 * be test-solved again and publishes under a new code.
 */
class CreateDraftCopy(
    private val drafts: DraftRepository,
    private val newId: () -> String,
    private val clock: () -> Instant,
) {
    /**
     * Title "{source} 2", 3, … (smallest free), the source title cut so the copy stays within
     * [Draft.TITLE_MAX]. A source that no longer exists copies nothing and yields null.
     */
    suspend operator fun invoke(sourceId: String): Draft? {
        val source = drafts.get(sourceId) ?: return null
        val taken = drafts.observeAll().first().map { it.title }.toSet()
        val title = generateSequence(2) { it + 1 }.map { numbered(source.title, it) }.first { it !in taken }
        val copy = source.copy(
            id = newId(),
            title = title,
            updatedAt = clock(),
            testSolve = null,
            reservedCode = null,
        )
        drafts.upsert(copy)
        return copy
    }

    private fun numbered(base: String, number: Int): String {
        val suffix = " $number"
        return base.take(Draft.TITLE_MAX - suffix.length).trimEnd() + suffix
    }
}
