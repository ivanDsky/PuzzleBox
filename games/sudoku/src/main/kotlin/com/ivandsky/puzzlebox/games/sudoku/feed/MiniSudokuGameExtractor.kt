package com.ivandsky.puzzlebox.games.sudoku.feed

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Finds the Mini Sudoku puzzle in any LinkedIn body: the inline GraphQL response, the
 * normalized `included` form, or a `$metadata` + `value` envelope (research 01).
 *
 * The body is scanned for the data-bearing `miniSudokuGamePuzzle` object wherever it sits.
 * An occurrence counts only when the brace-matched region parses as a JSON object with
 * numeric `gridRowSize` and `gridColSize`: the key also appears as a type-map string and as a
 * null union member, and neither may win. Structural checks belong to [MiniSudokuWireParser].
 */
object MiniSudokuGameExtractor {

    const val KEY: String = "\"miniSudokuGamePuzzle\""

    // Mini Sudoku is LinkedIn gameTypeId 7; the urn's last component is the edition.
    private val entityUrnEdition = Regex("\"entityUrn\"\\s*:\\s*\"urn:li:fsd_game:\\([^\"]*?,7,(\\d+)\\)\"")
    private val puzzleIdEdition = Regex("\"puzzleId\"\\s*:\\s*(\\d+)")

    data class Match(
        /** The miniSudokuGamePuzzle object, verbatim. */
        val miniSudokuGamePuzzleJson: String,
        /** The edition number as text ("450"), when the body carried one. */
        val editionText: String?,
    )

    /** The first data-bearing miniSudokuGamePuzzle object in [body], or null. */
    fun extract(body: String): Match? {
        var from = 0
        while (true) {
            val keyIndex = body.indexOf(KEY, from)
            if (keyIndex < 0) return null
            val candidate = objectAfter(body, keyIndex + KEY.length)
            if (candidate != null && isDataObject(candidate)) {
                return Match(miniSudokuGamePuzzleJson = candidate, editionText = editionText(body))
            }
            from = keyIndex + KEY.length
        }
    }

    /**
     * The brace-balanced object that is the key's value, string-escape aware. Null when the
     * value is not an object (a string or null mention), so the scan moves on.
     */
    private fun objectAfter(text: String, valueSearchFrom: Int): String? {
        var start = valueSearchFrom
        while (start < text.length && (text[start].isWhitespace() || text[start] == ':')) start++
        if (start >= text.length || text[start] != '{') return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private fun isDataObject(json: String): Boolean = try {
        val obj = Json.parseToJsonElement(json) as? JsonObject
        obj != null && obj.isNumber("gridRowSize") && obj.isNumber("gridColSize")
    } catch (_: SerializationException) {
        false
    }

    private fun JsonObject.isNumber(key: String): Boolean {
        val value = get(key) as? JsonPrimitive ?: return false
        return !value.isString && value.content.toIntOrNull() != null
    }

    private fun editionText(body: String): String? =
        (entityUrnEdition.find(body) ?: puzzleIdEdition.find(body))?.groupValues?.get(1)
}
