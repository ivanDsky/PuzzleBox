package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
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
import com.ivandsky.puzzlebox.game.editor.encodeShare
import com.ivandsky.puzzlebox.game.editor.EditorShareCodec
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.PublishBlock
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Publish (spec A15): a ready draft's board goes out under a code reserved on the draft before
 * the network call, the same code on every retry; a taken code is regenerated; a success lists
 * the puzzle among the author's published ones.
 */
class CreateSharedPuzzleTest {

    private val drafts = FakeDraftRepository()
    private val backend = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR }

    /** The draft's reserved code as stored at each publish call. */
    private val reservedAtPublish = mutableListOf<String?>()
    private val shared = object : SharedPuzzleRepository by backend {
        override suspend fun publish(doc: SharedPuzzleDoc): PublishResult {
            reservedAtPublish += drafts.rows[DRAFT_ID]?.reservedCode
            return backend.publish(doc)
        }
    }
    private val published = FakePublishedPuzzleRepository()
    private val identity = FakeIdentityService(Identity(uid = AUTHOR, displayName = "Ivan", photoUrl = null))
    private val board = EditorDraft.fresh(ShareEditor, SMALL)
    private val unique = Verdict.Unique(Solution(listOf(1, 2, 3)))

    private fun createShared(seed: Int = 7) = CreateSharedPuzzle(
        drafts = drafts,
        shared = shared,
        published = published,
        identity = identity,
        validate = ValidatePublish(),
        random = Random(seed),
        clock = { NOW },
    )

    @Test
    fun `a ready draft publishes its board under a code stored on the draft before the call`() = runTest {
        drafts.upsert(stored(testSolve = TestSolve(board.layoutKey, timeMs = 102_000, hints = 1)))

        val outcome = createShared()(DRAFT_ID, board, unique)

        val code = ShareCode.random(Random(7))
        assertEquals(PublishOutcome.Published(code), outcome)
        assertEquals(listOf<String?>(code.value), reservedAtPublish)
        val doc = checkNotNull(backend.docs[code])
        assertEquals(AUTHOR, doc.authorUid)
        assertEquals("Castle", doc.title)
        assertArrayEquals(board.encodeShare(), doc.payload)
        assertEquals(ShareEditor.shareFormatVersion, doc.shareCodecVersion)
        assertEquals(102_000L, doc.authorTimeMs)
        assertEquals(
            PublishedPuzzle(
                code = code,
                gameId = GameId("list"),
                variant = SMALL,
                title = "Castle",
                authorTimeMs = 102_000,
                authorHints = 1,
                createdAt = NOW,
                unpublished = false,
                solvesCount = null,
                solvesCountAt = null,
            ),
            published.rows[code],
        )
    }

    @Test
    fun `a retry reuses the code already reserved on the draft`() = runTest {
        val code = checkNotNull(ShareCode.parse("K7Q2MX"))
        drafts.upsert(stored(testSolve = TestSolve(board.layoutKey, 1_000, 0), reservedCode = code.value))

        val outcome = createShared()(DRAFT_ID, board, unique)

        assertEquals(PublishOutcome.Published(code), outcome)
        assertEquals(setOf(code), backend.docs.keys)
    }

    @Test
    fun `a taken code is regenerated and reserved again`() = runTest {
        val taken = ShareCode.random(Random(7))
        backend.signedInUid = "someone-else"
        backend.publish(docAt(taken, "someone-else"))
        backend.signedInUid = AUTHOR
        drafts.upsert(stored(testSolve = TestSolve(board.layoutKey, 1_000, 0)))

        val outcome = createShared()(DRAFT_ID, board, unique)

        val fresh = (outcome as PublishOutcome.Published).code
        assertTrue("a new code, not the taken one", fresh != taken)
        assertEquals(listOf<String?>(taken.value, fresh.value), reservedAtPublish)
        assertEquals(fresh.value, drafts.rows[DRAFT_ID]?.reservedCode)
    }

    @Test
    fun `an untested draft is blocked and reserves nothing`() = runTest {
        drafts.upsert(stored(testSolve = null))

        val outcome = createShared()(DRAFT_ID, board, unique)

        assertEquals(PublishOutcome.Blocked(listOf(PublishBlock(PublishBlock.NOT_TESTED))), outcome)
        assertNull(drafts.rows[DRAFT_ID]?.reservedCode)
        assertTrue(reservedAtPublish.isEmpty())
    }

    private fun stored(testSolve: TestSolve?, reservedCode: String? = null) = Draft(
        id = DRAFT_ID,
        gameId = GameId("list"),
        variant = SMALL,
        draftFormatVersion = 1,
        blob = board.encode(),
        title = "Castle",
        updatedAt = Instant.parse("2026-11-01T08:00:00Z"),
        testSolve = testSolve,
        reservedCode = reservedCode,
        imported = false,
        authorName = "Ivan",
        note = "",
    )

    private fun docAt(code: ShareCode, uid: String) = SharedPuzzleDoc(
        code = code, formatVersion = 1, gameId = "list", variant = SMALL.value, shareCodecVersion = 1,
        payload = byteArrayOf(0), title = "Theirs", authorName = "", note = "", authorUid = uid,
        authorTimeMs = 1, authorHints = 0, createdAtMs = 0, unpublished = false,
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
