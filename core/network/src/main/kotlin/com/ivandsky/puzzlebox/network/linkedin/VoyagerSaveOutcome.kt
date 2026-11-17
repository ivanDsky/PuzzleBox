package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSendResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject


/**
 * What one voyager save answer means. [body] matters only for a 200: it is a save only when it carries the
 * mutation's own result, `data.data.updateIdentityDashGames`, as the capture's answer does; a 200 that does
 * not (a GraphQL error list, a login page) is a rejection, never a post. Everything else follows the shared
 * status table. A 409 says the stored record moved on, not what it holds, so [VoyagerGameSave] reads the game
 * again before it gets here.
 */
internal fun voyagerSaveOutcome(code: Int, body: String): LinkedInSendResult = when (code) {
    HTTP_OK -> if (carriesUpdate(body)) LinkedInSendResult.Posted else LinkedInSendResult.Rejected(Kind.Unexpected)
    else -> LinkedInSendResult.Rejected(linkedInRejection(code))
}

private fun carriesUpdate(body: String): Boolean {
    val root = try {
        Json.parseToJsonElement(body)
    } catch (_: SerializationException) {
        return false
    } catch (_: IllegalArgumentException) {
        return false
    }
    val data = ((root as? JsonObject)?.get("data") as? JsonObject)?.get("data") as? JsonObject
    return data?.get("updateIdentityDashGames") is JsonObject
}
