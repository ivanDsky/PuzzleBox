package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Turns one connections response into the port's board. A shape mismatch is null, never an exception, so a
 * renamed field reads as a rejection rather than as "none of your connections played".
 *
 * Rows keep the response order and the player's own row stays in: the parser has no profile id to filter by,
 * and ranking and ties belong to the consumers. `paging.total` is ignored; the query asks for one page.
 */
internal object VoyagerBoardParser {

    private const val ROOT = "identityDashGameConnectionsEntitiesByOptedInToLeaderboardAndPlayed"
    private const val PROFILE_PREFIX = "urn:li:fsd_profile:"

    fun parse(body: String): LinkedInBoard? {
        val json = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val elements = json.obj("data").obj("data").obj(ROOT).array("elements") ?: return null
        val names = namesByProfile(json.array("included"))

        val rows = elements.mapNotNull { (it as? JsonObject)?.let { row -> rowOf(row, names) } }
        // A page of rows that all failed to parse is a renamed field, not an empty board.
        if (elements.isNotEmpty() && rows.isEmpty()) return null
        return LinkedInBoard(rows)
    }

    private fun rowOf(row: JsonObject, names: Map<String, String>): LinkedInBoard.Row? {
        val urn = row.obj("playerDetails").obj("player").string("*profile") ?: return null
        if (!urn.startsWith(PROFILE_PREFIX)) return null
        val memberRef = urn.removePrefix(PROFILE_PREFIX)

        val seconds = row.obj("gameScore").int("timeElapsed")
        // A hidden row carries no ranking; a ranked row with no positive time is hidden too, never time 0.
        val ranked = row["ranking"].let { it != null && it !is JsonNull }
        val visible = ranked && seconds != null && seconds > 0
        if (!visible) return LinkedInBoard.Row(memberRef, names[urn].orEmpty(), null, false, false)

        return LinkedInBoard.Row(
            memberRef = memberRef,
            name = names[urn].orEmpty(),
            timeSeconds = seconds,
            hintFree = row.obj("completionAttributes").bool("isHintFree") == true,
            flawless = row.bool("isFlawless") == true,
        )
    }

    private fun namesByProfile(included: JsonArray?): Map<String, String> {
        val names = HashMap<String, String>()
        included?.forEach { record ->
            val obj = record as? JsonObject ?: return@forEach
            val urn = obj.string("entityUrn") ?: return@forEach
            if (!urn.startsWith(PROFILE_PREFIX)) return@forEach
            names[urn] = listOfNotNull(obj.string("firstName"), obj.string("lastName")).joinToString(" ").trim()
        }
        return names
    }

    private fun JsonElement?.obj(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject

    private fun JsonElement?.array(key: String): JsonArray? = (this as? JsonObject)?.get(key) as? JsonArray

    private fun JsonElement?.string(key: String): String? =
        ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonElement?.int(key: String): Int? =
        ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

    private fun JsonElement?.bool(key: String): Boolean? =
        ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
}
