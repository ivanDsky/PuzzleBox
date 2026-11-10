package com.ivandsky.puzzlebox.network.linkedin

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads the connect handshake: today's game URN names the member. Null means the body is not the expected
 * shape (a stale query id or a changed response), which the adapter reports as a rejection.
 */
internal object VoyagerTodayParser {

    fun profileId(body: String): String? {
        val root = try {
            Json.parseToJsonElement(body)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        val first = root.obj("data")?.obj("data")?.obj("identityDashGamesByTodaysGame")
            ?.array("*elements")?.firstOrNull()
        val urn = (first as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        return VoyagerQueries.profileIdOf(urn)
    }
}

private fun JsonElement.obj(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject

private fun JsonElement.array(key: String): JsonArray? = (this as? JsonObject)?.get(key) as? JsonArray
