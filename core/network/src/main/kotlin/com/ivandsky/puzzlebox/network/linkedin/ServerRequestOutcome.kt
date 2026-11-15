package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSendResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

private const val HTTP_OK = 200
private const val HTTP_CONFLICT = 409
private const val RESPONSE_LINE = "0:"

/**
 * What one `updateGameState` answer means. [body] matters only for a 200: LinkedIn answers 200 to a request
 * it refused in the stream itself, so the line `0:` must carry a `ServerRequestResponse` with `errors: []`
 * before this is a post. A 409 means a newer result is already there. Everything else follows the shared
 * status table, and a 200 whose body cannot be read as a server response is a rejection too, never a post.
 */
internal fun serverRequestOutcome(code: Int, body: String): LinkedInSendResult = when (code) {
    HTTP_OK -> if (hasNoErrors(body)) LinkedInSendResult.Posted else LinkedInSendResult.Rejected(Kind.Unexpected)
    HTTP_CONFLICT -> LinkedInSendResult.AlreadyPlayed
    else -> LinkedInSendResult.Rejected(linkedInRejection(code))
}

private fun hasNoErrors(body: String): Boolean {
    val line = body.lineSequence().firstOrNull { it.startsWith(RESPONSE_LINE) } ?: return false
    val root = try {
        Json.parseToJsonElement(line.removePrefix(RESPONSE_LINE))
    } catch (_: SerializationException) {
        return false
    }
    val errors = ((root as? JsonObject)?.get("response") as? JsonObject)?.get("errors")
    return errors is JsonArray && errors.isEmpty()
}
