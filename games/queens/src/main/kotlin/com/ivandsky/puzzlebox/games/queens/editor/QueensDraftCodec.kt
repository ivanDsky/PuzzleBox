package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.queensVariantId

/**
 * Versioned Queens draft bytes (ADR-0005): `[u8 VERSION][u8 size][size² × u8 region]`, an
 * unpainted cell stored as [UNPAINTED_BYTE]. Version 1 holds the board only; the history
 * section lands in ticket 03 under the same version, so [encode] drops the history for now
 * and [decode] restores an empty one. Unknown versions and sizes are rejected, never guessed.
 */
object QueensDraftCodec {
    const val VERSION: Int = 1

    /** Undo/redo steps the history section keeps (the section is filled in ticket 03). */
    const val MAX_HISTORY: Int = 10_000

    private const val HEADER_BYTES = 2
    private const val UNPAINTED_BYTE = 0xFF

    fun encode(state: DraftState<QueensDraft>): ByteArray {
        val draft = state.draft
        val bytes = ByteArray(HEADER_BYTES + draft.regions.size)
        bytes[0] = VERSION.toByte()
        bytes[1] = draft.size.toByte()
        draft.regions.forEachIndexed { idx, region ->
            bytes[HEADER_BYTES + idx] = (if (region == QueensDraft.UNPAINTED) UNPAINTED_BYTE else region).toByte()
        }
        return bytes
    }

    fun decode(bytes: ByteArray): DecodeResult<DraftState<QueensDraft>> {
        if (bytes.size < HEADER_BYTES) return malformed("truncated header: ${bytes.size} bytes")
        val version = bytes[0].toInt() and 0xFF
        if (version != VERSION) return DecodeResult.Err(DecodeError.UnsupportedVersion(version, VERSION))
        val size = bytes[1].toInt() and 0xFF
        val variant = queensVariantId(size)
        if (QueensVariants.byId(variant) == null) return DecodeResult.Err(DecodeError.UnknownVariant(variant.value))
        val cells = size * size
        if (bytes.size != HEADER_BYTES + cells) {
            return malformed("expected ${HEADER_BYTES + cells} bytes for size $size, got ${bytes.size}")
        }
        val regions = ArrayList<Int>(cells)
        for (idx in 0 until cells) {
            val raw = bytes[HEADER_BYTES + idx].toInt() and 0xFF
            when {
                raw == UNPAINTED_BYTE -> regions.add(QueensDraft.UNPAINTED)
                raw < size -> regions.add(raw)
                else -> return malformed("cell $idx region $raw outside 0 until $size")
            }
        }
        return DecodeResult.Ok(DraftState(QueensDraft(size, regions)))
    }

    private fun malformed(detail: String): DecodeResult<Nothing> = DecodeResult.Err(DecodeError.Malformed(detail))
}
