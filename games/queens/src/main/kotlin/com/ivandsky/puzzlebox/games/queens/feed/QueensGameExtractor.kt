package com.ivandsky.puzzlebox.games.queens.feed

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Rotation-tolerant extraction of the Queens puzzle from any LinkedIn body (zip
 * TrailGameExtractor precedent; research 02-endpoint-wire-format.md: "the puzzle
 * payload's API path has moved before, so accept any LinkedIn JSON response").
 *
 * No pinned JSON path, no schema: the extractor scans the raw body — a GraphQL
 * `/voyager/api/graphql` response, a React Flight stream line, an SSR `<script>` blob —
 * for the data-bearing `queensGamePuzzle` object wherever it sits. An occurrence counts
 * only when the brace-matched region parses as a JSON object carrying a numeric
 * `gridSize`, so code/JS *mentions* of the key (the SPA shell) never win. Structural
 * validation stays with [com.ivandsky.puzzlebox.games.queens.codec.QueensWireParser].
 */
object QueensGameExtractor {

    private const val KEY = "\"queensGamePuzzle\""
    private const val EDITION_KEY = "todaysGameEditionText"

    private val editionRegex = Regex("\"$EDITION_KEY\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

    /** Whatever the fetch produced for [com.ivandsky.puzzlebox.games.queens.codec.QueensWireParser]. */
    data class Match(
        /** The queensGamePuzzle JSON object, verbatim wire shape. */
        val queensGamePuzzleJson: String,
        /** Daily number string ("592"), when the body carried it. */
        val editionText: String?,
    )

    /** First data-bearing queensGamePuzzle object in [body], or null. */
    fun extract(body: String): Match? {
        var from = 0
        while (true) {
            val keyIndex = body.indexOf(KEY, from)
            if (keyIndex < 0) return null
            val candidate = objectAfter(body, keyIndex)
            if (candidate != null && isDataObject(candidate)) {
                return Match(queensGamePuzzleJson = candidate, editionText = editionText(body))
            }
            from = keyIndex + KEY.length
        }
    }

    /** Brace-balanced JSON object following the key occurrence, string-escape aware. */
    private fun objectAfter(text: String, keyIndex: Int): String? {
        val start = text.indexOf('{', keyIndex)
        if (start < 0) return null
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

    /** Data, not a mention: parses as JSON and carries a numeric gridSize. */
    private fun isDataObject(json: String): Boolean = try {
        val obj = Json.parseToJsonElement(json) as? JsonObject
        (obj?.get("gridSize") as? JsonPrimitive)?.content?.toIntOrNull() != null
    } catch (_: kotlinx.serialization.SerializationException) {
        false
    }

    private fun editionText(body: String): String? = try {
        editionRegex.find(body)?.let { Json.parseToJsonElement("\"${it.groupValues[1]}\"").jsonPrimitive.content }
    } catch (_: kotlinx.serialization.SerializationException) {
        null
    }
}
