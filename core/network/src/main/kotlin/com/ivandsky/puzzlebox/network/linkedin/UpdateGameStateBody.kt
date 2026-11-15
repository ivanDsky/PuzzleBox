package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInGamePage
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlayValue
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPost
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The JSON body of LinkedIn's `updateGameState` server request, shaped as the web client sends it in the
 * redacted win capture: a state bag, the same states again under `requestedArguments`, and a payload that
 * binds each argument to a state key. Server-issued values (puzzle, profile, request time, play counts)
 * come from [LinkedInGamePage] and are echoed as served; the rest is the PuzzleBox solve.
 */
internal object UpdateGameStateBody {

    fun build(post: LinkedInPost, page: LinkedInGamePage): String {
        val states = states(post, page)
        val keys = states.map { it.key }
        val stateBag = JsonArray(states.map(State::json))
        val serverRequestArguments = arguments(keys, post)
        val body = buildJsonObject {
            put("requestId", REQUEST_ID)
            putJsonObject("serverRequest") {
                put("requestId", REQUEST_ID)
                put("requestedArguments", serverRequestArguments)
                put("isApfcEnabled", false)
                put("isStreaming", false)
                put("rumPageKey", "")
            }
            put("states", stateBag)
            put(
                "requestedArguments",
                JsonObject(
                    serverRequestArguments + mapOf(
                        "states" to stateBag,
                        "screenId" to JsonPrimitive(SCREEN_ID),
                        "knownTemplateIds" to JsonArray(emptyList()),
                    ),
                ),
            )
        }
        return Json.encodeToString(JsonObject.serializer(), body)
    }

    private fun states(post: LinkedInPost, page: LinkedInGamePage): List<State> = buildList {
        add(State("gameBoardPuzzleId", bigint(page.puzzleId), "signedLongValue"))
        add(State("gameBoardProfileId", JsonPrimitive(page.profileId), "stringValue"))
        add(State("gameBoardHasWon", JsonPrimitive(true)))
        add(State("gameBoardTimeElapsed", bigint(post.timeMs), "signedLongValue"))
        add(State("gameBoardTotalPlaysCount", JsonPrimitive(page.totalPlays), "intValue"))
        add(State("gameBoardWinCount", JsonPrimitive(page.wins), "intValue"))
        add(State("gameIsFlawlessBinding", JsonPrimitive(post.flawless), "booleanValue"))
        add(State("gameBoardHasUsedHint", JsonPrimitive(post.hintUsed), "booleanValue"))
        add(State("gameIsMistakeFreeBinding", JsonPrimitive(post.play.mistakeFree), "booleanValue"))
        add(State("gameIsTutorialGameBinding", JsonPrimitive(false), "booleanValue"))
        add(State("gameBoardGameRequestTime", bigint(page.requestTime), "signedLongValue"))
        post.play.states.forEach { (key, value) ->
            add(
                when (value) {
                    is LinkedInPlayValue.Strings -> State(key, JsonArray(value.values.map(::JsonPrimitive)))
                    is LinkedInPlayValue.Number -> State(key, JsonPrimitive(value.value), "intValue")
                },
            )
        }
    }

    private fun arguments(stateKeys: List<String>, post: LinkedInPost): JsonObject = buildJsonObject {
        put("\$type", REQUESTED_ARGUMENTS_TYPE)
        putJsonArray("requestedStateKeys") {
            stateKeys.forEach { key ->
                add(
                    buildJsonObject {
                        putJsonObject("key") { putJsonObject("value") { put("\$case", "id"); put("id", key) } }
                    },
                )
            }
        }
        putJsonObject("payload") {
            put("puzzleId", binding("gameBoardPuzzleId"))
            put("profileId", binding("gameBoardProfileId"))
            put("gameTypeId", post.gameTypeId.toString())
            put("isEmbeddedInOnboarding", false)
            put("shouldShowInlineCompletionCard", false)
            put("isBonusGame", false)
            put("isGameEnded", binding("gameBoardHasWon"))
            put("timeElapsed", binding("gameBoardTimeElapsed"))
            put("totalPlaysCount", binding("gameBoardTotalPlaysCount"))
            put("winCount", binding("gameBoardWinCount"))
            put("isFlawless", binding("gameIsFlawlessBinding"))
            put("hasUsedHint", binding("gameBoardHasUsedHint"))
            put("isMistakeFree", binding("gameIsMistakeFreeBinding"))
            put("isTutorialGame", binding("gameIsTutorialGameBinding"))
            put("gameRequestTime", binding("gameBoardGameRequestTime"))
            post.play.payloadKeys.forEach { (argument, key) -> put(argument, binding(key)) }
        }
        putJsonObject("requestMetadata") { put("\$type", REQUEST_METADATA_TYPE) }
    }

    private fun binding(key: String): JsonElement = buildJsonObject {
        put("key", key)
        put("namespace", NAMESPACE)
    }

    private fun bigint(value: Long): JsonElement = buildJsonObject {
        put("type", "bigint")
        put("value", value.toString())
    }

    /** One entry of the state bag; [protoCase] is absent where the capture has none. */
    private class State(val key: String, val value: JsonElement, val protoCase: String? = null) {
        val json: JsonObject
            get() = buildJsonObject {
                put("key", key)
                put("namespace", NAMESPACE)
                put("value", value)
                if (protoCase != null) put("originalProtoCase", protoCase)
                putJsonObject("protoKey") {
                    put("\$type", "proto.sdui.Key")
                    putJsonObject("value") { put("\$case", "id"); put("id", key) }
                }
            }
    }

    private const val REQUEST_ID = "updateGameState"
    private const val NAMESPACE = "MemoryNamespace"
    private const val SCREEN_ID = "com.linkedin.sdui.flagshipnav.games.GamePlayDesktopPage"
    private const val REQUESTED_ARGUMENTS_TYPE = "proto.sdui.actions.requests.RequestedArguments"
    private const val REQUEST_METADATA_TYPE = "proto.sdui.common.RequestMetadata"
}
