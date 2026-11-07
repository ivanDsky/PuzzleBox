package com.ivandsky.puzzlebox.game.editor

import com.ivandsky.puzzlebox.game.DecodeResult

/**
 * Capability: the draft can travel as a share payload. A game opts into sharing by implementing
 * this next to [DraftExport] on its editor; the recipient re-derives the solution ([SharedOpen]).
 */
interface EditorShareCodec<D : Any> {
    val shareFormatVersion: Int

    /** Share codec v1: board only, solution stripped (ADR-0020). */
    fun encodeShare(draft: D): ByteArray
    fun decodeShare(bytes: ByteArray): DecodeResult<D>
}
