package com.ivandsky.puzzlebox.games.tango.codec

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Pure extraction of LinkedIn's `lotkaGamePuzzle` JSON -> typed [TangoPuzzle] (spec A7, A10;
 * research 02). The RSC flight framing and HTTP stay in the feed transport.
 *
 * Wire shape: `gridSize`; `solution` — the full row-major grid; `presetCellIdxes` — which
 * solution cells are givens; `edges{startIdx,endIdx,isEqual}` — the `=` (`isEqual`) / `×`
 * constraints. Both wire shapes parse alike: the stream decorates objects with proto `$type`
 * and prefixes cell values (`LotkaCellValue_ZERO`), the by-id GraphQL body decorates with
 * `$type`/`$recipeTypes` and sends bare `ZERO`/`ONE`. Unknown fields are ignored.
 *
 * ZERO is SUN and ONE is MOON: live #759's solution starts ZERO,ZERO,ONE where the community
 * archives (Loukis-13, awa.games; research 04-community-tango) show S,S,M, and every other
 * preset agrees. The solution's 3-in-a-row/balance parity cannot decide it — the rules are
 * symmetric under swapping the two values.
 *
 * Variant discipline (ADR-0005): the variant is derived from gridSize (`tango/n<n>`) within
 * [TangoPuzzle.MIN_N]..[TangoPuzzle.MAX_N]; the wire names no variant.
 */
object LotkaWireParser {

    /** Content + identity: identity arrives with the fetch (date + edition text). */
    data class TangoDaily(
        val puzzle: TangoPuzzle,
        val ref: TangoPuzzleRef,
    )

    /**
     * @param lotkaGamePuzzleJson the lotkaGamePuzzle JSON object (verbatim wire shape)
     * @param date the fetch day — the wire carries no ISO date
     * @param editionText the daily number string ("759"), null when the body carried none
     */
    fun parse(
        lotkaGamePuzzleJson: String,
        date: LocalDate,
        editionText: String? = null,
    ): DecodeResult<TangoDaily> = try {
        parse(Json.parseToJsonElement(lotkaGamePuzzleJson).jsonObject, date, editionText)
    } catch (e: kotlinx.serialization.SerializationException) {
        DecodeResult.Err(DecodeError.Malformed("invalid JSON: ${e.message}"))
    } catch (e: IllegalArgumentException) {
        DecodeResult.Err(DecodeError.Malformed("invalid JSON structure: ${e.message}"))
    }

    private fun parse(payload: JsonObject, date: LocalDate, editionText: String?): DecodeResult<TangoDaily> {
        val n = payload.int("gridSize")
            ?: return malformed("missing gridSize")
        if (n !in TangoPuzzle.MIN_N..TangoPuzzle.MAX_N) {
            return malformed("gridSize $n outside ${TangoPuzzle.MIN_N}..${TangoPuzzle.MAX_N}")
        }

        val solutionArray = payload["solution"] as? JsonArray
            ?: return malformed("missing solution")
        if (solutionArray.size != n * n) return malformed("solution has ${solutionArray.size} cells, expected ${n * n}")
        val solution = solutionArray.mapIndexed { idx, element ->
            val token = (element as? JsonPrimitive)?.content
            cellValue(token) ?: return malformed("solution[$idx]='$token' is not ZERO/ONE")
        }

        val givens = LinkedHashMap<Int, TangoCell>()
        (payload["presetCellIdxes"] as? JsonArray)?.forEachIndexed { i, element ->
            val idx = (element as? JsonPrimitive)?.content?.toIntOrNull()
            if (idx == null || idx !in 0 until n * n) return malformed("presetCellIdxes[$i]='$element' is off the board")
            givens[idx] = solution[idx]
        }

        val markers = ArrayList<TangoMarker>()
        (payload["edges"] as? JsonArray)?.forEachIndexed { i, element ->
            val edge = element as? JsonObject ?: return malformed("edge $i must be an object")
            val a = edge.int("startIdx") ?: return malformed("edge $i missing startIdx")
            val b = edge.int("endIdx") ?: return malformed("edge $i missing endIdx")
            val isEqual = edge.primitive("isEqual")?.toBooleanStrictOrNull()
                ?: return malformed("edge $i missing isEqual")
            if (a !in 0 until n * n || b !in 0 until n * n) return malformed("edge $i references a cell off the board")
            val low = minOf(a, b)
            val side = when (maxOf(a, b) - low) {
                1 -> TangoEdgeSide.RIGHT.takeIf { low % n != n - 1 }
                n -> TangoEdgeSide.DOWN
                else -> null
            } ?: return malformed("edge $i ($a-$b) is not an adjacent pair")
            markers += TangoMarker(low, side, if (isEqual) TangoMarkerKind.SAME else TangoMarkerKind.OPPOSITE)
        }

        return DecodeResult.Ok(
            TangoDaily(
                puzzle = TangoPuzzle(variant = VariantId("tango/n$n"), n = n, givens = givens, markers = markers),
                ref = TangoPuzzleRef(date = date, puzzleNumber = editionText?.toIntOrNull()),
            ),
        )
    }

    /** `ZERO`/`ONE`, with or without the stream's `LotkaCellValue_` enum prefix. */
    private fun cellValue(token: String?): TangoCell? = when (token?.removePrefix("LotkaCellValue_")) {
        "ZERO" -> TangoCell.SUN
        "ONE" -> TangoCell.MOON
        else -> null
    }

    private fun malformed(detail: String): DecodeResult.Err = DecodeResult.Err(DecodeError.Malformed(detail))

    private fun JsonObject.int(name: String): Int? = primitive(name)?.toIntOrNull()

    private fun JsonObject.primitive(name: String): String? = (this[name] as? JsonPrimitive)?.content
}
