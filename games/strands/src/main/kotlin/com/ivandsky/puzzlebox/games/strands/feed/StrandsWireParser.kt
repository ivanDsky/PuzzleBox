package com.ivandsky.puzzlebox.games.strands.feed

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Body text → [StrandsWire], or a typed failure for a body that is not JSON of that shape. Never throws. */
object StrandsWireParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): DecodeResult<StrandsWire> = try {
        DecodeResult.Ok(json.decodeFromString(StrandsWire.serializer(), body))
    } catch (e: SerializationException) {
        DecodeResult.Err(DecodeError.Malformed("invalid JSON: ${e.message}"))
    } catch (e: IllegalArgumentException) {
        DecodeResult.Err(DecodeError.Malformed("invalid JSON structure: ${e.message}"))
    }
}
