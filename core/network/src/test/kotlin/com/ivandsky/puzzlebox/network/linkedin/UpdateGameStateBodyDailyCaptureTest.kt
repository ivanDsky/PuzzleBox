package com.ivandsky.puzzlebox.network.linkedin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The first-move and win bodies against the unsolved-daily capture (research 03, Addendum 4), as parsed JSON. */
class UpdateGameStateBodyDailyCaptureTest {

    private val post = LinkedInPostFixtures.dailyPost()
    private val page = LinkedInPostFixtures.unsolvedDailyPage

    @Test
    fun `the win body equals the captured daily win request`() {
        assertEquals(LinkedInPostFixtures.dailyWinRequest, parse(UpdateGameStateBody.build(post, page)))
    }

    @Test
    fun `the opening body equals the captured daily first-move request`() {
        assertEquals(LinkedInPostFixtures.dailyFirstMoveRequest, parse(checkNotNull(UpdateGameStateBody.opening(post, page))))
    }

    @Test
    fun `the opening is not a win and is flawless while the win with a backtrack is not`() {
        val opening = states(parse(checkNotNull(UpdateGameStateBody.opening(post, page))))
        val win = states(parse(UpdateGameStateBody.build(post, page)))

        assertEquals(JsonPrimitive(false), opening.getValue("gameBoardHasWon"))
        assertEquals(JsonPrimitive(true), opening.getValue("gameIsFlawlessBinding"))
        assertEquals(JsonPrimitive(true), opening.getValue("gameIsMistakeFreeBinding"))
        assertEquals(JsonPrimitive(true), win.getValue("gameBoardHasWon"))
        assertEquals(JsonPrimitive(false), win.getValue("gameIsFlawlessBinding"))
    }

    @Test
    fun `an opening is never flawed by the hint or flaws of the final play`() {
        val flawed = post.copy(hintUsed = true)

        val opening = states(parse(checkNotNull(UpdateGameStateBody.opening(flawed, page))))

        assertEquals(JsonPrimitive(false), opening.getValue("gameBoardHasUsedHint"))
        assertEquals(JsonPrimitive(true), opening.getValue("gameIsFlawlessBinding"))
    }

    @Test
    fun `a game with no opening has none`() {
        assertNull(UpdateGameStateBody.opening(LinkedInPostFixtures.post(), page))
    }

    private fun parse(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private fun states(body: JsonObject): Map<String, JsonElement> =
        body.getValue("states").jsonArray.associate {
            it.jsonObject.getValue("key").jsonPrimitive.content to it.jsonObject.getValue("value")
        }
}
