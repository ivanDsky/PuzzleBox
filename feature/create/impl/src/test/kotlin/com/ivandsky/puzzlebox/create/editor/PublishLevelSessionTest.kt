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
import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.game.editor.DraftExport
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.EditorShareCodec
import com.ivandsky.puzzlebox.game.editor.GameEditor
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The author's level on the published doc (96): a Public doc stores the level the author chose, the
 * proposal when they chose none; link only still stores none.
 * The pick is handed to the draft's writer, comes back through `open`, and is frozen like the visibility once an attempt
 * falls short.
 */
class PublishLevelSessionTest {

    private val drafts = FakeDraftRepository()
    private val backend = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR.uid }
    private val board = EditorDraft.fresh(ShareEditor, SMALL)
    private val hard = Verdict.Unique(Solution(listOf(1, 2, 3)), difficulty = Difficulty(Level.Hard, score = 12, hardestRung = null))
    private val stored = mutableListOf<Level?>()

    private var row = Draft(
        id = DRAFT_ID, gameId = GameId("list"), variant = SMALL, draftFormatVersion = 1, blob = board.encode(), title = "Castle",
        updatedAt = Instant.parse("2026-11-01T08:00:00Z"), testSolve = TestSolve(board.layoutKey, 1_000, 0),
        reservedCode = null, imported = false, authorName = "Ivan", note = "",
    )

    @Test
    fun `flag on, a Public doc stores the level the author chose`() = runTest {
        val session = session()

        session.pickLevel(Level.Easy, hard)
        publish(session)

        assertEquals(Level.Easy, backend.docs.values.single().difficulty)
    }

    @Test
    fun `flag on, with no pick the doc stores the proposal`() = runTest {
        val session = session()

        publish(session)

        assertEquals(Level.Hard, backend.docs.values.single().difficulty)
    }

    @Test
    fun `flag on, a link only doc stores no level whatever was chosen`() = runTest {
        val session = session()

        session.pickLevel(Level.Easy, hard)
        session.pickVisibility(Visibility.LinkOnly)
        publish(session)

        val doc = backend.docs.values.single()
        assertEquals(Visibility.LinkOnly, doc.visibility)
        assertNull(doc.difficulty)
    }

    @Test
    fun `the control is offered only while the doc will be Public`() = runTest {
        val session = session()
        assertTrue(session.state.value.details.levelOffered)

        session.pickVisibility(Visibility.LinkOnly)
        assertFalse(session.state.value.details.levelOffered)

        session.pickVisibility(Visibility.Public)
        assertTrue(session.state.value.details.levelOffered)
    }

    @Test
    fun `a pick is handed to the draft's writer and shown, and picking the proposal again drops it`() = runTest {
        val session = session()

        session.pickLevel(Level.Expert, hard)
        assertEquals(Level.Expert, session.state.value.details.level)
        session.pickLevel(Level.Hard, hard)

        assertNull(session.state.value.details.level)
        assertEquals(listOf<Level?>(Level.Expert, null), stored)
    }

    @Test
    fun `a pick that changes nothing is not stored again`() = runTest {
        val session = session()

        session.pickLevel(Level.Hard, hard)
        session.pickLevel(Level.Easy, hard)
        session.pickLevel(Level.Easy, hard)

        assertEquals(listOf<Level?>(Level.Easy), stored)
    }

    @Test
    fun `a board with no proposal takes no pick`() = runTest {
        val session = session()

        session.pickLevel(Level.Easy, Verdict.Checking)
        session.pickLevel(Level.Easy, Verdict.Unique(Solution(listOf(1)), needsGuessing = true))

        assertNull(session.state.value.details.level)
        assertTrue(stored.isEmpty())
    }

    @Test
    fun `the stored pick comes back through open and is what a publish sends`() = runTest {
        val session = session()

        session.open("Ivan", "", published = null, level = Level.Medium)
        assertEquals(Level.Medium, session.state.value.details.level)
        publish(session)

        assertEquals(Level.Medium, backend.docs.values.single().difficulty)
    }

    @Test
    fun `a stored pick on a board with no proposal sends what a publish sent before`() = runTest {
        val session = session()
        session.open("Ivan", "", published = null, level = Level.Medium)

        session.publish(board, Verdict.Unique(Solution(listOf(1, 2, 3)), needsGuessing = true))
        runCurrent()

        assertNull(backend.docs.values.single().difficulty)
    }

    @Test
    fun `typing the author name or note keeps the pick`() = runTest {
        val session = session()
        session.pickLevel(Level.Easy, hard)

        session.editAuthorName("Ada")
        session.editNote("Start top left")

        assertEquals(Level.Easy, session.state.value.details.level)
    }

    @Test
    fun `an attempt that falls short freezes the pick, and the retry is the same publish`() = runTest {
        val session = session()
        session.pickLevel(Level.Easy, hard)
        backend.commitThenOffline = true
        publish(session)
        assertEquals(PublishOutcome.Offline, session.state.value.outcome)
        val lost = backend.docs.keys.single()

        session.pickLevel(Level.Expert, hard)
        assertEquals(Level.Easy, session.state.value.details.level)
        publish(session)

        assertEquals(PublishOutcome.Published(lost), session.state.value.outcome)
        assertEquals(Level.Easy, backend.docs.getValue(lost).difficulty)
    }

    @Test
    fun `a pick after the publish landed changes nothing`() = runTest {
        val session = session()
        publish(session)

        session.pickLevel(Level.Easy, hard)

        assertNull(session.state.value.details.level)
        assertTrue(stored.isEmpty())
    }

    private fun TestScope.publish(session: PublishSession) {
        session.publish(board, hard)
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
            storeLevel = { level ->
                stored += level
                row = row.copy(difficultyChoice = level)
            },
        )
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
