package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

/**
 * The 756 real 6×6 LinkedIn dailies of `fixtures/tango-boards.json` (source and terms in
 * `TangoCorpusTest`) as drafts, in file order: what the scored grader is checked and measured on.
 */
internal object TangoDailyBoards {
    val all: List<TangoDraft> by lazy {
        val text = requireNotNull(javaClass.getResource("/fixtures/tango-boards.json")) { "tango-boards.json" }.readText()
        Json.parseToJsonElement(text).jsonArray.map { element ->
            val record = element.jsonObject
            val givens = record.getValue("giv").jsonPrimitive.content.withIndex().filter { it.value != '.' }
                .associate { (idx, cell) -> idx to if (cell == 'S') TangoCell.SUN else TangoCell.MOON }
            val markers = record.getValue("sg").jsonArray.map { edge ->
                val (a, b, kind) = edge.jsonArray
                val from = a.jsonPrimitive.int
                TangoMarker(
                    from,
                    if (b.jsonPrimitive.int == from + 1) TangoEdgeSide.RIGHT else TangoEdgeSide.DOWN,
                    if (kind.jsonPrimitive.content == "=") TangoMarkerKind.SAME else TangoMarkerKind.OPPOSITE,
                )
            }
            TangoDraft(record.getValue("n").jsonPrimitive.int, givens, markers)
        }
    }
}
