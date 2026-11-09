package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.SharedOpen
import com.ivandsky.puzzlebox.game.editor.SharedOpenResult
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.game.editor.canShare
import com.ivandsky.puzzlebox.game.editor.encodeShare
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoTestSupport
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TangoEditorExportTest {

    private val sample = TangoDraft(6, TangoTestSupport.sample.givens, TangoTestSupport.sample.markers)
    private val solution = Solution(TangoTestSupport.solution.map { it.ordinal })

    private fun fixture(): ByteArray =
        TangoEditorExportTest::class.java.getResourceAsStream("/editor/tango-share-v1.bin")!!.use { it.readBytes() }

    @Test
    fun `toPuzzle round-trips through TangoCodec to the draft board`() {
        val puzzle = TangoEditor.toPuzzle(sample, solution)

        assertEquals(TangoDefinition.id, puzzle.gameId)
        assertEquals(TangoTestSupport.variant, puzzle.variant)
        assertEquals(TangoCodec.VERSION, puzzle.formatVersion)
        assertEquals(DecodeResult.Ok(TangoTestSupport.sample), TangoCodec.decode(puzzle.payload))
    }

    @Test
    fun `a Tango draft can share and encodes its current board`() {
        val draft = EditorDraft(TangoEditor, DraftState(sample))

        assertTrue(draft.canShare)
        assertArrayEquals(fixture(), draft.encodeShare())
    }

    @Test
    fun `the frozen fixture re-verifies into a Tango puzzle`() {
        val result = SharedOpen.verify(TangoEditor, TangoEditor.shareFormatVersion, fixture(), Budget.Unlimited)

        val verified = result as SharedOpenResult.Verified
        assertEquals(TangoDefinition.id, verified.puzzle.gameId)
        assertEquals(TangoTestSupport.variant, verified.variant)
        assertEquals(DecodeResult.Ok(TangoTestSupport.sample), TangoCodec.decode(verified.puzzle.payload))
    }

    @Test
    fun `the fixture board analyzes to the sample solution`() {
        val verdict = TangoEditor.analyze(sample, Budget.Unlimited)

        assertEquals(solution, (verdict as Verdict.Unique).solution)
    }

    @Test
    fun `a newer share version on the fixture needs an update`() {
        val result = SharedOpen.verify(TangoEditor, TangoEditor.shareFormatVersion, fixture().also { it[0] = 3 }, Budget.Unlimited)

        assertEquals(SharedOpenResult.NeedsUpdate(found = 3, maxKnown = 2), result)
    }
}
