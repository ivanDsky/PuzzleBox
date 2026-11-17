package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInGamePage
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOpening
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlay
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlayValue
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPost
import com.ivandsky.puzzlebox.game.linkedin.LinkedInProgress
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
        progress = LinkedInProgress.Fresh,
        isTutorial = true,
        totalPlays = 0,
        wins = 0,
        requestTime = 0,
    )

    /** What the daily page fixture serves. */
    val dailyPage = LinkedInGamePage(
        puzzleId = 608,
        profileId = "ACoAAFAKEPROFILE0000000000000000000000000",
        progress = LinkedInProgress.Won,
        isTutorial = false,
        totalPlays = 597,
        wins = 597,
        requestTime = 0,
    )

    /** What the unsolved daily page fixture serves: #609, yesterday's totals, request time 0. */
    val unsolvedDailyPage = dailyPage.copy(puzzleId = 609, progress = LinkedInProgress.Fresh)

    /** The derived in-progress page: the unsolved one after an accepted save, plays 598. */
    val inProgressDailyPage = unsolvedDailyPage.copy(progress = LinkedInProgress.Started, totalPlays = 598)

    val dailyWinRequest: JsonObject get() = Json.parseToJsonElement(text("zip-update-game-state-daily-win.request.json")).jsonObject

    val dailyFirstMoveRequest: JsonObject
        get() = Json.parseToJsonElement(text("zip-update-game-state-daily-first-move.request.json")).jsonObject

    /** The 36 cells of the daily win capture's path, in path order. */
    val dailyPath: List<String>
        get() = dailyWinRequest.getValue("states").jsonArray
            .single { it.jsonObject.getValue("key").jsonPrimitive.content == "zipGamePathBinding" }
            .jsonObject.getValue("value").jsonArray.map { it.jsonPrimitive.content }

    /** The post of the daily capture: puzzle 609, 7003 ms, one backtrack, no hint, so not mistake-free and not flawless. */
    fun dailyPost() = LinkedInPost(
        gamePath = "games/zip/",
        gameTypeId = 6,
        puzzleNumber = 609,
        timeMs = 7_003,
        hintUsed = false,
        play = LinkedInPlay(
            states = mapOf(
                "zipGamePathBinding" to LinkedInPlayValue.Strings(dailyPath),
                "zipNumBacktracksBinding" to LinkedInPlayValue.Number(1),
            ),
            payloadKeys = mapOf("playRecord" to "zipGamePathBinding", "numBacktracks" to "zipNumBacktracksBinding"),
            mistakeFree = false,
            opening = LinkedInOpening(
                states = mapOf(
                    "zipGamePathBinding" to LinkedInPlayValue.Strings(dailyPath.take(2)),
                    "zipNumBacktracksBinding" to LinkedInPlayValue.Number(0),
                ),
                timeMs = 1_000,
            ),
        ),
    )

    val tangoWinRequest: JsonObject get() = Json.parseToJsonElement(text("tango-update-game-state-daily-win.request.json")).jsonObject

    val tangoOpeningRequest: JsonObject get() = Json.parseToJsonElement(text("tango-update-game-state-daily-opening.request.json")).jsonObject

    /** The Tango daily (#770) at load: yesterday's totals of 594 plays and wins, request time 0. */
    val tangoPage = LinkedInGamePage(
        puzzleId = 770,
        profileId = "ACoAAFAKEPROFILE0000000000000000000000000",
        progress = LinkedInProgress.Fresh,
        isTutorial = false,
        totalPlays = 594,
        wins = 594,
        requestTime = 0,
    )

    private fun tangoStrings(request: JsonObject, key: String): List<String>? =
        request.getValue("states").jsonArray
            .firstOrNull { it.jsonObject.getValue("key").jsonPrimitive.content == key }
            ?.jsonObject?.getValue("value")?.jsonArray?.map { it.jsonPrimitive.content }

    /** The post of the Tango win capture: the captured board and solve order, 34035 ms, opening at 583 ms with no solve order. */
    fun tangoPost() = LinkedInPost(
        gamePath = "games/tango/",
        gameTypeId = 5,
        puzzleNumber = 770,
        timeMs = 34_035,
        hintUsed = false,
        play = LinkedInPlay(
            states = mapOf(
                "tangoBoardStateBinding" to LinkedInPlayValue.Strings(checkNotNull(tangoStrings(tangoWinRequest, "tangoBoardStateBinding"))),
                "tangoSolveOrderBinding" to LinkedInPlayValue.Strings(checkNotNull(tangoStrings(tangoWinRequest, "tangoSolveOrderBinding"))),
            ),
            payloadKeys = mapOf("playRecord" to "tangoBoardStateBinding", "solveOrder" to "tangoSolveOrderBinding"),
            mistakeFree = false,
            opening = LinkedInOpening(
                states = mapOf(
                    "tangoBoardStateBinding" to LinkedInPlayValue.Strings(checkNotNull(tangoStrings(tangoOpeningRequest, "tangoBoardStateBinding"))),
                ),
                timeMs = 583,
            ),
        ),
    )

    val queensWinRequest: JsonObject get() = Json.parseToJsonElement(text("queens-update-game-state-daily-win.request.json")).jsonObject

    val queensOpeningRequest: JsonObject get() = Json.parseToJsonElement(text("queens-update-game-state-daily-opening.request.json")).jsonObject

    /** What the unsolved Queens #930 daily page serves: plays 725, wins 722, request time 0. */
    val queensDailyPage = unsolvedDailyPage.copy(puzzleId = 930, totalPlays = 725, wins = 722)

    /** The Queens state [key] of a capture as the encoder holds it: its strings. */
    private fun JsonObject.queensStrings(key: String) = LinkedInPlayValue.Strings(
        getValue("states").jsonArray.single { it.jsonObject.getValue("key").jsonPrimitive.content == key }
            .jsonObject.getValue("value").jsonArray.map { it.jsonPrimitive.content },
    )

    /**
     * The post of the Queens captures: puzzle 930, 80075 ms, a conflict on the way (not mistake-free), no hint, with the
     * opening save of 1001 ms. The play values are the captured ones, so only the body is under test.
     */
    fun queensPost() = LinkedInPost(
        gamePath = "games/queens/",
        gameTypeId = 3,
        puzzleNumber = 930,
        timeMs = 80_075,
        hintUsed = false,
        play = LinkedInPlay(
            states = mapOf(
                "queensBoardStateBinding" to queensWinRequest.queensStrings("queensBoardStateBinding"),
                "queensSolveOrderBinding" to queensWinRequest.queensStrings("queensSolveOrderBinding"),
            ),
            payloadKeys = mapOf("playRecord" to "queensBoardStateBinding", "solveOrder" to "queensSolveOrderBinding"),
            mistakeFree = false,
            opening = LinkedInOpening(
                states = mapOf(
                    "queensBoardStateBinding" to queensOpeningRequest.queensStrings("queensBoardStateBinding"),
                    "queensSolveOrderBinding" to queensOpeningRequest.queensStrings("queensSolveOrderBinding"),
                ),
                timeMs = 1_001,
            ),
        ),
    )
}
