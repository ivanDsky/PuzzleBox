package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.model.GameId

/**
 * The index pass's resume file: every number the pass has settled, so a rerun fetches only the rest.
 * It holds numbers, technique ids and counts and skip reasons, never puzzle content.
 *
 * The file is a header line, then one line per settled number, appended as it settles:
 * ```
 * puzzle-cli-index-checkpoint 1
 * queens 101 ok line-lock=2 singles=4
 * queens 102 skip NotFound #102 for gameTypeId 3 is outside #1..#101
 * ```
 * [parse] drops a malformed last line, or one with no newline (the run was killed mid-write, [tornLine]); a malformed line
 * anywhere else, or another header version, is an error and nothing is fetched.
 */
class IndexCheckpoint private constructor(
    private val settled: Map<GameId, Map<Int, Settlement>>,
    /** The malformed last line [parse] dropped, or null when the file was whole. */
    val tornLine: String?,
) {

    /** The settlement of [number] of [game], or null when it is not settled yet. */
    fun settlement(game: GameId, number: Int): Settlement? = settled[game]?.get(number)

    /** This checkpoint with [number] of [game] settled as [settlement]. */
    fun with(game: GameId, number: Int, settlement: Settlement): IndexCheckpoint =
        IndexCheckpoint(settled + (game to ((settled[game] ?: emptyMap()) + (number to settlement))), null)

    /** The whole file text: header, then the settled numbers by game and number, each line ending in a newline. */
    fun text(): String = buildString {
        append(HEADER).append('\n')
        for ((game, numbers) in settled) {
            for ((number, settlement) in numbers.toSortedMap()) append(line(game, number, settlement)).append('\n')
        }
    }

    companion object {
        private const val MAGIC = "puzzle-cli-index-checkpoint"
        private const val VERSION = 1
        private const val HEADER = "$MAGIC $VERSION"
        private val ID = Regex("[a-z0-9-]+")

        /** A checkpoint with nothing settled. */
        fun empty(): IndexCheckpoint = IndexCheckpoint(emptyMap(), null)

        /** The line that records [number] of [game] settling as [settlement]; no newline, one line. */
        fun line(game: GameId, number: Int, settlement: Settlement): String = when (settlement) {
            is Settlement.Ok -> buildString {
                append(game.value).append(' ').append(number).append(" ok")
                for ((id, count) in settlement.counts.entries.sortedBy { it.key.value }) {
                    append(' ').append(id.value).append('=').append(count)
                }
            }
            is Settlement.Skip ->
                "${game.value} $number skip ${settlement.kind} ${oneLine(settlement.detail)}".trimEnd()
        }

        /** [detail] with every line break and run of spaces folded to one space. */
        fun oneLine(detail: String): String = detail.trim().replace(Regex("\\s+"), " ")

        /**
         * The checkpoint [text] holds; blank text is an empty checkpoint. A header of a different
         * version is [DecodeError.UnsupportedVersion]; any other malformed line, except the last, is
         * [DecodeError.Malformed]. A malformed last line is dropped and reported as [tornLine].
         */
        fun parse(text: String): DecodeResult<IndexCheckpoint> {
            val lines = text.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return DecodeResult.Ok(empty())
            val header = lines.first().split(' ')
            if (header.firstOrNull() != MAGIC || header.size != 2) {
                return DecodeResult.Err(DecodeError.Malformed("bad checkpoint header '${lines.first()}'"))
            }
            val version = header[1].toIntOrNull()
                ?: return DecodeResult.Err(DecodeError.Malformed("bad checkpoint version '${header[1]}'"))
            if (version != VERSION) return DecodeResult.Err(DecodeError.UnsupportedVersion(version, VERSION))
            val settled = LinkedHashMap<GameId, MutableMap<Int, Settlement>>()
            var torn: String? = null
            val body = lines.drop(1)
            for ((at, line) in body.withIndex()) {
                // A last line with no newline was cut short even when its prefix parses ("=1" of "=12").
                val entry = if (at == body.lastIndex && !text.endsWith("\n")) null else entry(line)
                if (entry == null) {
                    if (at != body.lastIndex) return DecodeResult.Err(DecodeError.Malformed("bad checkpoint line '$line'"))
                    torn = line
                    break
                }
                val (game, number, settlement) = entry
                settled.getOrPut(game) { LinkedHashMap() }[number] = settlement
            }
            return DecodeResult.Ok(IndexCheckpoint(settled, torn))
        }

        private fun entry(line: String): Triple<GameId, Int, Settlement>? {
            val tokens = line.split(' ')
            if (tokens.size < 3) return null
            val game = tokens[0].takeIf { ID.matches(it) } ?: return null
            val number = tokens[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            val settlement = when (tokens[2]) {
                "ok" -> counts(tokens.drop(3))?.let { Settlement.Ok(it) }
                "skip" -> skip(tokens.drop(3))
                else -> null
            } ?: return null
            return Triple(GameId(game), number, settlement)
        }

        private fun counts(pairs: List<String>): Map<TechniqueId, Int>? {
            if (pairs.isEmpty()) return null
            val counts = LinkedHashMap<TechniqueId, Int>()
            for (pair in pairs) {
                val id = pair.substringBefore('=')
                val count = pair.substringAfter('=', "").toIntOrNull()?.takeIf { it >= 1 } ?: return null
                if (!ID.matches(id) || counts.put(TechniqueId(id), count) != null) return null
            }
            return counts
        }

        private fun skip(rest: List<String>): Settlement? {
            val kind = SkipKind.entries.firstOrNull { it.name == rest.firstOrNull() } ?: return null
            return Settlement.Skip(kind, rest.drop(1).joinToString(" "))
        }
    }
}
