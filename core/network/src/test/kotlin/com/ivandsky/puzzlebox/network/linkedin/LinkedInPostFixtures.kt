package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInGamePage
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlay
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlayValue
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPost
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The redacted captures of the post wire and the values the tests build from them. */
internal object LinkedInPostFixtures {
    fun text(name: String): String =
        requireNotNull(LinkedInPostFixtures::class.java.classLoader).getResource("fixtures/linkedin-post/$name")!!.readText()

    val winRequest: JsonObject get() = Json.parseToJsonElement(text("zip-update-game-state-win.request.json")).jsonObject

    /** The 36 cells of the win capture's path, row-major strings in path order. */
    val capturedPath: List<String>
        get() = winRequest.getValue("states").jsonArray
            .single { it.jsonObject.getValue("key").jsonPrimitive.content == "zipGamePathBinding" }
            .jsonObject.getValue("value").jsonArray.map { it.jsonPrimitive.content }

    /** The play of the win capture: its path and no backtracks. */
    fun capturedPlay(mistakeFree: Boolean = true) = LinkedInPlay(
        states = mapOf(
            "zipGamePathBinding" to LinkedInPlayValue.Strings(capturedPath),
            "zipNumBacktracksBinding" to LinkedInPlayValue.Number(0),
        ),
        payloadKeys = mapOf("playRecord" to "zipGamePathBinding", "numBacktracks" to "zipNumBacktracksBinding"),
        mistakeFree = mistakeFree,
    )

    fun post(timeMs: Long = 4_501, hintUsed: Boolean = false, mistakeFree: Boolean = true) = LinkedInPost(
        gamePath = "games/zip/",
        gameTypeId = 6,
        puzzleNumber = 608,
        timeMs = timeMs,
        hintUsed = hintUsed,
        play = capturedPlay(mistakeFree),
    )

    /** The state the win capture's page carried: the tutorial puzzle, no plays, request time 0. */
    val capturePage = LinkedInGamePage(
        puzzleId = 1,
        profileId = "ACoAAFAKEPROFILE0000000000000000000000000",
        hasWon = false,
        isTutorial = true,
        totalPlays = 0,
        wins = 0,
        requestTime = 0,
    )

    /** What the daily page fixture serves. */
    val dailyPage = LinkedInGamePage(
        puzzleId = 608,
        profileId = "ACoAAFAKEPROFILE0000000000000000000000000",
        hasWon = true,
        isTutorial = false,
        totalPlays = 597,
        wins = 597,
        requestTime = 0,
    )
}
