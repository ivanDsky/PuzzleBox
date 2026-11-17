package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInGamePage
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPost
import com.ivandsky.puzzlebox.game.linkedin.LinkedInVoyagerCell
import com.ivandsky.puzzlebox.game.linkedin.LinkedInVoyagerPlay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The JSON bodies of LinkedIn's voyager game save, shaped as the web client sends them in the redacted Mini
 * Sudoku daily capture (research 03, Addendum 8): the first save ([opening]) and the win ([win]). Each is the
 * member's whole stored record of the game, keyed by the game URN built from [LinkedInGamePage]'s member and the
 * post's own game type and puzzle number; nothing else of the page is echoed.
 */
internal object VoyagerSaveGameBody {

    /** The first save: the board after the first placement, in progress and with no flaw yet. */
    fun opening(post: LinkedInPost, play: LinkedInVoyagerPlay, page: LinkedInGamePage): String = body(
        post, play, page,
        // The capture's first save alone asks for web metadata; the later ones do not.
        Step(PLAY_STATE_IN_PROGRESS, play.openingTimeMs, flawless = true, hintFree = true, mistakeFree = true, play.openingCells, webMetadata = true),
    )

    /** The win: the solved board, with the solve's time and flaws. */
    fun win(post: LinkedInPost, play: LinkedInVoyagerPlay, page: LinkedInGamePage): String = body(
        post, play, page,
        Step(PLAY_STATE_SOLVED, post.timeMs, post.flawless, hintFree = !post.hintUsed, post.play.mistakeFree, play.cells, webMetadata = false),
    )

    /** What differs between the opening and the win of one post. */
    private class Step(
        val playState: String,
        val timeMs: Long,
        val flawless: Boolean,
        val hintFree: Boolean,
        val mistakeFree: Boolean,
        val cells: List<LinkedInVoyagerCell>,
        val webMetadata: Boolean,
    )

    private fun body(post: LinkedInPost, play: LinkedInVoyagerPlay, page: LinkedInGamePage, step: Step): String {
        val body = buildJsonObject {
            putJsonObject("variables") {
                putJsonObject("entity") {
                    putJsonObject("entity") { put("gameStoredRecord", record(play.stateName, step)) }
                    put("resourceKey", VoyagerQueries.gameUrn(page.profileId, post.gameTypeId, post.puzzleNumber))
                }
            }
            put("queryId", VoyagerQueries.SAVE_GAME)
            if (step.webMetadata) put("includeWebMetadata", true)
        }
        return Json.encodeToString(JsonObject.serializer(), body)
    }

    private fun record(stateName: String, step: Step): JsonObject = buildJsonObject {
        put("gamePlayState", step.playState)
        put("timeElapsed", seconds(step.timeMs))
        put("isFlawless", step.flawless)
        putJsonObject("completionAttributes") {
            put("isHintFree", step.hintFree)
            put("isMistakeFree", step.mistakeFree)
        }
        putJsonObject("gameStateUnion") {
            putJsonArray(stateName) {
                step.cells.forEach { cell ->
                    add(
                        buildJsonObject {
                            put("cellIdx", cell.idx)
                            putJsonObject("cellContentUnion") { put("cellValue", cell.value) }
                        },
                    )
                }
            }
        }
    }

    /** This wire counts whole seconds, as the game's clock shows them; a save in the first second is sent as one. */
    private fun seconds(timeMs: Long): Long = (timeMs / MS_PER_SECOND).coerceAtLeast(MIN_SECONDS)

    private const val PLAY_STATE_IN_PROGRESS = "IN_PROGRESS"
    private const val PLAY_STATE_SOLVED = "END_SOLVED"
    private const val MS_PER_SECOND = 1_000L
    private const val MIN_SECONDS = 1L
}
