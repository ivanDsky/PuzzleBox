package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInGamePage
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlay
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPost
import com.ivandsky.puzzlebox.game.linkedin.LinkedInProgress
import com.ivandsky.puzzlebox.game.linkedin.LinkedInVoyagerCell
import com.ivandsky.puzzlebox.game.linkedin.LinkedInVoyagerPlay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The redacted Mini Sudoku daily capture of the voyager wire (research 03, Addendum 8) and the values the tests
 * build from it. The two `*.derived.json` game reads are the unplayed capture with only the stored play state
 * changed and the fields no reader uses dropped: LinkedIn's own in-progress and solved reads were not captured.
 */
internal object MiniSudokuVoyagerFixtures {

    const val STATE_NAME = "miniSudokuGameState"

    val unplayedGame: String get() = LinkedInPostFixtures.text("minisudoku-voyager-game-daily-unplayed.response.json")
    val inProgressGame: String get() = LinkedInPostFixtures.text("minisudoku-voyager-game-daily-in-progress.derived.json")
    val solvedGame: String get() = LinkedInPostFixtures.text("minisudoku-voyager-game-daily-solved.derived.json")
    val winResponse: String get() = LinkedInPostFixtures.text("minisudoku-voyager-save-daily-win.response.json")

    val openingRequest: JsonObject get() = request("minisudoku-voyager-save-daily-opening.request.json")
    val winRequest: JsonObject get() = request("minisudoku-voyager-save-daily-win.request.json")

    /** What the unplayed capture serves: #463, two plays and two wins before today, nothing stored yet. */
    val unplayedPage = LinkedInGamePage(
        puzzleId = 463,
        profileId = "ACoAAFAKEPROFILE0000000000000000000000000",
        progress = LinkedInProgress.Fresh,
        isTutorial = false,
        totalPlays = 2,
        wins = 2,
        requestTime = 0,
    )

    val inProgressPage = unplayedPage.copy(progress = LinkedInProgress.Started)

    val solvedPage = unplayedPage.copy(progress = LinkedInProgress.Won)

    /** The post of the capture: puzzle 463 solved in 41 s with no flaw, its first digit placed 3 s in. */
    fun post(timeMs: Long = 41_870, hintUsed: Boolean = false, mistakeFree: Boolean = true) = LinkedInPost(
        gamePath = "games/mini-sudoku/",
        gameTypeId = 7,
        puzzleNumber = 463,
        timeMs = timeMs,
        hintUsed = hintUsed,
        play = LinkedInPlay(
            states = emptyMap(),
            payloadKeys = emptyMap(),
            mistakeFree = mistakeFree,
            voyager = play(),
        ),
    )

    fun play(openingTimeMs: Long = 3_412) = LinkedInVoyagerPlay(
        stateName = STATE_NAME,
        cells = cells(winRequest),
        openingCells = cells(openingRequest),
        openingTimeMs = openingTimeMs,
    )

    /** The stored record a save [request] carries. */
    fun record(request: JsonObject): JsonObject =
        request.getValue("variables").jsonObject.getValue("entity").jsonObject
            .getValue("entity").jsonObject.getValue("gameStoredRecord").jsonObject

    private fun request(name: String): JsonObject = Json.parseToJsonElement(LinkedInPostFixtures.text(name)).jsonObject

    private fun cells(request: JsonObject): List<LinkedInVoyagerCell> =
        record(request).getValue("gameStateUnion").jsonObject.getValue(STATE_NAME).jsonArray.map {
            val cell = it.jsonObject
            LinkedInVoyagerCell(
                idx = cell.getValue("cellIdx").jsonPrimitive.int,
                value = cell.getValue("cellContentUnion").jsonObject.getValue("cellValue").jsonPrimitive.int,
            )
        }
}
