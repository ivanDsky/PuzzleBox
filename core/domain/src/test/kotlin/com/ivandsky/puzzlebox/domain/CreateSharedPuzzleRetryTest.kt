package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.data.repository.PublishResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftExport
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.EditorShareCodec
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Publishing never doubles and never loses the author's code: an answer lost after the commit,
 * or an app killed after the code was reserved, is retried under that same code and ends with
 * one document; a code that stays taken is regenerated a bounded number of times, then refused.
 */
class CreateSharedPuzzleRetryTest {

    private val drafts = FakeDraftRepository()
    private val backend = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR }
    private val published = FakePublishedPuzzleRepository()
    private val identity = FakeIdentityService(Identity(uid = AUTHOR, displayName = "Ivan", photoUrl = null))
    private val board = EditorDraft.fresh(ShareEditor, SMALL)
    private val unique = Verdict.Unique(Solution(listOf(1, 2, 3)))

    private fun createShared(shared: SharedPuzzleRepository = backend, seed: Int = 7) = CreateSharedPuzzle(
        drafts = drafts,
        shared = shared,
        published = published,
        identity = identity,
        validate = ValidatePublish(),
        random = Random(seed),
        clock = { NOW },
    )

    @Test
    fun `a commit whose answer is lost is retried under the same code and leaves one doc`() = runTest {
        drafts.upsert(ready())
        backend.commitThenOffline = true
        val publish = createShared()

        val first = publish(DRAFT_ID, board, unique)

        assertEquals(PublishOutcome.Offline, first)
        val reserved = checkNotNull(drafts.rows.getValue(DRAFT_ID).reservedCode)
        assertEquals("the doc landed before the answer was lost", setOf(reserved), backend.docs.keys.map { it.value }.toSet())
        assertTrue("not listed as published yet", published.rows.isEmpty())

        val retry = publish(DRAFT_ID, board, unique)

        val code = checkNotNull(ShareCode.parse(reserved))
        assertEquals(PublishOutcome.Published(code), retry)
        assertEquals("exactly one doc, under the reserved code", setOf(code), backend.docs.keys)
        assertEquals("one published row", setOf(code), published.rows.keys)
        assertEquals("one create counted against the cap", 1, backend.publishesToday)
    }

    @Test
    fun `an edited title after a lost answer publishes under a fresh code with the new title`() = runTest {
        drafts.upsert(ready())
        backend.commitThenOffline = true
        val publish = createShared()
        assertEquals(PublishOutcome.Offline, publish(DRAFT_ID, board, unique))
        val lost = checkNotNull(ShareCode.parse(checkNotNull(drafts.rows.getValue(DRAFT_ID).reservedCode)))
        drafts.upsert(drafts.rows.getValue(DRAFT_ID).copy(title = "Fortress"))

        val outcome = publish(DRAFT_ID, board, unique)

        val code = (outcome as PublishOutcome.Published).code
        assertTrue("a different code than the lost one", code != lost)
        assertEquals("the new code is the reserved one", code.value, drafts.rows.getValue(DRAFT_ID).reservedCode)
        assertEquals("friends get the new title", "Fortress", backend.docs.getValue(code).title)
        assertEquals("the lost doc keeps its old title", "Castle", backend.docs.getValue(lost).title)
        assertTrue("the lost doc is retired", backend.docs.getValue(lost).unpublished)
        assertTrue("the new doc stays published", !backend.docs.getValue(code).unpublished)
        assertEquals("the row carries the new title", "Fortress", published.rows.getValue(code).title)
        assertEquals("only the new code is listed", setOf(code), published.rows.keys)
    }

    @Test
    fun `a draft killed after its code was reserved publishes later under that code`() = runTest {
        drafts.upsert(ready())
        backend.online = false
        assertEquals(PublishOutcome.Offline, createShared(seed = 7)(DRAFT_ID, board, unique))
        val reserved = checkNotNull(drafts.rows.getValue(DRAFT_ID).reservedCode)
        assertTrue("nothing written offline", backend.docs.isEmpty())

        // A fresh process: new use case, new random, the stored row as it was left.
        backend.online = true
        val outcome = createShared(seed = 99)(DRAFT_ID, board, unique)

        val code = checkNotNull(ShareCode.parse(reserved))
        assertEquals(PublishOutcome.Published(code), outcome)
        assertEquals(setOf(code), backend.docs.keys)
        assertEquals(reserved, drafts.rows.getValue(DRAFT_ID).reservedCode)
    }

    @Test
    fun `a code taken on every try is regenerated three times, then the publish is rejected`() = runTest {
        drafts.upsert(ready())
        val tried = mutableListOf<ShareCode>()
        val alwaysTaken = object : SharedPuzzleRepository by backend {
            override suspend fun publish(doc: SharedPuzzleDoc): PublishResult {
                tried += doc.code
                return PublishResult.CodeTaken
            }
        }

        val outcome = createShared(shared = alwaysTaken)(DRAFT_ID, board, unique)

        assertTrue("rejected, got $outcome", outcome is PublishOutcome.Rejected)
        assertEquals("the first code and three regenerations, no fifth try", 4, tried.size)
        assertEquals("every try under a fresh code", 4, tried.toSet().size)
        assertEquals("the last tried code stays reserved", tried.last().value, drafts.rows.getValue(DRAFT_ID).reservedCode)
        assertTrue(backend.docs.isEmpty())
        assertTrue(published.rows.isEmpty())
    }

    private fun ready() = Draft(
        id = DRAFT_ID,
        gameId = GameId("list"),
        variant = SMALL,
        draftFormatVersion = 1,
        blob = board.encode(),
        title = "Castle",
        updatedAt = Instant.parse("2026-11-01T08:00:00Z"),
        testSolve = TestSolve(board.layoutKey, timeMs = 1_000, hints = 0),
        reservedCode = null,
        imported = false,
        authorName = "Ivan",
        note = "",
    )

    /** A three-cell board that shares its cells as bytes and exports them as its puzzle. */
    private object ShareEditor : GameEditor<List<Int>>, EditorShareCodec<List<Int>>, DraftExport<List<Int>> {
        override val gameId: GameId = GameId("list")
        override val variants: List<VariantId> = listOf(SMALL)
        override val draftFormatVersion: Int = 1
        override val shareFormatVersion: Int = 1
        override fun newDraft(variant: VariantId): List<Int> = listOf(1, 2, 3)
        override fun variantOf(draft: List<Int>): VariantId = SMALL
        override fun encodeDraft(state: DraftState<List<Int>>): ByteArray = state.draft.map { it.toByte() }.toByteArray()
        override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<List<Int>>> =
            DecodeResult.Ok(DraftState(bytes.map { it.toInt() }))
        override fun analyze(draft: List<Int>, budget: Budget): Verdict = Verdict.Unique(Solution(draft))
        override fun encodeShare(draft: List<Int>): ByteArray = draft.map { it.toByte() }.toByteArray()
        override fun decodeShare(bytes: ByteArray): DecodeResult<List<Int>> = DecodeResult.Ok(bytes.map { it.toInt() })
        override fun toPuzzle(draft: List<Int>, solution: Solution): Puzzle = Puzzle(gameId, SMALL, 1, encodeShare(draft))
    }

    private companion object {
        const val DRAFT_ID = "d1"
        const val AUTHOR = "author-uid"
        val SMALL = VariantId("list/small")
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
    }
}
