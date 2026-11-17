package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInGamePage
import com.ivandsky.puzzlebox.game.linkedin.LinkedInProgress
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Reads today's game off the voyager read a voyager save starts from (ADR-0032 §11): whose game it is, which
 * puzzle, and how far the member's stored record of it has got. The game is the `included` entry the response's
 * first element names; the member comes out of its URN.
 *
 * Null means the body is not the expected shape (a stale query id or a changed response), which the caller
 * reports as a rejection: a game with no readable record is never guessed Fresh, since Fresh is what lets a
 * save go out.
 */
internal object VoyagerGamePageParser {

    fun parse(body: String): LinkedInGamePage? {
        val root = try {
            Json.parseToJsonElement(body)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        val urn = root.obj("data")?.obj("data")?.obj("identityDashGamesByTodaysGame")
            ?.array("*elements")?.firstOrNull()?.text() ?: return null
        val profileId = VoyagerQueries.profileIdOf(urn) ?: return null
        val game = root.array("included")?.firstOrNull { it.string("entityUrn") == urn } ?: return null
        val record = game.obj("gameStoredRecord") ?: return null
        return LinkedInGamePage(
            puzzleId = game.long("puzzleId") ?: return null,
            profileId = profileId,
            progress = progress(record.string("gamePlayState") ?: return null),
            isTutorial = game.bool("isTutorialGame") == true,
            totalPlays = game.obj("personalStats")?.long("totalPlaysCount") ?: 0,
            wins = game.long("winCount") ?: 0,
            requestTime = record.long("gameRequestTime") ?: 0,
        )
    }

    /** A state not known here is Won, so nothing is sent over a record this build cannot read. */
    private fun progress(playState: String): LinkedInProgress = when (playState) {
        NOT_PLAYED -> LinkedInProgress.Fresh
        IN_PROGRESS -> LinkedInProgress.Started
        else -> LinkedInProgress.Won
    }

    private const val NOT_PLAYED = "UNKNOWN"
    private const val IN_PROGRESS = "IN_PROGRESS"
}

private fun JsonElement.obj(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject

private fun JsonElement.array(key: String): JsonArray? = (this as? JsonObject)?.get(key) as? JsonArray

private fun JsonElement.primitive(key: String): JsonPrimitive? = (this as? JsonObject)?.get(key) as? JsonPrimitive

private fun JsonElement.text(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonElement.string(key: String): String? = primitive(key)?.text()

private fun JsonElement.long(key: String): Long? = primitive(key)?.takeIf { !it.isString }?.longOrNull

private fun JsonElement.bool(key: String): Boolean? = primitive(key)?.takeIf { !it.isString }?.booleanOrNull
