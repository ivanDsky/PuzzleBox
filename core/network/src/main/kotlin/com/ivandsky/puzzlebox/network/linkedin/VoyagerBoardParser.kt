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
        val profiles = profilesByUrn(json.array("included"))

        val rows = elements.mapNotNull { (it as? JsonObject)?.let { row -> rowOf(row, profiles) } }
        // A page of rows that all failed to parse is a renamed field, not an empty board.
        if (elements.isNotEmpty() && rows.isEmpty()) return null
        return LinkedInBoard(rows)
    }

    private fun rowOf(row: JsonObject, profiles: Map<String, Profile>): LinkedInBoard.Row? {
        val urn = row.obj("playerDetails").obj("player").string("*profile") ?: return null
        if (!urn.startsWith(PROFILE_PREFIX)) return null
        val memberRef = urn.removePrefix(PROFILE_PREFIX)
        val profile = profiles[urn]
        val name = profile?.name.orEmpty()
        val pictureUrl = profile?.pictureUrl

        val seconds = row.obj("gameScore").int("timeElapsed")
        // A hidden row carries no ranking; a ranked row with no positive time is hidden too, never time 0.
        val ranked = row["ranking"].let { it != null && it !is JsonNull }
        val visible = ranked && seconds != null && seconds > 0
        if (!visible) return LinkedInBoard.Row(memberRef, name, null, false, false, pictureUrl)

        return LinkedInBoard.Row(
            memberRef = memberRef,
            name = name,
            timeSeconds = seconds,
            hintFree = row.obj("completionAttributes").bool("isHintFree") == true,
            flawless = row.bool("isFlawless") == true,
            pictureUrl = pictureUrl,
        )
    }

    private class Profile(val name: String, val pictureUrl: String?)

    private fun profilesByUrn(included: JsonArray?): Map<String, Profile> {
        val profiles = HashMap<String, Profile>()
        included?.forEach { record ->
            val obj = record as? JsonObject ?: return@forEach
            val urn = obj.string("entityUrn") ?: return@forEach
            if (!urn.startsWith(PROFILE_PREFIX)) return@forEach
            val name = listOfNotNull(obj.string("firstName"), obj.string("lastName")).joinToString(" ").trim()
            profiles[urn] = Profile(name, pictureUrlOf(obj.obj("profilePicture")))
        }
        return profiles
    }

    /** The resolved reference first, the framed one when it has nothing usable; any other shape has no photo. */
    private fun pictureUrlOf(picture: JsonObject?): String? =
        listOf("displayImageReferenceResolutionResult", "displayImageWithFrameReference")
            .firstNotNullOfOrNull { key -> smallestArtifactUrl(picture.obj(key).obj("vectorImage")) }

    /** The smallest artifact is the 100 px one, which is all a list row needs; the url is the root plus its path segment. */
    private fun smallestArtifactUrl(vector: JsonObject?): String? {
        val root = vector.string("rootUrl")?.takeIf { it.isNotEmpty() } ?: return null
        val smallest = vector.array("artifacts").orEmpty()
            .mapNotNull { artifact ->
                val width = artifact.int("width") ?: return@mapNotNull null
                val segment = artifact.string("fileIdentifyingUrlPathSegment") ?: return@mapNotNull null
                width to segment
            }
            .minByOrNull { it.first } ?: return null
        return root + smallest.second
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
