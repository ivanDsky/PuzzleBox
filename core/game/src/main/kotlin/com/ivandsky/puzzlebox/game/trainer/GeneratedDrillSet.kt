package com.ivandsky.puzzlebox.game.trainer

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import java.security.MessageDigest
import java.util.Base64

/**
 * The bundled drill puzzles of one game: puzzles a generator made for techniques the archive index is short on, kept
 * only after the game's [TechniqueWalker] proved the technique in them. Each [Drill] carries the whole puzzle, so it
 * plays offline with no fetch, and an immutable [Drill.id] that keys it for as long as a shipped set carries it.
 * [techniqueSetVersion] names the walker's technique set the pass ran with.
 *
 * The file is plain UTF-8 text ([write] / [read]), one line per drill, diff-stable like [TechniqueIndex]:
 * ```
 * generated-drills 1
 * game minisudoku
 * technique-set 1
 * g-3f2a9c1d0b7e pointing=2 sudoku/6x6 1 AQpzdWRva3UvNng2...
 * g-91c4e0aa5d32 pointing=3 sudoku/6x6 1 AQpzdWRva3UvNng2...
 * ```
 * Header lines come in that order, then one line per drill sorted by technique id, then drill id: the drill id, then
 * `<technique>=<target steps>`, the puzzle's variant, its payload format version and the payload in base64. [read]
 * ignores blank lines and does not require the sorted order.
 *
 * @property gameId the game every drill belongs to
 * @property techniqueSetVersion version of the walker's technique set the pass ran with
 * @property drills the bundled puzzles
 */
data class GeneratedDrillSet(
    val gameId: GameId,
    val techniqueSetVersion: Int,
    val drills: List<Drill>,
) {
    /**
     * One bundled puzzle. [id] is [idOf] of the puzzle's payload; [targetSteps] is how many steps [technique] takes
     * in the puzzle, the figure the drill source orders by.
     */
    data class Drill(val id: String, val technique: TechniqueId, val targetSteps: Int, val puzzle: Puzzle)

    companion object {
        /** Layout version [write] stamps and [read] accepts; a newer file is [DecodeError.UnsupportedVersion]. */
        const val SCHEMA_VERSION = 1

        private const val MAGIC = "generated-drills"
        private const val ID_PREFIX = "g-"
        private const val ID_HEX_LENGTH = 12
        private val TECHNIQUE = Regex("[a-z0-9-]+")
        private val HEX = "0123456789abcdef".toCharArray()

        /**
         * The immutable id of a drill whose puzzle holds [payload]: `g-` and the first 12 hex digits of the payload's
         * SHA-256. The same puzzle always gets the same id, whichever run generated it.
         */
        fun idOf(payload: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(payload)
            return buildString {
                append(ID_PREFIX)
                for (i in 0 until ID_HEX_LENGTH) {
                    val nibble = (digest[i / 2].toInt() shr (if (i % 2 == 0) 4 else 0)) and 0xF
                    append(HEX[nibble])
                }
            }
        }

        /**
         * [set] as file text: header, then drills sorted by technique id and drill id, a trailing newline,
         * byte-identical for equal sets whatever order the list holds. A programming error (`require`), never a
         * runtime input, when the game id is empty or holds whitespace, the technique-set version is negative, a
         * drill is another game's, its id is not [idOf] its payload, ids repeat, a technique id is not
         * `[a-z0-9-]+`, a target count is below 1, or a variant id is empty or holds whitespace.
         */
        fun write(set: GeneratedDrillSet): String {
            val game = set.gameId.value
            require(game.isNotEmpty() && game.none(Char::isWhitespace)) { "game id must be one word: '$game'" }
            require(set.techniqueSetVersion >= 0) { "technique-set version must not be negative" }
            val seen = HashSet<String>()
            return buildString {
                append(MAGIC).append(' ').append(SCHEMA_VERSION).append('\n')
                append("game ").append(game).append('\n')
                append("technique-set ").append(set.techniqueSetVersion).append('\n')
                for (drill in set.drills.sortedWith(compareBy<Drill>({ it.technique.value }, { it.id }))) {
                    val puzzle = drill.puzzle
                    require(puzzle.gameId == set.gameId) { "drill ${drill.id} is ${puzzle.gameId}'s, not $game's" }
                    require(drill.id == idOf(puzzle.payload)) { "drill id ${drill.id} is not the id of its payload" }
                    require(seen.add(drill.id)) { "drill ${drill.id} is listed twice" }
                    require(TECHNIQUE.matches(drill.technique.value)) {
                        "technique id must match [a-z0-9-]+: '${drill.technique.value}'"
                    }
                    require(drill.targetSteps >= 1) { "target steps of ${drill.id} must be at least 1: ${drill.targetSteps}" }
                    val variant = puzzle.variant.value
                    require(variant.isNotEmpty() && variant.none(Char::isWhitespace)) { "variant must be one word: '$variant'" }
                    append(drill.id).append(' ')
                    append(drill.technique.value).append('=').append(drill.targetSteps).append(' ')
                    append(variant).append(' ').append(puzzle.formatVersion).append(' ')
                    append(Base64.getEncoder().encodeToString(puzzle.payload)).append('\n')
                }
            }
        }

        /**
         * The set [text] holds. A schema newer than [SCHEMA_VERSION] is [DecodeError.UnsupportedVersion] (never
         * half-read); anything else broken (missing or misordered header, a line that is not five words, a drill id
         * that is not the id of its payload, bad base64, a bad technique id, a target count below 1, an id listed
         * twice) is [DecodeError.Malformed]. Never throws.
         */
        fun read(text: String): DecodeResult<GeneratedDrillSet> {
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
                val gameId = GameId(game)
                val ids = HashSet<String>()
                val drills = lines.drop(3).map { drill(gameId, it) }
                for (drill in drills) if (!ids.add(drill.id)) broken("drill ${drill.id} is listed twice")
                DecodeResult.Ok(GeneratedDrillSet(gameId, techniqueSet, drills))
            } catch (broken: BrokenDrillSet) {
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

        private fun drill(gameId: GameId, line: String): Drill {
            val words = line.split(' ')
            if (words.size != 5) broken("expected '<id> <technique>=<steps> <variant> <format> <payload>', found '$line'")
            val id = words[0]
            val target = words[1]
            val eq = target.indexOf('=')
            if (eq < 0) broken("drill $id: expected '<technique>=<steps>', found '$target'")
            val technique = target.substring(0, eq)
            if (!TECHNIQUE.matches(technique)) broken("drill $id: bad technique id '$technique'")
            val steps = number(target.substring(eq + 1), "target steps of $id")
            if (steps < 1) broken("drill $id: target steps must be at least 1")
            val variant = words[2]
            if (variant.isEmpty()) broken("drill $id: variant is empty")
            val format = number(words[3], "format version of $id")
            val payload = try {
                Base64.getDecoder().decode(words[4])
            } catch (_: IllegalArgumentException) {
                broken("drill $id: payload is not base64")
            }
            if (id != idOf(payload)) broken("drill $id: the id is not the id of its payload")
            return Drill(id, TechniqueId(technique), steps, Puzzle(gameId, VariantId(variant), format, payload))
        }

        private fun broken(detail: String): Nothing = throw BrokenDrillSet(detail)
    }
}

/** Internal control flow of [GeneratedDrillSet.read]: carries the detail up to its one catch. */
private class BrokenDrillSet(val detail: String) : Exception(detail)
