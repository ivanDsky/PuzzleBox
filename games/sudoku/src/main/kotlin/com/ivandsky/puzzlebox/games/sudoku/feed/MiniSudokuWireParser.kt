package com.ivandsky.puzzlebox.games.sudoku.feed

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate

/**
 * Turns a `miniSudokuGamePuzzle` object into a [SudokuPuzzle] for [expected]'s geometry.
 *
 * LinkedIn sends the full solution plus preset indexes, so the givens are the solution's
 * digits at the presets. Everything this engine assumes is checked rather than trusted: a
 * square grid of the variant's size, regions that are exactly the variant's boxes, distinct
 * in-range presets, a solution where every row, column and box is a permutation, and no
 * column restrictions (the field is LinkedIn's hook for a rule this engine does not play).
 */
object MiniSudokuWireParser {

    data class Daily(val puzzle: SudokuPuzzle, val ref: SudokuPuzzleRef)

    fun parse(json: String, date: LocalDate, editionText: String?, expected: SudokuVariant): DecodeResult<Daily> =
        try {
            val obj = Json.parseToJsonElement(json) as? JsonObject ?: throw WireError("not a JSON object")
            val puzzle = parsePuzzle(obj, expected)
            val ref = SudokuPuzzleRef(SudokuGames.MINI.id, date, editionText?.toIntOrNull())
            DecodeResult.Ok(Daily(puzzle, ref))
        } catch (e: SizeMismatch) {
            DecodeResult.Err(DecodeError.VariantSizeMismatch(expected.id.value, expected.geometry.size, e.actual))
        } catch (e: WireError) {
            DecodeResult.Err(DecodeError.Malformed(e.message ?: "malformed miniSudokuGamePuzzle"))
        } catch (e: IllegalArgumentException) {
            // kotlinx SerializationException is an IllegalArgumentException.
            DecodeResult.Err(DecodeError.Malformed("unparseable miniSudokuGamePuzzle: ${e.message}"))
        }

    private fun parsePuzzle(obj: JsonObject, expected: SudokuVariant): SudokuPuzzle {
        val geometry = expected.geometry
        val rows = obj.int("gridRowSize")
        val cols = obj.int("gridColSize")
        if (rows != cols) throw WireError("grid is $rows x $cols, not square")
        if (rows != geometry.size) throw SizeMismatch(rows)
        val cells = geometry.cellCount

        val regions = obj.array("regions").map { region ->
            val regionObj = region as? JsonObject ?: throw WireError("region is not an object")
            regionObj.array("cellIdxes").map { it.intValue() }.toSet()
        }
        val boxes = geometry.boxCells().map { box -> box.toSet() }.toSet()
        if (regions.size != geometry.size || regions.toSet() != boxes) {
            throw WireError("regions are not the ${geometry.boxRows}x${geometry.boxCols} boxes")
        }

        val presets = obj.array("presetCellIdxes").map { it.intValue() }
        if (presets.any { it !in 0 until cells }) throw WireError("preset index out of range")
        if (presets.toSet().size != presets.size) throw WireError("duplicate preset index")

        val solution = obj.array("solution").map { it.intValue() }
        if (solution.size != cells) throw WireError("solution has ${solution.size} digits, expected $cells")
        if (solution.any { it !in 1..geometry.size }) throw WireError("solution digit outside 1..${geometry.size}")
        val full = (1 shl geometry.size) - 1
        for (unit in geometry.units) {
            var seen = 0
            for (idx in geometry.cellsOf(unit)) seen = seen or (1 shl (solution[idx] - 1))
            if (seen != full) throw WireError("solution repeats a digit in $unit")
        }

        when (val restrictions = obj["columnRestrictions"]) {
            null, JsonNull -> Unit
            is JsonArray -> if (restrictions.isNotEmpty()) throw WireError("columnRestrictions are not supported")
            else -> throw WireError("columnRestrictions is not an array")
        }

        val presetSet = presets.toSet()
        val givens = List(cells) { if (it in presetSet) solution[it] else 0 }
        return SudokuPuzzle(expected, givens, solution)
    }

    private fun JsonObject.int(key: String): Int = (get(key) ?: throw WireError("missing $key")).intValue()

    private fun JsonObject.array(key: String): JsonArray = get(key) as? JsonArray ?: throw WireError("missing array $key")

    private fun JsonElement.intValue(): Int {
        val primitive = this as? JsonPrimitive ?: throw WireError("expected a number, got $this")
        if (primitive.isString) throw WireError("expected a number, got a string")
        return primitive.content.toIntOrNull() ?: throw WireError("expected an integer, got ${primitive.content}")
    }

    private class WireError(message: String) : Exception(message)

    private class SizeMismatch(val actual: Int) : Exception()
}
