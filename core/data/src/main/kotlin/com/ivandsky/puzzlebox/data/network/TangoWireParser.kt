package com.ivandsky.puzzlebox.data.network

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
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Pure extraction of `tangoGamePuzzle` JSON -> typed [TangoPuzzle] (the RSC flight
 * framing and HTTP stay in core/data; research 01-tango-payload, [INFERRED] schema).
 * Unknown JSON fields are ignored — the parser is structural, not a schema.
 *
 * Variant discipline (ADR-0005): the official daily variant is the [VARIANT] constant,
 * never derived from the board size. A payload that names a different variant fails
 * with [DecodeError.UnknownVariant] — ticket 06 validates the live string.
 * Zone discipline (A10): the payload zone when present and parseable, else [FALLBACK_ZONE].
 */
object TangoWireParser {

    /** Exact variant-string candidate — ticket 06 validates this live. */
    const val VARIANT: String = "tango/n6"

    /** A10: the daily rolls in LinkedIn's zone; absent/unknown payload zone falls back here. */
    const val FALLBACK_ZONE: String = "America/Los_Angeles"

    /** Content + identity: identity arrives with the fetch (date + edition text). */
    data class TangoDaily(
        val puzzle: TangoPuzzle,
        val ref: TangoPuzzleRef,
        /** A10: payload zone id when carried and parseable, else [FALLBACK_ZONE]. */
        val zoneId: String,
    )

    /**
     * @param tangoGamePuzzleJson the tangoGamePuzzle JSON object (verbatim wire shape)
     * @param date the fetch day — the wire carries no ISO date
     * @param editionText the daily number string ("301"), null for undated customs
     */
    fun parse(
        tangoGamePuzzleJson: String,
        date: LocalDate,
        editionText: String? = null,
    ): DecodeResult<TangoDaily> = try {
        parse(Json.parseToJsonElement(tangoGamePuzzleJson).jsonObject, date, editionText)
    } catch (e: kotlinx.serialization.SerializationException) {
        DecodeResult.Err(DecodeError.Malformed("invalid JSON: ${e.message}"))
    } catch (e: IllegalArgumentException) {
        DecodeResult.Err(DecodeError.Malformed("invalid JSON structure: ${e.message}"))
    }

    /** Internal: [payload] parsing may throw on exotic JSON — the public String overload
     *  wraps it and keeps the never-throw contract. */
    internal fun parse(payload: JsonObject, date: LocalDate, editionText: String? = null): DecodeResult<TangoDaily> {
        val n = payload.int("gridSize")
            ?: return DecodeResult.Err(DecodeError.Malformed("missing gridSize"))
        if (n !in TangoPuzzle.MIN_N..TangoPuzzle.MAX_N) {
            return DecodeResult.Err(DecodeError.Malformed("gridSize $n outside ${TangoPuzzle.MIN_N}..${TangoPuzzle.MAX_N}"))
        }

        // ADR-0005: the daily is the pinned official variant; a payload naming anything
        // else is an unknown variant — never guessed from n.
        payload.primitive("variant")?.let { wireVariant ->
            if (wireVariant != VARIANT) {
                return DecodeResult.Err(DecodeError.UnknownVariant(wireVariant))
            }
        }
        val pinnedN = VARIANT.substringAfterLast("/n", "").toIntOrNull()
        if (n != pinnedN) {
            return DecodeResult.Err(DecodeError.VariantSizeMismatch(VARIANT, expected = pinnedN!!, actual = n))
        }
        val variant = VariantId(VARIANT)

        val givens = LinkedHashMap<Int, TangoCell>()
        (payload["given"] as? kotlinx.serialization.json.JsonArray)?.forEachIndexed { row, rowElement ->
            val cells = rowElement as? kotlinx.serialization.json.JsonArray
                ?: return DecodeResult.Err(DecodeError.Malformed("given row $row must be an array"))
            cells.forEachIndexed { col, cellElement ->
                val token = (cellElement as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@forEachIndexed
                val cell = try {
                    cellToken(token)
                } catch (_: IllegalArgumentException) {
                    return DecodeResult.Err(
                        DecodeError.Malformed("given [$row][$col]='$token' is not a sun/moon/empty token"),
                    )
                }
                if (cell != null) givens[row * n + col] = cell
            }
        }

        val markers = ArrayList<TangoMarker>()
        (payload["constraints"] as? kotlinx.serialization.json.JsonArray)?.forEachIndexed { i, element ->
            val constraint = element as? JsonObject
                ?: return DecodeResult.Err(DecodeError.Malformed("constraint $i must be an object"))
            val r1 = constraint.int("r1") ?: return DecodeResult.Err(DecodeError.Malformed("constraint $i missing r1"))
            val c1 = constraint.int("c1") ?: return DecodeResult.Err(DecodeError.Malformed("constraint $i missing c1"))
            val r2 = constraint.int("r2") ?: return DecodeResult.Err(DecodeError.Malformed("constraint $i missing r2"))
            val c2 = constraint.int("c2") ?: return DecodeResult.Err(DecodeError.Malformed("constraint $i missing c2"))
            val kindToken = constraint.primitive("kind")
                ?: return DecodeResult.Err(DecodeError.Malformed("constraint $i missing kind"))
            if (r1 !in 0 until n || c1 !in 0 until n || r2 !in 0 until n || c2 !in 0 until n) {
                return DecodeResult.Err(DecodeError.Malformed("constraint $i references a cell outside the board"))
            }
            val kind = when (kindToken) {
                "=", "eq" -> TangoMarkerKind.SAME
                "x", "×", "cross" -> TangoMarkerKind.OPPOSITE
                else -> return DecodeResult.Err(DecodeError.Malformed("constraint $i has unknown kind '$kindToken'"))
            }
            // Interior-edge normalization: either endpoint may be listed first.
            val marker = when {
                r1 == r2 && (c1 - c2 == 1 || c2 - c1 == 1) ->
                    TangoMarker(minOf(c1, c2) + r1 * n, TangoEdgeSide.RIGHT, kind)
                c1 == c2 && (r1 - r2 == 1 || r2 - r1 == 1) ->
                    TangoMarker(minOf(r1, r2) * n + c1, TangoEdgeSide.DOWN, kind)
                else -> return DecodeResult.Err(
                    DecodeError.Malformed("constraint $i ($r1,$c1)-($r2,$c2) is not an adjacent pair"),
                )
            }
            markers += marker
        }

        val zoneId = payload.primitive("zone")?.takeIf { token ->
            runCatching { ZoneId.of(token) }.isSuccess
        } ?: FALLBACK_ZONE

        return DecodeResult.Ok(
            TangoDaily(
                puzzle = TangoPuzzle(variant = variant, n = n, givens = givens, markers = markers),
                ref = TangoPuzzleRef(date = date, puzzleNumber = editionText?.toIntOrNull()),
                zoneId = zoneId,
            ),
        )
    }

    /** "S"/"sun" and "M"/"moon" give values; empty/blank tokens are non-givens (null). */
    private fun cellToken(token: String): TangoCell? = when (token.lowercase()) {
        "", " ", "-", "_" -> null
        "s", "sun" -> TangoCell.SUN
        "m", "moon" -> TangoCell.MOON
        else -> throw IllegalArgumentException("unknown cell token")
    }

    private fun JsonObject.int(name: String): Int? =
        (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()

    private fun JsonObject.primitive(name: String): String? =
        (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.content
}
