package com.ivandsky.puzzlebox.games.zip.codec

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.games.zip.engine.ZipVariants
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Pure extraction of `trailGamePuzzle` JSON -> typed puzzle (F2 §1; the RSC flight framing
 * and HTTP stay in core/data, ticket 12). Unknown JSON fields are ignored (F2-5: wallHints,
 * isMystery semantics unobserved) — the parser is structural, not a schema.
 *
 * The result is UNTRUSTED until the caller runs the A16 oracle gate: verify the shipped
 * solution linearly ([com.ivandsky.puzzlebox.games.zip.engine.ZipRules.verifyPath]) and
 * agree with the solver ([com.ivandsky.puzzlebox.games.zip.engine.ZipSolver.countSolutions]
 * == 1 and found == shipped). A failed gate is `FetchError.Untrusted`, never playable.
 */
object ZipWireParser {

    /** Content + identity: identity arrives with the fetch (date + edition text). */
    data class ZipDaily(
        val puzzle: ZipPuzzle,
        val ref: ZipPuzzleRef,
    )

    /**
     * @param trailGamePuzzleJson the trailGamePuzzle JSON object (verbatim wire shape)
     * @param date the fetch day — the wire carries no date
     * @param editionText the daily number string ("592"), null for undated customs
     */
    fun parse(
        trailGamePuzzleJson: String,
        date: LocalDate,
        editionText: String? = null,
    ): DecodeResult<ZipDaily> = try {
        parse(Json.parseToJsonElement(trailGamePuzzleJson).jsonObject, date, editionText)
    } catch (e: kotlinx.serialization.SerializationException) {
        DecodeResult.Err(DecodeError.Malformed("invalid JSON: ${e.message}"))
    } catch (e: IllegalArgumentException) {
        DecodeResult.Err(DecodeError.Malformed("invalid JSON structure: ${e.message}"))
    }

    /** Internal: [payload] parsing may throw on exotic JSON — the public String overload
     *  wraps it and keeps the never-throw contract. */
    internal fun parse(payload: JsonObject, date: LocalDate, editionText: String? = null): DecodeResult<ZipDaily> {
        val gridSize = payload.int("gridSize")
            ?: return DecodeResult.Err(DecodeError.Malformed("missing gridSize"))
        if (gridSize !in 2..16) {
            return DecodeResult.Err(DecodeError.Malformed("gridSize $gridSize outside 2..16"))
        }
        val variantId = VariantId("zip/n$gridSize")
        val variant = ZipVariants.byId(variantId)
            ?: return DecodeResult.Err(DecodeError.UnknownVariant(variantId.value))

        val orderedSequence = payload.array("orderedSequence")
            ?: return DecodeResult.Err(DecodeError.Malformed("missing orderedSequence"))
        if (orderedSequence.isEmpty()) {
            return DecodeResult.Err(DecodeError.Malformed("empty orderedSequence"))
        }
        val waypoints = orderedSequence.map { Cell(it) }
        val nSquared = gridSize * gridSize
        if (waypoints.any { it.idx !in 0 until nSquared }) {
            return DecodeResult.Err(DecodeError.Malformed("waypoint out of range"))
        }
        if (waypoints.map { it.idx }.distinct().size != waypoints.size) {
            return DecodeResult.Err(DecodeError.Malformed("duplicate waypoint"))
        }

        val walls = HashSet<Wall>()
        (payload["walls"] as? kotlinx.serialization.json.JsonArray)?.forEach { element ->
            val wall = element as? kotlinx.serialization.json.JsonObject
                ?: return DecodeResult.Err(DecodeError.Malformed("wall entry must be an object"))
            val cellIdx = wall.int("cellIdx")
                ?: return DecodeResult.Err(DecodeError.Malformed("wall missing cellIdx"))
            if (cellIdx !in 0 until nSquared) {
                return DecodeResult.Err(DecodeError.Malformed("wall cell $cellIdx out of range"))
            }
            val direction = wall.primitive("direction")
                ?: return DecodeResult.Err(DecodeError.Malformed("wall missing direction"))
            val dir = when {
                direction.endsWith("RIGHT") -> WallDirection.RIGHT
                direction.endsWith("DOWN") -> WallDirection.DOWN
                else -> return DecodeResult.Err(DecodeError.Malformed("unknown wall direction $direction"))
            }
            walls += Wall(Cell(cellIdx), dir)
        }

        // A MISSING solution stays null (Phase-2 boards ship none); a PRESENT but
        // unparseable one is Malformed — never silently absent.
        val solution: List<Cell>? = if (payload.containsKey("solution")) {
            payload.array("solution")?.map { Cell(it) }
                ?: return DecodeResult.Err(DecodeError.Malformed("solution present but unparseable"))
        } else {
            null
        }
        if (solution != null && solution.map { it.idx }.distinct().size != nSquared) {
            return DecodeResult.Err(DecodeError.Malformed("solution is not a permutation of the board"))
        }

        return DecodeResult.Ok(
            ZipDaily(
                puzzle = ZipPuzzle(
                    variant = variant.id,
                    board = ZipBoard(gridSize, walls, waypoints),
                    solution = solution,
                ),
                ref = ZipPuzzleRef(date = date, puzzleNumber = editionText?.toIntOrNull()),
            ),
        )
    }

    private fun JsonObject.int(name: String): Int? =
        (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()

    /** Throw-free: a non-array field or a non-primitive/non-integer element yields null. */
    private fun JsonObject.array(name: String): List<Int>? =
        (this[name] as? kotlinx.serialization.json.JsonArray)
            ?.map { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: return null }

    private fun JsonObject.primitive(name: String): String? =
        (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.content
}
