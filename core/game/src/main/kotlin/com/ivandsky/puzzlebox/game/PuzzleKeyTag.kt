package com.ivandsky.puzzlebox.game

import com.ivandsky.puzzlebox.model.PuzzleKey
import java.nio.charset.CharacterCodingException
import java.time.LocalDate

/**
 * The [PuzzleKey] in front of every session codec's ref: a u8 kind tag (0 Daily, 1 Custom),
 * then that kind's body. The Custom body is u8 idLen (1..255), then the id as UTF-8. The Daily
 * body is each codec's own, because its frozen fixtures pin the field widths; the codec reads
 * the raw fields and builds the key through [daily].
 *
 * Bytes travel through the codec's own cursor as `u8` lambdas, so no codec has to share a
 * cursor type. Anything that cannot be read back is Malformed, never thrown and never guessed.
 */
object PuzzleKeyTag {

    private const val DAILY = 0
    private const val CUSTOM = 1
    private const val MAX_ID_BYTES = 0xFF

    /** [afterTag] writes whatever a codec's layout keeps between the tag and the body. */
    fun write(key: PuzzleKey, u8: (Int) -> Unit, afterTag: () -> Unit = {}, daily: (PuzzleKey.Daily) -> Unit) {
        when (key) {
            is PuzzleKey.Daily -> {
                u8(DAILY)
                afterTag()
                daily(key)
            }

            is PuzzleKey.Custom -> {
                val id = key.puzzleId.encodeToByteArray()
                require(id.size in 1..MAX_ID_BYTES) { "puzzleId must be 1..$MAX_ID_BYTES UTF-8 bytes, got ${id.size}" }
                u8(CUSTOM)
                afterTag()
                u8(id.size)
                id.forEach { u8(it.toInt() and 0xFF) }
            }
        }
    }

    /**
     * Refs a codec wrote before [taggedSince] carry no tag and are always Daily. [afterTag]
     * reads what [write]'s hook wrote and returns false when that is malformed.
     */
    fun read(
        version: Int,
        taggedSince: Int,
        u8: () -> Int?,
        afterTag: () -> Boolean = { true },
        daily: () -> DecodeResult<PuzzleKey.Daily>,
    ): DecodeResult<PuzzleKey> {
        val tag = if (version < taggedSince) DAILY else u8() ?: return malformed("truncated key tag")
        if (tag != DAILY && tag != CUSTOM) return malformed("unknown key tag $tag")
        if (!afterTag()) return malformed("malformed key header")
        return if (tag == DAILY) daily() else readCustom(u8)
    }

    /** The Daily key for fields a codec read; a date [LocalDate] cannot hold or a number past Int is Malformed. */
    fun daily(epochDay: Long, number: Long?): DecodeResult<PuzzleKey.Daily> {
        if (epochDay !in LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay()) return malformed("key date $epochDay out of range")
        if (number != null && number !in Int.MIN_VALUE..Int.MAX_VALUE) return malformed("puzzle number $number out of range")
        return DecodeResult.Ok(PuzzleKey.Daily(LocalDate.ofEpochDay(epochDay), number?.toInt()))
    }

    private fun readCustom(u8: () -> Int?): DecodeResult<PuzzleKey> {
        val len = u8()?.takeIf { it > 0 } ?: return malformed("missing puzzleId length")
        val id = ByteArray(len)
        for (i in 0 until len) id[i] = (u8() ?: return malformed("truncated puzzleId")).toByte()
        // A lenient decode would swap bad bytes for U+FFFD and so read back a different id.
        val text = try {
            id.decodeToString(throwOnInvalidSequence = true)
        } catch (e: CharacterCodingException) {
            return malformed("puzzleId is not UTF-8: $e")
        }
        return DecodeResult.Ok(PuzzleKey.Custom(text))
    }

    private fun malformed(detail: String) = DecodeResult.Err(DecodeError.Malformed(detail))
}
