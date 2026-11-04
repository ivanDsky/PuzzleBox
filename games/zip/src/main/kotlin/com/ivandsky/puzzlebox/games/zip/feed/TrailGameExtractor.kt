package com.ivandsky.puzzlebox.games.zip.feed

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Rotation-tolerant extraction of the Zip puzzle from any LinkedIn body (research 01 §4:
 * "the puzzle payload's API path has moved before, so accept any LinkedIn JSON response").
 *
 * No pinned JSON path, no schema: the extractor scans the raw body for the data-bearing
 * `trailGamePuzzle` object — wherever it sits (an RSC flight-stream line, a bare JSON
 * response, an SSR `<script>` blob) — and for the edition text next to it. An occurrence
 * counts only when the brace-matched region parses as a JSON object containing a numeric
 * `gridSize`, so code/JS *mentions* of the key (the SPA shell) never win. Structural
 * validation stays with the engine's [com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser].
 */
object TrailGameExtractor {

    private const val KEY = "\"trailGamePuzzle\""
    private const val EDITION_KEY = "todaysGameEditionText"

    private val editionRegex = Regex("\"$EDITION_KEY\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

    /** Whatever the fetch produced for [com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser]. */
    data class Match(
        /** The trailGamePuzzle JSON object, verbatim wire shape. */
        val trailGamePuzzleJson: String,
        /** Daily number string ("592"), when the body carried it. */
        val editionText: String?,
    )

    /** First data-bearing trailGamePuzzle object in [body], or null. */
    fun extract(body: String): Match? {
        var from = 0
        while (true) {
            val keyIndex = body.indexOf(KEY, from)
            if (keyIndex < 0) return null
            val candidate = objectAfter(body, keyIndex)
            if (candidate != null && isDataObject(candidate)) {
                return Match(trailGamePuzzleJson = candidate, editionText = editionText(body))
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
