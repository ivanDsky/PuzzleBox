package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInGamePage
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlay
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlayValue
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPost
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/** The write body against the win capture, field by field, with the page's and the post's values where they differ. */
class UpdateGameStateBodyTest {

    private val captured = LinkedInPostFixtures.winRequest

    @Test
    fun `the body is the win capture with a daily page's tutorial flag off`() {
        val body = build(LinkedInPostFixtures.post(), LinkedInPostFixtures.capturePage)

        // The capture was the tutorial; a post is never one. That one state is the only difference.
        assertEquals(withState(captured, "gameIsTutorialGameBinding", JsonPrimitive(false)), body)
    }

    @Test
    fun `the body carries the page's values and the post's time and flags`() {
        val page = LinkedInPostFixtures.dailyPage.copy(totalPlays = 12, wins = 9, requestTime = 1_794_769_845_204)
        val body = build(LinkedInPostFixtures.post(timeMs = 61_234, hintUsed = true, mistakeFree = false), page)

        val states = states(body)
        assertEquals(bigint("608"), states.getValue("gameBoardPuzzleId"))
        assertEquals(JsonPrimitive("ACoAAFAKEPROFILE0000000000000000000000000"), states.getValue("gameBoardProfileId"))
        assertEquals(JsonPrimitive(true), states.getValue("gameBoardHasWon"))
        assertEquals(bigint("61234"), states.getValue("gameBoardTimeElapsed"))
        assertEquals(JsonPrimitive(12), states.getValue("gameBoardTotalPlaysCount"))
        assertEquals(JsonPrimitive(9), states.getValue("gameBoardWinCount"))
        assertEquals(JsonPrimitive(false), states.getValue("gameIsFlawlessBinding"))
        assertEquals(JsonPrimitive(true), states.getValue("gameBoardHasUsedHint"))
        assertEquals(JsonPrimitive(false), states.getValue("gameIsMistakeFreeBinding"))
        assertEquals(JsonPrimitive(false), states.getValue("gameIsTutorialGameBinding"))
        assertEquals(bigint("1794769845204"), states.getValue("gameBoardGameRequestTime"))
    }

    @Test
    fun `a flawless play is flawless only with no hint`() {
        fun flawless(hintUsed: Boolean, mistakeFree: Boolean) =
            states(build(LinkedInPostFixtures.post(hintUsed = hintUsed, mistakeFree = mistakeFree), LinkedInPostFixtures.dailyPage))
                .getValue("gameIsFlawlessBinding")

        assertEquals(JsonPrimitive(true), flawless(hintUsed = false, mistakeFree = true))
        assertEquals(JsonPrimitive(false), flawless(hintUsed = true, mistakeFree = true))
        assertEquals(JsonPrimitive(false), flawless(hintUsed = false, mistakeFree = false))
    }

    @Test
    fun `requested state keys list every state in state order and each is bound in the payload`() {
        val body = build(LinkedInPostFixtures.post(), LinkedInPostFixtures.dailyPage)

        val stateKeys = body.getValue("states").jsonArray.map { it.jsonObject.getValue("key").jsonPrimitive.content }
        val requested = body.getValue("serverRequest").jsonObject.getValue("requestedArguments").jsonObject
            .getValue("requestedStateKeys").jsonArray
            .map { it.jsonObject.getValue("key").jsonObject.getValue("value").jsonObject.getValue("id").jsonPrimitive.content }
        assertEquals(stateKeys, requested)
        assertEquals(requested.toSet(), payloadStateKeys(body).toSet())
    }

    @Test
    fun `a game's own states and payload keys follow the shared ones`() {
        val play = LinkedInPlay(
            states = mapOf(
                "queensBoardStateBinding" to LinkedInPlayValue.Strings(listOf("EMPTY", "QUEEN")),
                "queensBacktrackBinding" to LinkedInPlayValue.Number(3),
            ),
            payloadKeys = mapOf("playRecord" to "queensBoardStateBinding", "numBacktracks" to "queensBacktrackBinding"),
            mistakeFree = true,
        )
        val body = build(LinkedInPostFixtures.post().copy(gameTypeId = 3, play = play), LinkedInPostFixtures.dailyPage)

        val keys = body.getValue("states").jsonArray.map { it.jsonObject.getValue("key").jsonPrimitive.content }
        assertEquals(listOf("queensBoardStateBinding", "queensBacktrackBinding"), keys.takeLast(2))
        val payload = body.getValue("requestedArguments").jsonObject.getValue("payload").jsonObject
        assertEquals(JsonPrimitive("3"), payload.getValue("gameTypeId"))
        assertEquals(binding("queensBoardStateBinding"), payload.getValue("playRecord"))
        assertEquals(listOf("playRecord", "numBacktracks"), payload.keys.toList().takeLast(2))
    }

    private fun build(post: LinkedInPost, page: LinkedInGamePage): JsonObject =
        Json.parseToJsonElement(UpdateGameStateBody.build(post, page)).jsonObject

    private fun states(body: JsonObject): Map<String, JsonElement> =
        body.getValue("states").jsonArray.associate {
            it.jsonObject.getValue("key").jsonPrimitive.content to it.jsonObject.getValue("value")
        }

    private fun payloadStateKeys(body: JsonObject): List<String> =
        body.getValue("requestedArguments").jsonObject.getValue("payload").jsonObject.values
            .mapNotNull { (it as? JsonObject)?.get("key")?.jsonPrimitive?.content }

    private fun bigint(digits: String) = buildJsonObject {
        put("type", "bigint")
        put("value", digits)
    }

    private fun binding(key: String) = buildJsonObject {
        put("key", key)
        put("namespace", "MemoryNamespace")
    }

    /** [body] with the one state [key] set to [value], in every place the capture repeats its states. */
    private fun withState(body: JsonObject, key: String, value: JsonElement): JsonObject {
        fun JsonArray.set(): JsonArray = buildJsonArray {
            forEach { state ->
                val entry = state.jsonObject
                add(if (entry.getValue("key").jsonPrimitive.content == key) JsonObject(entry + ("value" to value)) else entry)
            }
        }
        val args = body.getValue("requestedArguments").jsonObject
        return JsonObject(
            body + mapOf(
                "states" to body.getValue("states").jsonArray.set(),
                "requestedArguments" to JsonObject(args + ("states" to args.getValue("states").jsonArray.set())),
            ),
        )
    }
}
