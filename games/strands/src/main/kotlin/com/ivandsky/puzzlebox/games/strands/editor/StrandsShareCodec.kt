package com.ivandsky.puzzlebox.games.strands.editor

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.EditorShareCodec
import com.ivandsky.puzzlebox.games.strands.codec.ByteCursor
import com.ivandsky.puzzlebox.games.strands.codec.ByteWriter

/**
 * Versioned paths-free Strands share bytes (ADR-0005, ADR-0020); varints are unsigned LEB128,
 * strings varint-length-prefixed UTF-8:
 *
 * ```
 * v1: [u8 version][string letters][string clue][varint n][n × string word][zigzag spangram]
 * ```
 *
 * No paths and never a tiling: the recipient's analyzer recovers it from the words, and
 * [StrandsEditor.toPuzzle] re-bakes the valid words from the letters, so neither travels.
 * Every word is named; an unnamed path is written empty for the encoder to stay total and is
 * rejected as malformed on decode.
 */
internal object StrandsShareCodec : EditorShareCodec<StrandsDraft> {
    override val shareFormatVersion: Int = 1

    private const val CELLS = StrandsDraft.ROWS * StrandsDraft.COLS

    /** Same cap the author's publish gate enforces ([StrandsPublishGates]); a crafted clue is cut, not rejected. */
    private const val MAX_CLUE = 40

    override fun encodeShare(draft: StrandsDraft): ByteArray {
        val out = ByteWriter()
        out.u8(shareFormatVersion)
        out.string(draft.letters)
        out.string(draft.clue)
        out.varint(draft.paths.size)
        draft.paths.forEach { out.string(it.word.orEmpty()) }
        out.zigzag(draft.spangram.toLong())
        return out.toByteArray()
    }

    /** version byte > 1 ⇒ DecodeError.UnsupportedVersion(found, 1); any other defect ⇒ Malformed. */
    override fun decodeShare(bytes: ByteArray): DecodeResult<StrandsDraft> {
        val cursor = ByteCursor(bytes)
        val version = cursor.u8() ?: return malformed("empty share bytes")
        if (version > shareFormatVersion) {
            return DecodeResult.Err(DecodeError.UnsupportedVersion(found = version, maxKnown = shareFormatVersion))
        }
        if (version != shareFormatVersion) return malformed("share version $version was never written")
        val letters = cursor.string()?.takeIf { it.length == CELLS } ?: return malformed("letters are not $CELLS cells")
        val clue = cursor.string()?.take(MAX_CLUE) ?: return malformed("truncated clue")
        val count = cursor.count() ?: return malformed("truncated word count")
        val paths = List(count) {
            val word = cursor.string()?.takeIf { it.isNotBlank() } ?: return malformed("word $it missing or blank")
            StrandsDraftPath(emptyList(), word)
        }
        val spangram = cursor.zigzag()?.takeIf { it in StrandsDraft.NO_SPANGRAM.toLong() until count }
            ?: return malformed("spangram outside the words")
        if (!cursor.isExhausted) return malformed("trailing bytes")
        return DecodeResult.Ok(StrandsDraft(letters, clue, paths, spangram.toInt()))
    }

    private fun malformed(detail: String): DecodeResult.Err = DecodeResult.Err(DecodeError.Malformed(detail))
}
