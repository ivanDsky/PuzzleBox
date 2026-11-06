package com.ivandsky.puzzlebox.games.tango.feed

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Rotation-tolerant extraction of the Tango puzzle from any LinkedIn body (spec A10:
 * LinkedIn's live key is `lotkaGamePuzzle`, research 02). The key and the structural probe
 * live here and only here; nothing downstream inlines them.
 *
 * Like Zip's TrailGameExtractor: scan the raw body for the data-bearing `lotkaGamePuzzle`
 * member — wherever it sits (an RSC flight-stream line, a by-id GraphQL body, an SSR
 * `<script>` blob). An occurrence counts only when the key is followed by an object value
 * that parses as JSON with a numeric `gridSize`, so mentions of the key (the stream's
 * `"$case":"lotkaGamePuzzle"`, a by-id `"lotkaGamePuzzle": null` union member, SPA code)
 * never win. Structural validation stays with
 * [com.ivandsky.puzzlebox.games.tango.codec.LotkaWireParser].
 */
object TangoGameExtractor {

    /** LinkedIn's flight-stream and GraphQL object key for the Tango puzzle. */
    const val KEY: String = "lotkaGamePuzzle"

    private const val KEY_TOKEN = "\"$KEY\""
    private const val EDITION_KEY = "todaysGameEditionText"

    private val editionRegex = Regex("\"$EDITION_KEY\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

    /** Whatever the fetch produced for [com.ivandsky.puzzlebox.games.tango.codec.LotkaWireParser]. */
    data class Match(
        /** The lotkaGamePuzzle JSON object, verbatim wire shape. */
        val lotkaGamePuzzleJson: String,
        /** Daily number string ("759"), when the body carried it. */
        val editionText: String?,
    )

    /** First data-bearing lotkaGamePuzzle object in [body], or null. */
    fun extract(body: String): Match? {
        var from = 0
        while (true) {
            val keyIndex = body.indexOf(KEY_TOKEN, from)
            if (keyIndex < 0) return null
            val candidate = objectValueAfter(body, keyIndex + KEY_TOKEN.length)
            if (candidate != null && isDataObject(candidate)) {
                return Match(lotkaGamePuzzleJson = candidate, editionText = editionText(body))
            }
            from = keyIndex + KEY_TOKEN.length
        }
    }

    /**
     * The brace-balanced object that is this key's own value (`"key" : {…}`), string-escape
     * aware; null when the value is anything else — a later, unrelated object must never be
     * mistaken for it.
     */
    private fun objectValueAfter(text: String, afterKey: Int): String? {
        var start = skipWhitespace(text, afterKey)
        if (start >= text.length || text[start] != ':') return null
        start = skipWhitespace(text, start + 1)
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

    private fun skipWhitespace(text: String, from: Int): Int {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        return i
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
