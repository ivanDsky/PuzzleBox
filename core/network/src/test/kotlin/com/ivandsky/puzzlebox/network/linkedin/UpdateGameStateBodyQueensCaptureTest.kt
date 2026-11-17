package com.ivandsky.puzzlebox.network.linkedin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Queens opening and win bodies against the captured #930 requests, as parsed JSON: state keys and order,
 * requested keys and payload bindings equal, each state value equal. The one value PuzzleBox decides differently is
 * flawless: the web client sent true after a conflict, PuzzleBox's single rule (mistake-free and no hint) says false.
 */
class UpdateGameStateBodyQueensCaptureTest {

    private val post = LinkedInPostFixtures.queensPost()
    private val page = LinkedInPostFixtures.queensDailyPage

    @Test
    fun `the win body is the captured win request but for the flawless flag`() {
        val body = parse(UpdateGameStateBody.build(post, page))

        assertEquals(withoutFlawless(LinkedInPostFixtures.queensWinRequest), withoutFlawless(body))
        assertEquals(JsonPrimitive(false), states(body).getValue("gameIsFlawlessBinding"))
    }

    @Test
    fun `the opening body equals the captured opening request`() {
        assertEquals(LinkedInPostFixtures.queensOpeningRequest, parse(checkNotNull(UpdateGameStateBody.opening(post, page))))
    }

    @Test
    fun `both bodies carry the board and solve order states, request them and bind them in the payload`() {
        for (body in listOf(UpdateGameStateBody.build(post, page), checkNotNull(UpdateGameStateBody.opening(post, page))).map(::parse)) {
            val keys = body.getValue("states").jsonArray.map { it.jsonObject.getValue("key").jsonPrimitive.content }
            assertEquals(listOf("queensBoardStateBinding", "queensSolveOrderBinding"), keys.takeLast(2))
            val requested = body.getValue("serverRequest").jsonObject.getValue("requestedArguments").jsonObject
                .getValue("requestedStateKeys").jsonArray
                .map { it.jsonObject.getValue("key").jsonObject.getValue("value").jsonObject.getValue("id").jsonPrimitive.content }
            assertEquals(keys, requested)
            val payload = body.getValue("requestedArguments").jsonObject.getValue("payload").jsonObject
            assertEquals(JsonPrimitive("3"), payload.getValue("gameTypeId"))
            assertEquals("queensBoardStateBinding", payload.getValue("playRecord").jsonObject.getValue("key").jsonPrimitive.content)
            assertEquals("queensSolveOrderBinding", payload.getValue("solveOrder").jsonObject.getValue("key").jsonPrimitive.content)
        }
    }

    private fun parse(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private fun states(body: JsonObject): Map<String, JsonElement> =
        body.getValue("states").jsonArray.associate {
            it.jsonObject.getValue("key").jsonPrimitive.content to it.jsonObject.getValue("value")
        }

    /** [body] with the flawless state set to false everywhere the body repeats its states. */
    private fun withoutFlawless(body: JsonObject): JsonObject {
        fun JsonArray.unflawed() = JsonArray(
            map { state ->
                val entry = state.jsonObject
                if (entry.getValue("key").jsonPrimitive.content == "gameIsFlawlessBinding") JsonObject(entry + ("value" to JsonPrimitive(false))) else entry
            },
        )
        val args = body.getValue("requestedArguments").jsonObject
        return JsonObject(
            body + mapOf(
                "states" to body.getValue("states").jsonArray.unflawed(),
                "requestedArguments" to JsonObject(args + ("states" to args.getValue("states").jsonArray.unflawed())),
            ),
        )
    }
}
