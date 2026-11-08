package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.SharedOpen
import com.ivandsky.puzzlebox.game.editor.SharedOpenResult
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor's variant guards share decode, as it guards draft decode: a crafted doc can carry
 * another size's payload under this game's id, and the friend's open must fail its check on the
 * typed size error instead of crashing the analyzer on a board it cannot hold.
 */
class SudokuEditorShareDecodeTest {
    private val mini = SudokuEditor(SudokuGames.MINI)
    private val classic = SudokuEditor(SudokuGames.CLASSIC)

    /** Share bytes of a board of [size] with one given, as a crafted doc carries. */
    private fun shareBytes(size: Int): ByteArray =
        SudokuShareCodec.encodeShare(SudokuDraft(size, List(size * size) { if (it == 0) 1 else 0 }))

    private fun error(bytes: ByteArray): DecodeError = (mini.decodeShare(bytes) as DecodeResult.Err).error

    @Test
    fun `a mini editor rejects a classic-size share payload`() {
        assertEquals(DecodeError.VariantSizeMismatch("sudoku/6x6", 6, 9), error(shareBytes(9)))
    }

    @Test
    fun `a classic editor rejects a mini-size share payload`() {
        val decoded = classic.decodeShare(shareBytes(6))

        assertEquals(
            DecodeError.VariantSizeMismatch("sudoku/9x9", 9, 6),
            (decoded as DecodeResult.Err).error,
        )
    }

    @Test
    fun `opening a foreign-size payload fails its check instead of crashing`() {
        val result = SharedOpen.verify(mini, mini.shareFormatVersion, shareBytes(9), Budget.Unlimited)

        assertEquals(SharedOpenResult.FailedCheck, result)
    }

    @Test
    fun `an own-size payload still decodes under the same editor`() {
        assertTrue(mini.decodeShare(shareBytes(6)) is DecodeResult.Ok)
        assertTrue(classic.decodeShare(shareBytes(9)) is DecodeResult.Ok)
    }
}
