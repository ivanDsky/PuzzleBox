package com.ivandsky.puzzlebox.network.linkedin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Tango opening and win bodies against the Tango #770 captures (research 03, Addendum 5), compared structurally. */
class UpdateGameStateBodyTangoCaptureTest {

    private val post = LinkedInPostFixtures.tangoPost()
    private val openingPage = LinkedInPostFixtures.tangoPage
    private val winPage = openingPage.copy(totalPlays = 595)

    @Test
    fun `the win body has the captured state keys, requested keys, payload bindings and board states`() {
        val body = parse(UpdateGameStateBody.build(post, winPage))
        val captured = LinkedInPostFixtures.tangoWinRequest

        assertEquals(stateKeys(captured), stateKeys(body))
        assertEquals(requestedKeys(captured), requestedKeys(body))
        assertEquals(payload(captured), payload(body))
        assertEquals(stateValues(captured, TANGO_KEYS), stateValues(body, TANGO_KEYS))
        assertEquals(stateValues(captured, SERVER_KEYS), stateValues(body, SERVER_KEYS))
    }

    @Test
    fun `the opening body requests the solve order the captured client binds without sending a state for it`() {
        val body = parse(checkNotNull(UpdateGameStateBody.opening(post, openingPage)))
        val captured = LinkedInPostFixtures.tangoOpeningRequest

        assertEquals(stateKeys(captured), stateKeys(body))
        assertEquals(requestedKeys(captured), requestedKeys(body))
        assertEquals(payload(captured), payload(body))
        assertEquals(stateValues(captured, TANGO_KEYS), stateValues(body, TANGO_KEYS))
        assertEquals(stateValues(captured, SERVER_KEYS), stateValues(body, SERVER_KEYS))
        assertEquals(false, "tangoSolveOrderBinding" in stateKeys(body))
        assertEquals(true, "tangoSolveOrderBinding" in requestedKeys(body))
    }

    @Test
    fun `a game whose payload binds only keys it has a state for requests exactly its states`() {
        val body = parse(UpdateGameStateBody.build(LinkedInPostFixtures.post(), LinkedInPostFixtures.capturePage))

        assertEquals(stateKeys(body), requestedKeys(body))
    }

    private fun parse(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private fun stateKeys(body: JsonObject): List<String> =
        body.getValue("states").jsonArray.map { it.jsonObject.getValue("key").jsonPrimitive.content }

    private fun stateValues(body: JsonObject, keys: Set<String>): Map<String, JsonElement> =
        body.getValue("states").jsonArray.map { it.jsonObject }
            .filter { it.getValue("key").jsonPrimitive.content in keys }
            .associate { it.getValue("key").jsonPrimitive.content to it.getValue("value") }

    private fun arguments(body: JsonObject): JsonObject = body.getValue("serverRequest").jsonObject.getValue("requestedArguments").jsonObject

    private fun requestedKeys(body: JsonObject): List<String> =
        arguments(body).getValue("requestedStateKeys").jsonArray.map {
            it.jsonObject.getValue("key").jsonObject.getValue("value").jsonObject.getValue("id").jsonPrimitive.content
        }

    private fun payload(body: JsonObject): JsonObject = arguments(body).getValue("payload").jsonObject

    private companion object {
        val TANGO_KEYS = setOf("tangoBoardStateBinding", "tangoSolveOrderBinding")
        val SERVER_KEYS = setOf(
            "gameBoardPuzzleId", "gameBoardProfileId", "gameBoardHasWon", "gameBoardTimeElapsed",
            "gameBoardTotalPlaysCount", "gameBoardWinCount", "gameBoardHasUsedHint", "gameIsTutorialGameBinding",
            "gameBoardGameRequestTime",
        )
    }
}
