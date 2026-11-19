package com.ivandsky.puzzlebox.game.trainer

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * The shipped technique table for one game's archive: per puzzle number, the steps each technique
 * takes when the game's [TechniqueWalker] solves it. Only ids and counts ship; no puzzle content,
 * no positions. [techniqueSetVersion] names the walker's technique set the pass ran with, and
 * [cutoff] is the last archive day the pass walked, so a later pass knows where to resume.
 *
 * [entries] is keyed by archive puzzle number. A puzzle mapped to an empty map was walked and
 * showed no listed technique; a number that is absent was not walked.
 *
 * The file is plain UTF-8 text ([write] / [read]), one line per puzzle, so the shipped resource
 * is diff-stable and reads without a serialization library:
 * ```
 * technique-index 1
 * game queens
 * technique-set 3
 * cutoff 2026-11-18
 * 101 line-lock=2 singles=4
 * 102 singles=6
 * 103
 * ```
 * Header lines come in that order, then one line per puzzle number ascending: the number, then
 * ` <id>=<count>` pairs sorted by id. [read] ignores blank lines.
 *
 * @property gameId the game whose archive this table covers
 * @property techniqueSetVersion version of the walker's technique set the pass ran with
 * @property cutoff the last archive day the pass walked
 * @property entries per archive puzzle number, the steps each technique takes (no zero counts)
 */
data class TechniqueIndex(
    val gameId: GameId,
    val techniqueSetVersion: Int,
    val cutoff: LocalDate,
    val entries: Map<Int, Map<TechniqueId, Int>>,
) {
    companion object {
        /** Layout version [write] stamps and [read] accepts; a newer file is [DecodeError.UnsupportedVersion]. */
        const val SCHEMA_VERSION = 1

        private const val MAGIC = "technique-index"
        private val ID = Regex("[a-z0-9-]+")

        /**
         * [index] as file text: header, then puzzles ascending with each puzzle's techniques sorted by
         * id, a trailing newline, byte-identical for equal indexes whatever order the maps iterate in.
         * A programming error (`require`), never a runtime input, when the game id is empty or holds
         * whitespace, the technique-set version or a puzzle number is negative, a technique id is not
         * `[a-z0-9-]+`, or a count is below 1.
         */
        fun write(index: TechniqueIndex): String {
            val game = index.gameId.value
            require(game.isNotEmpty() && game.none(Char::isWhitespace)) { "game id must be one word: '$game'" }
            require(index.techniqueSetVersion >= 0) { "technique-set version must not be negative" }
            return buildString {
                append(MAGIC).append(' ').append(SCHEMA_VERSION).append('\n')
                append("game ").append(game).append('\n')
                append("technique-set ").append(index.techniqueSetVersion).append('\n')
                append("cutoff ").append(index.cutoff.toString()).append('\n')
                for ((number, counts) in index.entries.toSortedMap()) {
                    require(number >= 0) { "puzzle number must not be negative: $number" }
                    append(number)
                    for ((id, count) in counts.entries.sortedBy { it.key.value }) {
                        require(ID.matches(id.value)) { "technique id must match [a-z0-9-]+: '${id.value}'" }
                        require(count >= 1) { "count for ${id.value} must be at least 1: $count" }
                        append(' ').append(id.value).append('=').append(count)
                    }
                    append('\n')
                }
            }
        }

        /**
         * The index [text] holds. A schema newer than [SCHEMA_VERSION] is
         * [DecodeError.UnsupportedVersion] (never half-read); anything else broken (missing or
         * misordered header, bad number, bad id, a count below 1, a puzzle number or technique listed
         * twice) is [DecodeError.Malformed]. Never throws.
         */
        fun read(text: String): DecodeResult<TechniqueIndex> {
            val lines = text.lines().filter { it.isNotBlank() }
            return try {
                val schema = number(header(lines, 0, MAGIC), "schema version")
                if (schema > SCHEMA_VERSION) {
                    return DecodeResult.Err(DecodeError.UnsupportedVersion(schema, SCHEMA_VERSION))
                }
                if (schema < 1) broken("schema version must be at least 1")
                val game = header(lines, 1, "game")
                if (game.isEmpty() || game.any(Char::isWhitespace)) broken("game id must be one word: '$game'")
                val techniqueSet = number(header(lines, 2, "technique-set"), "technique-set version")
                val cutoff = date(header(lines, 3, "cutoff"))
                val entries = LinkedHashMap<Int, Map<TechniqueId, Int>>()
                for (line in lines.drop(4)) {
                    val tokens = line.split(' ')
                    val puzzle = number(tokens[0], "puzzle number")
                    if (puzzle in entries) broken("puzzle $puzzle is listed twice")
                    entries[puzzle] = counts(puzzle, tokens.subList(1, tokens.size))
                }
                DecodeResult.Ok(TechniqueIndex(GameId(game), techniqueSet, cutoff, entries))
            } catch (broken: BrokenIndex) {
                DecodeResult.Err(DecodeError.Malformed(broken.detail))
            }
        }

        private fun header(lines: List<String>, at: Int, key: String): String {
            val line = lines.getOrNull(at) ?: broken("missing '$key' line")
            if (!line.startsWith("$key ")) broken("expected '$key <value>', found '$line'")
            return line.substring(key.length + 1)
        }

        private fun number(token: String, what: String): Int {
            if (token.isEmpty() || !token.all { it in '0'..'9' }) broken("bad $what '$token'")
            return token.toIntOrNull() ?: broken("$what '$token' is out of range")
        }

        private fun date(token: String): LocalDate = try {
            LocalDate.parse(token)
        } catch (_: DateTimeParseException) {
            broken("bad cutoff '$token'")
        }

        private fun counts(puzzle: Int, pairs: List<String>): Map<TechniqueId, Int> {
            val counts = LinkedHashMap<TechniqueId, Int>()
            for (pair in pairs) {
                val eq = pair.indexOf('=')
                if (eq < 0) broken("puzzle $puzzle: expected '<id>=<count>', found '$pair'")
                val id = pair.substring(0, eq)
                if (!ID.matches(id)) broken("puzzle $puzzle: bad technique id '$id'")
                val count = number(pair.substring(eq + 1), "count for $id in puzzle $puzzle")
                if (count < 1) broken("puzzle $puzzle: count for $id must be at least 1")
                if (counts.put(TechniqueId(id), count) != null) broken("puzzle $puzzle: $id is listed twice")
            }
            return counts
        }

        private fun broken(detail: String): Nothing = throw BrokenIndex(detail)
    }
}

/** Internal control flow of [TechniqueIndex.read]: carries the detail up to its one catch. */
private class BrokenIndex(val detail: String) : Exception(detail)
