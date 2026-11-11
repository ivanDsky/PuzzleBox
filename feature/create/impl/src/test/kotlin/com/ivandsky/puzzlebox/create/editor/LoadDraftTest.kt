package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.create.FakeGameEditor
import com.ivandsky.puzzlebox.create.FakeGameEditorUi
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Opening a stored draft for the Studio: the row, its game's editor and editor UI, and the
 * decoded board; null when any of them is missing or the blob does not decode.
 */
class LoadDraftTest {

    private val editor = FakeGameEditor()
    private val ui = FakeGameEditorUi()
    private val drafts = FakeDraftRepository()
    private val load = LoadDraft(drafts, GameRegistry(listOf(withEditor(editor))), GameEditorRegistry(listOf(ui)))

    @Test
    fun `a stored draft opens with its row, board and editor UI`() = runTest {
        val board = editor.draft(marks = 3)
        val row = storedRow(board.encode())
        drafts.upsert(row)

        val loaded = load(DRAFT_ID)

        assertEquals(row, loaded?.stored)
        assertEquals(board.state.draft, loaded?.draft?.state?.draft)
        assertSame(ui, loaded?.ui)
    }

    @Test
    fun `an unknown id loads nothing`() = runTest {
        assertNull(load(DRAFT_ID))
    }

    @Test
    fun `a draft of a game without an editor loads nothing`() = runTest {
        drafts.upsert(storedRow(editor.draft(marks = 1).encode()).copy(gameId = TestGames.beta))

        assertNull(load(DRAFT_ID))
    }

    @Test
    fun `a draft of a game without an editor UI loads nothing`() = runTest {
        val other = FakeGameEditor(GameId("other"))
        val noUi = LoadDraft(drafts, GameRegistry(listOf(withEditor(other))), GameEditorRegistry(listOf(ui)))
        drafts.upsert(storedRow(other.draft(marks = 1).encode()).copy(gameId = other.gameId))

        assertNull(noUi(DRAFT_ID))
    }

    @Test
    fun `a blob that does not decode loads nothing`() = runTest {
        drafts.upsert(storedRow(byteArrayOf(99, 1)))

        assertNull(load(DRAFT_ID))
    }

    private companion object {
        const val DRAFT_ID = "draft-1"
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")

        fun storedRow(blob: ByteArray) = Draft(
            id = DRAFT_ID,
            gameId = FakeGameEditor().gameId,
            variant = FakeGameEditor().variants.first(),
            draftFormatVersion = 1,
            blob = blob,
            title = "Untitled 1",
            updatedAt = NOW,
            testSolve = null,
            reservedCode = null,
            imported = false,
            authorName = "",
            note = "",
        )

        fun withEditor(editor: FakeGameEditor): GamePlugin<*, *, *> {
            val base = TestGames.plugin(editor.gameId)
            return GamePlugin(
                definition = base.definition,
                rules = base.rules,
                session = base.session,
                score = base.score,
                stateOf = base.stateOf,
                initialSession = base.initialSession,
                refOf = base.refOf,
                playAgainSession = base.playAgainSession,
                rendererModel = base.rendererModel,
                editor = editor,
            )
        }
    }
}
