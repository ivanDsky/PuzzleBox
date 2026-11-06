package com.ivandsky.puzzlebox.games.strands.codec

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.games.strands.ActiveHint
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzleRef
import com.ivandsky.puzzlebox.games.strands.StrandsSession
import com.ivandsky.puzzlebox.games.strands.StrandsState
import java.time.LocalDate

/**
 * Strands session codec v1 (A30). The puzzle rides by value (nested [StrandsCodec] bytes,
 * ZipSessionCodec precedent) so an in-progress day survives offline. Counts are varints:
 *
 * ```
 * u8      formatVersion = 1
 * bytes   puzzle                        (varint length + StrandsCodec bytes)
 * u8      flags                         (bit0 ref, bit1 ref number, bit2 active hint)
 * [bit0]  zigzag epochDay; [bit1] zigzag puzzleNumber
 * varint  foundCount; foundCount x string     (find order)
 * varint  nonThemeCount; nonThemeCount x string (insertion order)
 * varint  hintsUsed
 * [bit2]  string hint word, u8 stage ordinal
 * varint  elapsedMs
 * ```
 */
object StrandsSessionCodec : GameSessionCodec<StrandsSession> {

    const val VERSION: Int = 1

    private const val FLAG_REF = 1
    private const val FLAG_NUMBER = 2
    private const val FLAG_HINT = 4

    override fun serialize(session: StrandsSession): ByteArray {
        val state = session.state
        val ref = session.ref
        val hint = state.activeHint
        var flags = 0
        if (ref != null) flags = flags or FLAG_REF
        if (ref?.puzzleNumber != null) flags = flags or FLAG_NUMBER
        if (hint != null) flags = flags or FLAG_HINT

        val out = ByteWriter()
        out.u8(VERSION)
        out.bytes(StrandsCodec.encode(state.puzzle))
        out.u8(flags)
        if (ref != null) {
            out.zigzag(ref.date.toEpochDay())
            ref.puzzleNumber?.let { out.zigzag(it.toLong()) }
        }
        out.varint(state.found.size)
        state.found.forEach(out::string)
        out.varint(state.nonTheme.size)
        state.nonTheme.forEach(out::string)
        out.varint(state.hintsUsed)
        if (hint != null) {
            out.string(hint.word)
            out.u8(hint.stage.ordinal)
        }
        out.varint(session.elapsedMs)
        return out.toByteArray()
    }

    override fun deserialize(bytes: ByteArray): DecodeResult<StrandsSession> {
        val cursor = ByteCursor(bytes)
        val version = cursor.u8() ?: return malformed("empty session blob")
        if (version != VERSION) {
            return DecodeResult.Err(DecodeError.UnsupportedVersion(found = version, maxKnown = VERSION))
        }
        val puzzleBytes = cursor.bytes() ?: return malformed("truncated puzzle blob")
        val puzzle = when (val decoded = StrandsCodec.decode(puzzleBytes)) {
            is DecodeResult.Ok -> decoded.value
            is DecodeResult.Err -> return decoded
        }
        val targets = puzzle.themeWords.map { it.text }.toSet() + puzzle.spangram.text

        val flags = cursor.u8() ?: return malformed("truncated flags")
        if (flags and (FLAG_REF or FLAG_NUMBER or FLAG_HINT).inv() != 0) return malformed("unknown flags $flags")
        if (flags and FLAG_NUMBER != 0 && flags and FLAG_REF == 0) return malformed("number without ref")
        val ref = if (flags and FLAG_REF != 0) {
            val epochDay = cursor.zigzag() ?: return malformed("truncated ref date")
            if (epochDay !in LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay()) return malformed("ref date out of range")
            val number = if (flags and FLAG_NUMBER != 0) {
                cursor.zigzag()?.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
                    ?: return malformed("bad ref number")
            } else {
                null
            }
            StrandsPuzzleRef(LocalDate.ofEpochDay(epochDay), number)
        } else {
            null
        }

        val found = cursor.distinctStrings() ?: return malformed("malformed found words")
        if (!targets.containsAll(found)) return malformed("found word is no theme word or spangram")
        val nonTheme = cursor.distinctStrings() ?: return malformed("malformed non-theme words")
        val hintsUsed = cursor.int() ?: return malformed("bad hintsUsed")
        val activeHint = if (flags and FLAG_HINT != 0) {
            val word = cursor.string()?.takeIf { it in targets } ?: return malformed("bad hint word")
            val stage = cursor.u8()?.let(HintStage.entries::getOrNull) ?: return malformed("bad hint stage")
            ActiveHint(word, stage)
        } else {
            null
        }
        val elapsedMs = cursor.varint() ?: return malformed("truncated elapsedMs")
        if (!cursor.isExhausted) return malformed("trailing bytes")

        return DecodeResult.Ok(
            StrandsSession(
                ref = ref,
                state = StrandsState(puzzle, found.toList(), nonTheme, hintsUsed, activeHint),
                elapsedMs = elapsedMs,
            ),
        )
    }

    /** Null on truncation or a repeated word; keeps the stored order. */
    private fun ByteCursor.distinctStrings(): Set<String>? {
        val count = count() ?: return null
        val out = LinkedHashSet<String>(count)
        repeat(count) { if (!out.add(string() ?: return null)) return null }
        return out
    }

    private fun malformed(detail: String): DecodeResult.Err = DecodeResult.Err(DecodeError.Malformed(detail))
}
