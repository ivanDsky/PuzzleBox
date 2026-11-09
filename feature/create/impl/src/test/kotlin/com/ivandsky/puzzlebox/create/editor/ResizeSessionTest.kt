package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.create.FakeGameEditor
import com.ivandsky.puzzlebox.create.TestDraft
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.DraftResize
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.GameEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The resize confirm's rule: a resize that would start a blank board over content (an editor
 * without DraftResize, a board that isn't blank) waits on [ResizeSession.pending] for confirm or
 * cancel; any other resize applies at once; the open size and a locked draft do nothing.
 */
class ResizeSessionTest {

    private val blanking = FakeGameEditor(variants = listOf(OPEN, OTHER))
    private val keeping = KeepingEditor(blanking)
    private val applied = mutableListOf<VariantId>()
    private var locked = false

    private fun session(board: EditorDraft<*>) = ResizeSession(board = { board }, locked = { locked }, apply = { applied += it })

    @Test
    fun `a blank board resizes at once`() {
        val session = session(blanking.draft(0))

        session.request(OTHER)

        assertEquals(listOf(OTHER), applied)
        assertNull(session.pending.value)
    }

    @Test
    fun `a board with content asks first and confirm applies it once`() {
        val session = session(blanking.draft(3))

        session.request(OTHER)

        assertEquals(emptyList<VariantId>(), applied)
        assertEquals(OTHER, session.pending.value)

        session.confirm()
        session.confirm()

        assertEquals(listOf(OTHER), applied)
        assertNull(session.pending.value)
    }

    @Test
    fun `cancel drops the pending size without applying it`() {
        val session = session(blanking.draft(3))

        session.request(OTHER)
        session.cancel()
        session.confirm()

        assertEquals(emptyList<VariantId>(), applied)
        assertNull(session.pending.value)
    }

    @Test
    fun `a DraftResize editor never asks`() {
        val session = session(EditorDraft(keeping, DraftState(TestDraft(3))))

        session.request(OTHER)

        assertEquals(listOf(OTHER), applied)
        assertNull(session.pending.value)
    }

    @Test
    fun `the open size does nothing`() {
        val session = session(blanking.draft(3))

        session.request(OPEN)

        assertEquals(emptyList<VariantId>(), applied)
        assertNull(session.pending.value)
    }

    @Test
    fun `a locked draft does nothing`() {
        locked = true
        val blank = session(blanking.draft(0))
        val painted = session(blanking.draft(3))

        blank.request(OTHER)
        painted.request(OTHER)

        assertEquals(emptyList<VariantId>(), applied)
        assertNull(blank.pending.value)
        assertNull(painted.pending.value)
    }

    /** The fake editor plus a resize that keeps the board, as Zip's does. */
    private class KeepingEditor(base: FakeGameEditor) : GameEditor<TestDraft> by base, DraftResize<TestDraft> {
        override fun resized(draft: TestDraft, variant: VariantId): TestDraft = draft
    }

    private companion object {
        val OPEN = VariantId("fake/v1")
        val OTHER = VariantId("fake/v2")
    }
}
