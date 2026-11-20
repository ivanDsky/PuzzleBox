package com.ivandsky.puzzlebox.games.queens.codec

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Pure extraction of the official daily wire shape -> typed puzzle (zip ZipWireParser
 * precedent; research 02). The GraphQL `included[].gamePuzzle.queensGamePuzzle` object:
 *
 * ```
 * { "gridSize": 8,
 *   "colorGrid": [ { "colors": [0, 0, 1, ...] }, ... ],   // per-cell palette ids, row-major
 *   "solution":  [ { "row": 0, "col": 3 }, ... ] }        // official queens, 0-based
 * ```
 *
 * Palette ids need not be contiguous: early archive dailies skip ids (#8 uses 0..8 without 4
 * on an 8 board). The `gridSize` distinct ids map in ascending order onto region ids
 * 0 until gridSize, so a contiguous grid keeps its ids (and its canonical bytes).
 *
 * Unknown JSON fields are ignored — the parser is structural, not a schema. The result is
 * UNTRUSTED until the A13 gate runs (queens-02+): the independent solve must match the
 * shipped [QueensPuzzle.solution]; play state never exposes it.
 */
object QueensWireParser {

    /** Content + identity: identity arrives with the fetch (date + edition text). */
    data class QueensDaily(
        val puzzle: QueensPuzzle,
        val ref: QueensPuzzleRef,
    )

    /**
     * @param queensGamePuzzleJson the queensGamePuzzle JSON object (verbatim wire shape)
     * @param date the fetch day — the wire carries no date (research 02, Date Keying)
     * @param editionText the daily number string, null when the fetch did not capture one
     */
    fun parse(
        queensGamePuzzleJson: String,
        date: LocalDate,
        editionText: String? = null,
    ): DecodeResult<QueensDaily> = try {
        parse(Json.parseToJsonElement(queensGamePuzzleJson).jsonObject, date, editionText)
    } catch (e: kotlinx.serialization.SerializationException) {
        DecodeResult.Err(DecodeError.Malformed("invalid JSON: ${e.message}"))
    } catch (e: IllegalArgumentException) {
        DecodeResult.Err(DecodeError.Malformed("invalid JSON structure: ${e.message}"))
    }

    /** Internal: [payload] parsing may throw on exotic JSON — the public String overload
     *  wraps it and keeps the never-throw contract. */
    internal fun parse(payload: JsonObject, date: LocalDate, editionText: String? = null): DecodeResult<QueensDaily> {
        val gridSize = payload.int("gridSize")
            ?: return DecodeResult.Err(DecodeError.Malformed("missing gridSize"))
        val variantId = queensVariantId(gridSize)
        val variant = QueensVariants.byId(variantId)
            ?: return DecodeResult.Err(DecodeError.UnknownVariant(variantId.value))
        val n = variant.size

        val colorGrid = payload.arrayOfObjects("colorGrid")
            ?: return DecodeResult.Err(DecodeError.Malformed("missing or malformed colorGrid"))
        if (colorGrid.size != n) {
            return DecodeResult.Err(DecodeError.Malformed("colorGrid has ${colorGrid.size} rows, expected $n"))
        }
        val palette = ArrayList<Int>(n * n)
        colorGrid.forEachIndexed { row, entry ->
            val colors = entry.intArray("colors")
                ?: return DecodeResult.Err(DecodeError.Malformed("colorGrid row $row missing or malformed colors"))
            if (colors.size != n) {
                return DecodeResult.Err(DecodeError.Malformed("colorGrid row $row has ${colors.size} cells, expected $n"))
            }
            palette += colors
        }
        val ids = palette.toSortedSet()
        if (ids.first() < 0 || ids.size != n) {
            return DecodeResult.Err(DecodeError.Malformed("colorGrid has palette ids $ids, expected $n non-negative ids"))
        }
        val regionOf = ids.withIndex().associate { (region, id) -> id to region }
        val regions = palette.map { regionOf.getValue(it) }

        val solutionRows = payload.arrayOfObjects("solution")
            ?: return DecodeResult.Err(DecodeError.Malformed("missing or malformed solution"))
        val queens = ArrayList<Cell>(n * variant.queensPerUnit)
        solutionRows.forEach { entry ->
            val row = entry.int("row") ?: return DecodeResult.Err(DecodeError.Malformed("solution entry missing row"))
            val col = entry.int("col") ?: return DecodeResult.Err(DecodeError.Malformed("solution entry missing col"))
            if (row !in 0 until n || col !in 0 until n) {
                return DecodeResult.Err(DecodeError.Malformed("solution cell ($row, $col) out of range"))
            }
            queens += Cell(row * n + col)
        }
        if (queens.size != n * variant.queensPerUnit) {
            return DecodeResult.Err(
                DecodeError.Malformed("solution places ${queens.size} queens, expected ${n * variant.queensPerUnit}"),
            )
        }
        if (queens.map { it.idx }.toSet().size != queens.size) {
            return DecodeResult.Err(DecodeError.Malformed("duplicate queen cell in solution"))
        }

        return DecodeResult.Ok(
            QueensDaily(
                puzzle = QueensPuzzle(
                    size = n,
                    regions = regions,
                    queensPerUnit = variant.queensPerUnit,
                    solution = queens,
                ),
                ref = QueensPuzzleRef(PuzzleKey.Daily(date, editionText?.toIntOrNull())),
            ),
        )
    }

    private fun JsonObject.int(name: String): Int? =
        (this[name] as? JsonPrimitive)?.content?.toIntOrNull()

    /** Throw-free: a non-array field or a non-primitive/non-integer element yields null. */
    private fun JsonObject.intArray(name: String): List<Int>? =
        (this[name] as? JsonArray)?.map { (it as? JsonPrimitive)?.content?.toIntOrNull() ?: return null }

    /** Throw-free: `[{...}, ...]` shape as list of objects; anything else yields null. */
    private fun JsonObject.arrayOfObjects(name: String): List<JsonObject>? =
        (this[name] as? JsonArray)?.map { (it as? JsonObject) ?: return null }
}
