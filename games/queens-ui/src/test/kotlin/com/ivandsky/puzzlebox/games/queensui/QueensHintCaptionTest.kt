package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.codec.QueensWireParser
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason
import com.ivandsky.puzzlebox.games.queens.engine.hints.QueensHint
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.LineLockRung
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.PigeonholeRung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Spec A9 (ticket 12): a hint found by a rung above singles is captioned by that rung and
 * tagged `queens.hint.caption.<rung.id>`; a singles hint keeps its unit-kind tag and caption.
 */
class QueensHintCaptionTest {

    private fun region(reason: HintReason?) = QueensHint.Region(region = 0, cell = Cell(0), reason = reason)

    @Test
    fun `a line-lock hint carries the line-lock caption tag`() {
        val hint = region(HintReason(HintReason.Kind.REGION, unit = 1, rung = LineLockRung))

        assertEquals("queens.hint.caption.line-lock", queensHintCaptionTag(hint))
        assertNotNull(queensHintCaption(hint))
    }

    @Test
    fun `a singles ROW hint keeps the ROW caption tag and today's caption`() {
        val singles = region(HintReason(HintReason.Kind.ROW, unit = 0))
        val lineLock = region(HintReason(HintReason.Kind.ROW, unit = 0, rung = LineLockRung))

        assertEquals("queens.hint.caption.ROW", queensHintCaptionTag(singles))
        assertEquals(
            "a ROW hint's caption depends on its rung",
            false,
            queensHintCaption(singles) == queensHintCaption(lineLock),
        )
    }

    @Test
    fun `each rung above singles has its own caption`() {
        val captions = listOf(LineLockRung, PigeonholeRung)
            .map { queensHintCaption(region(HintReason(HintReason.Kind.COLUMN, unit = 2, rung = it))) }

        assertEquals(captions.size, captions.toSet().size)
    }

    @Test
    fun `only a reasoned hint carries a caption tag`() {
        assertNull(queensHintCaptionTag(null))
        assertNull(queensHintCaptionTag(QueensHint.None))
        assertNull(queensHintCaptionTag(region(null)))
        assertNull(queensHintCaptionTag(QueensHint.Incorrect(listOf(Cell(0)))))
    }

    @Test
    fun `the first hint on the line-lock daily is captioned by the line-lock rung`() {
        val state = QueensBoardState(QueensSession(ref = null, puzzle = lineLockDaily()))

        state.requestHint()

        assertEquals("queens.hint.caption.line-lock", state.hintCaptionTag)
        assertNotNull(state.hintCaption)
    }

    private fun lineLockDaily() = checkNotNull(javaClass.getResource("/fixtures/queens-daily-line-lock.json"))
        .readText()
        .let { QueensWireParser.parse(it, LocalDate.parse("2026-11-06")) }
        .let { (it as DecodeResult.Ok).value.puzzle }
}
