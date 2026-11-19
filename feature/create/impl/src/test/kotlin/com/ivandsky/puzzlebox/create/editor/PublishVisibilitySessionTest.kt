package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.create.domain.CreateSharedPuzzle
import com.ivandsky.puzzlebox.create.domain.PublishOutcome
import com.ivandsky.puzzlebox.create.domain.ValidatePublish
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc.Visibility
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.DraftExport
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.EditorShareCodec
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.LadderRung
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a publish sends (nav-rethink C1): Public by default with the verdict's difficulty, or Link only when picked.
 * The pick lives with the open session only.
 */
class PublishVisibilitySessionTest {

    private val drafts = FakeDraftRepository()
    private val backend = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR.uid }
    private val board = EditorDraft.fresh(ShareEditor, SMALL)
    private val medium = Verdict.Unique(Solution(listOf(1, 2, 3)), difficulty = Difficulty(MediumRung))

    /** The row as the Studio's one writer holds it. */
    private var row = Draft(
        id = DRAFT_ID, gameId = GameId("list"), variant = SMALL, draftFormatVersion = 1, blob = board.encode(), title = "Castle",
        updatedAt = Instant.parse("2026-11-01T08:00:00Z"), testSolve = TestSolve(board.layoutKey, 1_000, 0),
        reservedCode = null, imported = false, authorName = "Ivan", note = "",
    )

    @Test
    fun `the default is Public and the doc carries the verdict's difficulty`() = runTest {
        val session = session()

        assertEquals(PublishVisibility.Choice(Visibility.Public), session.state.value.details.visibility)
        publish(session)

        val doc = backend.docs.values.single()
        assertEquals(Visibility.Public, doc.visibility)
        assertEquals(Difficulty.Level.Medium, doc.difficulty)
    }

    @Test
    fun `picking Link only sends a link only doc`() = runTest {
        val session = session()

        session.pickVisibility(Visibility.LinkOnly)
        assertEquals(PublishVisibility.Choice(Visibility.LinkOnly), session.state.value.details.visibility)
        publish(session)

        val doc = backend.docs.values.single()
        assertEquals(Visibility.LinkOnly, doc.visibility)
        assertNull(doc.difficulty)
    }

    @Test
    fun `picking Public again after Link only sends Public`() = runTest {
        val session = session()

        session.pickVisibility(Visibility.LinkOnly)
        session.pickVisibility(Visibility.Public)
        publish(session)

        assertEquals(Visibility.Public, backend.docs.values.single().visibility)
    }

    @Test
    fun `typing the author name or note keeps the pick`() = runTest {
        val session = session()
        session.pickVisibility(Visibility.LinkOnly)

        session.editAuthorName("Ada")
        session.editNote("Start top left")

        assertEquals(Visibility.LinkOnly, session.state.value.details.visibility.pick)
    }

    @Test
    fun `an attempt that falls short freezes the pick, and the retry is the same publish`() = runTest {
        val session = session()
        backend.commitThenOffline = true
        publish(session)
        assertEquals(PublishOutcome.Offline, session.state.value.outcome)
        val lost = backend.docs.keys.single()
        assertTrue("the picker is off while the retry waits", session.state.value.details.visibility.locked)

        session.pickVisibility(Visibility.LinkOnly)
        assertEquals(Visibility.Public, session.state.value.details.visibility.pick)
        publish(session)

        assertEquals(PublishOutcome.Published(lost), session.state.value.outcome)
        assertEquals("one doc, still public", setOf(lost), backend.docs.keys)
        assertEquals(Visibility.Public, backend.docs.getValue(lost).visibility)
    }

    @Test
    fun `a pick after the publish landed changes nothing`() = runTest {
        val session = session()
        publish(session)

        session.pickVisibility(Visibility.LinkOnly)

        assertEquals(Visibility.Public, session.state.value.details.visibility.pick)
    }

    private fun TestScope.publish(session: PublishSession) {
        session.publish(board, medium)
        runCurrent()
    }

    private suspend fun TestScope.session(): PublishSession {
        drafts.upsert(row)
        return PublishSession(
            draftId = DRAFT_ID,
            createShared = CreateSharedPuzzle(
                drafts = drafts, shared = backend, published = FakePublishedPuzzleRepository(), identity = FakeIdentityService(AUTHOR),
                validate = ValidatePublish(), random = Random(7), clock = { NOW },
            ),
            identity = FakeIdentityService(AUTHOR),
            idTokens = noGoogleAccount,
            scope = backgroundScope,
            flush = { drafts.upsert(row) },
            reserved = { code -> row = row.copy(reservedCode = code) },
            storeDetails = { name, note -> row = row.copy(authorName = name, note = note) },
            storeLevel = {},
        )
    }

    private object MediumRung : LadderRung {
        override val id = "medium"
        override val level = Difficulty.Level.Medium
        override val captionKey = "medium"
    }

    /** A three-cell board that shares its cells as bytes and exports them as its puzzle. */
    private object ShareEditor : GameEditor<List<Int>>, EditorShareCodec<List<Int>>, DraftExport<List<Int>> {
        override val gameId: GameId = GameId("list")
        override val variants: List<VariantId> = listOf(SMALL)
        override val draftFormatVersion: Int = 1
        override val shareFormatVersion: Int = 1
        override fun newDraft(variant: VariantId): List<Int> = listOf(1, 2, 3)
        override fun variantOf(draft: List<Int>): VariantId = SMALL
        override fun encodeDraft(state: DraftState<List<Int>>): ByteArray = state.draft.map { it.toByte() }.toByteArray()
        override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<List<Int>>> = DecodeResult.Ok(DraftState(bytes.map { it.toInt() }))
        override fun analyze(draft: List<Int>, budget: Budget): Verdict = Verdict.Unique(Solution(draft))
        override fun encodeShare(draft: List<Int>): ByteArray = draft.map { it.toByte() }.toByteArray()
        override fun decodeShare(bytes: ByteArray): DecodeResult<List<Int>> = DecodeResult.Ok(bytes.map { it.toInt() })
        override fun toPuzzle(draft: List<Int>, solution: Solution): Puzzle = Puzzle(gameId, SMALL, 1, encodeShare(draft))
    }

    private companion object {
        const val DRAFT_ID = "d1"
        val AUTHOR = Identity(uid = "author-uid", displayName = "Ivan", photoUrl = null)
        val SMALL = VariantId("list/small")
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
    }
}
