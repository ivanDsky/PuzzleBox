package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.DraftExport
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorShareCodec
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Opening a code (spec A1, A13, A19, A24) over the fake backend and store, every outcome in
 * turn. The game is [PairEditor], a two-cell board whose real solver counts its solutions, so
 * "two solutions" is a board property here rather than a canned verdict. Real games' share
 * codecs sit in games:* modules, which no core module may reach (ADR-0008).
 */
class LoadSharedPuzzleTest {

    private val shared = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR }
    private val received = FakeReceivedPuzzleRepository()
    private val registry = GameRegistry(listOf(withEditor(PAIR), TestGames.plugin(PLAIN)))

    private val load = LoadSharedPuzzle(
        registry = registry,
        shared = shared,
        received = received,
        budget = { Budget.within(BUDGET_MS, System::nanoTime) },
        clock = { NOW },
    )

    @Test
    fun `a board with one solution opens and is kept on this device`() = runTest {
        shared.publish(doc(payload = byteArrayOf(1, 0)))

        val outcome = load(CODE)

        val row = (outcome as OpenOutcome.Opened).puzzle
        assertEquals(received.rows[CODE], row)
        assertEquals(PAIR, row.gameId)
        assertEquals(PairEditor.VARIANT, row.variant)
        assertArrayEquals("the solution this device found", byteArrayOf(1, 2), row.puzzleBytes)
        assertEquals("Twins", row.title)
        assertEquals(NOW, row.openedAt)
        assertEquals(PostState.Pending, row.postState)
    }

    @Test
    fun `a stored row opens with the backend offline`() = runTest {
        val stored = stored()
        received.upsert(stored)
        shared.online = false

        assertEquals(OpenOutcome.Opened(stored), load(CODE))
    }

    @Test
    fun `a row already opened here still opens after its author withdraws it`() = runTest {
        shared.publish(doc(payload = byteArrayOf(1, 0)))
        load(CODE)
        shared.unpublish(CODE)

        assertTrue(load(CODE) is OpenOutcome.Opened)
    }

    @Test
    fun `a code with no document is not found`() = runTest {
        assertEquals(OpenOutcome.NotFound, load(CODE))
        assertTrue(received.rows.isEmpty())
    }

    @Test
    fun `a puzzle withdrawn before it was opened here is no longer available`() = runTest {
        shared.publish(doc(payload = byteArrayOf(1, 0)))
        shared.unpublish(CODE)

        assertEquals(OpenOutcome.NoLongerAvailable, load(CODE))
        assertTrue(received.rows.isEmpty())
    }

    @Test
    fun `a document in format 2 needs an update`() = runTest {
        shared.publish(doc(payload = byteArrayOf(1, 0), formatVersion = SharedPuzzleDoc.FORMAT_VERSION + 1))

        assertEquals(OpenOutcome.NeedsUpdate(SharedPuzzleDoc.FORMAT_VERSION + 1), load(CODE))
        assertTrue(received.rows.isEmpty())
    }

    @Test
    fun `a payload in a newer share format needs an update`() = runTest {
        shared.publish(doc(payload = byteArrayOf(1, 0), shareCodecVersion = PairEditor.SHARE_VERSION + 1))

        assertEquals(OpenOutcome.NeedsUpdate(PairEditor.SHARE_VERSION + 1), load(CODE))
    }

    @Test
    fun `a game this build does not register is an unknown game`() = runTest {
        shared.publish(doc(payload = byteArrayOf(1, 0), gameId = "sudoku9"))

        assertEquals(OpenOutcome.UnknownGame("sudoku9"), load(CODE))
        assertTrue(received.rows.isEmpty())
    }

    @Test
    fun `a board with two solutions fails its check and is not kept`() = runTest {
        shared.publish(doc(payload = byteArrayOf(0, 0)))

        assertEquals(OpenOutcome.FailedCheck, load(CODE))
        assertTrue(received.rows.isEmpty())
    }

    @Test
    fun `a malformed payload fails its check`() = runTest {
        shared.publish(doc(payload = byteArrayOf(1, 0, 7)))

        assertEquals(OpenOutcome.FailedCheck, load(CODE))
    }

    @Test
    fun `a game without an editor fails its check`() = runTest {
        shared.publish(doc(payload = byteArrayOf(1, 0), gameId = PLAIN.value))

        assertEquals(OpenOutcome.FailedCheck, load(CODE))
    }

    @Test
    fun `a new code with the backend offline is offline`() = runTest {
        shared.publish(doc(payload = byteArrayOf(1, 0)))
        shared.online = false

        assertEquals(OpenOutcome.Offline, load(CODE))
        assertTrue(received.rows.isEmpty())
    }

    @Test
    fun `a codec that throws on a crafted payload fails its check`() = runTest {
        shared.publish(doc(payload = byteArrayOf(1, 0)))
        val throwing = loadWith(ThrowingEditor(onDecode = true, IllegalArgumentException("crafted")))

        assertEquals(OpenOutcome.FailedCheck, throwing(CODE))
    }

    @Test
    fun `an analyzer that throws on a crafted payload fails its check`() = runTest {
        shared.publish(doc(payload = byteArrayOf(1, 0)))
        val throwing = loadWith(ThrowingEditor(onDecode = false, IllegalStateException("crafted")))

        assertEquals(OpenOutcome.FailedCheck, throwing(CODE))
    }

    @Test
    fun `cancellation still propagates out of the open`() = runTest {
        shared.publish(doc(payload = byteArrayOf(1, 0)))
        val throwing = loadWith(ThrowingEditor(onDecode = true, CancellationException()))

        try {
            throwing(CODE)
            fail("expected CancellationException to propagate")
        } catch (expected: CancellationException) {
            // the open was cancelled, not failed
        }
    }

    @Test
    fun `a public doc's published level is stored over the level graded on this device`() = runTest {
        shared.publish(doc(payload = byteArrayOf(1, 0), difficulty = Difficulty.Level.Easy))
        val grading = loadWith(GradedEditor(Difficulty(Difficulty.Level.Hard, score = null, hardestRung = null)))

        val row = (grading(CODE) as OpenOutcome.Opened).puzzle

        assertEquals(Difficulty.Level.Easy, row.difficulty)
        assertEquals(Difficulty.Level.Easy, received.rows[CODE]?.difficulty)
    }

    @Test
    fun `a doc with no published level stores the level graded on this device`() = runTest {
        shared.publish(doc(payload = byteArrayOf(1, 0), difficulty = null))
        val grading = loadWith(GradedEditor(Difficulty(Difficulty.Level.Hard, score = null, hardestRung = null)))

        val row = (grading(CODE) as OpenOutcome.Opened).puzzle

        assertEquals(Difficulty.Level.Hard, row.difficulty)
        assertEquals(Difficulty.Level.Hard, received.rows[CODE]?.difficulty)
    }

    @Test
    fun `a doc with no published level and an ungraded game stores no level`() = runTest {
        shared.publish(doc(payload = byteArrayOf(1, 0), difficulty = null))

        assertNull((load(CODE) as OpenOutcome.Opened).puzzle.difficulty)
    }

    private fun loadWith(editor: GameEditor<List<Int>>): LoadSharedPuzzle =
        LoadSharedPuzzle(
            registry = GameRegistry(listOf(withEditor(PAIR, editor))),
            shared = shared,
            received = received,
            budget = { Budget.within(BUDGET_MS, System::nanoTime) },
            clock = { NOW },
        )

    /** A [PairEditor] whose unique verdict carries [grade], standing in for the legacy grader's label. */
    private class GradedEditor(private val grade: Difficulty) : GameEditor<List<Int>> by PairEditor,
        EditorShareCodec<List<Int>> by PairEditor,
        DraftExport<List<Int>> by PairEditor {
        override fun analyze(draft: List<Int>, budget: Budget): Verdict =
            when (val verdict = PairEditor.analyze(draft, budget)) {
                is Verdict.Unique -> Verdict.Unique(verdict.solution, grade)
                else -> verdict
            }
    }

    /** A [PairEditor] whose decode or analyze throws, standing in for a game's codec bug. */
    private class ThrowingEditor(
        private val onDecode: Boolean,
        private val boom: RuntimeException,
    ) : GameEditor<List<Int>> by PairEditor,
        EditorShareCodec<List<Int>> by PairEditor,
        DraftExport<List<Int>> by PairEditor {
        override fun decodeShare(bytes: ByteArray): DecodeResult<List<Int>> {
            if (onDecode) throw boom
            return PairEditor.decodeShare(bytes)
        }

        override fun analyze(draft: List<Int>, budget: Budget): Verdict {
            if (!onDecode) throw boom
            return PairEditor.analyze(draft, budget)
        }
    }

    private fun doc(
        payload: ByteArray,
        gameId: String = PAIR.value,
        formatVersion: Int = SharedPuzzleDoc.FORMAT_VERSION,
        shareCodecVersion: Int = PairEditor.SHARE_VERSION,
        difficulty: Difficulty.Level? = null,
    ) = SharedPuzzleDoc(
        code = CODE, formatVersion = formatVersion, gameId = gameId, variant = PairEditor.VARIANT.value,
        shareCodecVersion = shareCodecVersion, payload = payload, title = "Twins", authorName = "Jonas",
        note = "", authorUid = AUTHOR, authorTimeMs = 42_000L, authorHints = 0, createdAtMs = 0L, unpublished = false,
        difficulty = difficulty,
    )

    private fun stored() = ReceivedPuzzle(
        code = CODE, gameId = PAIR, variant = PairEditor.VARIANT, sharedFormatVersion = 1,
        puzzleBytes = byteArrayOf(2, 1), title = "Kept", authorName = "Jonas", note = "", authorTimeMs = 42_000L,
        authorHints = 0, authorUid = AUTHOR, openedAt = Instant.EPOCH, unpublished = false,
        postState = PostState.Pending, postAttempts = 0, difficulty = null,
    )

    private companion object {
        val PAIR = PairEditor.GAME
        val PLAIN = GameId("plain")
        val CODE = checkNotNull(ShareCode.parse("K7Q2MX"))
        const val AUTHOR = "uid-jonas"
        const val BUDGET_MS = 1_000L
        val NOW: Instant = Instant.parse("2026-11-07T10:00:00Z")
    }
}
