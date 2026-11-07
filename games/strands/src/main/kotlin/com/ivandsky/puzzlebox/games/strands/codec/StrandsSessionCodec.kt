package com.ivandsky.puzzlebox.games.strands.codec

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.game.PuzzleKeyTag
import com.ivandsky.puzzlebox.games.strands.ActiveHint
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzleRef
import com.ivandsky.puzzlebox.games.strands.StrandsSession
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.model.PuzzleKey

/**
 * Strands session codec (A30). The puzzle rides by value (nested [StrandsCodec] bytes,
 * ZipSessionCodec precedent) so an in-progress day survives offline. v2 (ADR-0018, spec A11)
 * tags the ref with its [PuzzleKey] kind; v1 refs are untagged dailies and still decode.
 * Unknown versions → UnsupportedVersion. Counts are varints:
 *
 * ```
 * u8      formatVersion = 2
 * bytes   puzzle                        (varint length + StrandsCodec bytes)
 * u8      flags                         (bit0 ref, bit1 daily number, bit2 active hint)
 * [bit0]  u8 keyTag (absent in v1: read as 0), then
 *         0 Daily  zigzag epochDay; [bit1] zigzag puzzleNumber
 *         1 Custom u8 idLen (1..255); idLen x u8 puzzleId (UTF-8); bit1 must be clear
 * varint  foundCount; foundCount x string     (find order)
 * varint  nonThemeCount; nonThemeCount x string (insertion order)
 * varint  hintsUsed
 * [bit2]  string hint word, u8 stage ordinal
 * varint  elapsedMs
 * ```
 */
object StrandsSessionCodec : GameSessionCodec<StrandsSession> {

    const val VERSION: Int = 2

    /** The oldest format still readable (v1: untagged daily ref). */
    private const val V1: Int = 1

    private const val FLAG_REF = 1
    private const val FLAG_NUMBER = 2
    private const val FLAG_HINT = 4

    /** The first format whose ref carries a [PuzzleKeyTag]; v1 refs are untagged dailies. */
    private const val V_KEY_TAG: Int = 2

    override fun serialize(session: StrandsSession): ByteArray {
        val state = session.state
        val key = session.ref?.key
        val hint = state.activeHint
        var flags = 0
        if (key != null) flags = flags or FLAG_REF
        if (key is PuzzleKey.Daily && key.puzzleNumber != null) flags = flags or FLAG_NUMBER
        if (hint != null) flags = flags or FLAG_HINT

        val out = ByteWriter()
        out.u8(VERSION)
        out.bytes(StrandsCodec.encode(state.puzzle))
        out.u8(flags)
        key?.let { writeKey(out, it) }
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
        if (version !in V1..VERSION) {
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
            when (val key = readKey(cursor, version, hasNumber = flags and FLAG_NUMBER != 0)) {
                is DecodeResult.Ok -> StrandsPuzzleRef(key.value)
                is DecodeResult.Err -> return key
            }
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

    /** The Daily number is not flagged here: flag bit1 says whether it follows the date. */
    private fun writeKey(out: ByteWriter, key: PuzzleKey) =
        PuzzleKeyTag.write(key, out::u8) { daily ->
            out.zigzag(daily.date.toEpochDay())
            daily.puzzleNumber?.let { out.zigzag(it.toLong()) }
        }

    private fun readKey(cursor: ByteCursor, version: Int, hasNumber: Boolean): DecodeResult<PuzzleKey> {
        val key = PuzzleKeyTag.read(version, taggedSince = V_KEY_TAG, u8 = cursor::u8) {
            val epochDay = cursor.zigzag() ?: return@read malformed("truncated ref date")
            val number = if (hasNumber) cursor.zigzag() ?: return@read malformed("truncated ref number") else null
            PuzzleKeyTag.daily(epochDay, number)
        }
        return if (hasNumber && key.getOrNull() is PuzzleKey.Custom) malformed("number on a custom ref") else key
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
