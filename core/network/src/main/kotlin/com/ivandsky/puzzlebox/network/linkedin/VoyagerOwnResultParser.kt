package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Reads the end-page response for one game URN.
 *
 * The requested URN is the truth: the matching record's own `puzzleId` is unreliable for past days and is
 * never read. Null means the response does not contain the game that was asked for, which is a shape
 * mismatch and not "didn't play".
 */
internal object VoyagerOwnResultParser {

    fun parse(body: String, gameUrn: String): LinkedInOwnResult? {
        val root = try {
            Json.parseToJsonElement(body)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        val record = root.array("included")?.firstOrNull { it.string("entityUrn") == gameUrn } ?: return null

        val stored = record.obj("gameStoredRecord") ?: return LinkedInOwnResult.NotPlayed
        if (stored.string("gamePlayState") != "END_SOLVED") return LinkedInOwnResult.NotPlayed
        val seconds = record.obj("gameScore")?.int("timeElapsed")?.takeIf { it > 0 }
            ?: return LinkedInOwnResult.NotPlayed
        val hintFree = stored.obj("completionAttributes")?.bool("isHintFree") == true
        return LinkedInOwnResult.Solved(timeSeconds = seconds, hintFree = hintFree)
    }
}

private fun JsonElement.obj(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject

private fun JsonElement.array(key: String): JsonArray? = (this as? JsonObject)?.get(key) as? JsonArray

private fun JsonElement.string(key: String): String? =
    ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonElement.int(key: String): Int? =
    ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

private fun JsonElement.bool(key: String): Boolean? =
    ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
