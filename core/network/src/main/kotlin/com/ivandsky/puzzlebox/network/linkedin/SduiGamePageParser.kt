package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInGamePage

/**
 * Reads the server-issued board state out of a game page's RSC stream, which the web client echoes back in
 * `updateGameState`. Each state sits in the stream as `{"key":{"key":{"value":{"$case":"id","id":K}}},"value":
 * {"$case":C,"C":V}}`; the first one of a key is the initial state, so a later update never wins. A long is
 * an RSC bigint, `"$n<digits>"`, or a plain `intValue`.
 *
 * `gameBoardHasWon` is not part of the initial state on a page LinkedIn serves; the web client sets it when
 * the board is won. Whether the player already has a result is read from `mostRecentGameState`: a saved play
 * on a solved page, `"$undefined"` on an unsolved one. The completion attributes are on both, so they are no
 * signal. An explicit `gameBoardHasWon` state, if LinkedIn ever serves one, takes precedence.
 *
 * Any other state missing or malformed returns null: the page changed shape, so nothing is sent from it.
 */
internal object SduiGamePageParser {

    fun parse(rsc: String): LinkedInGamePage? {
        val puzzleId = long(rsc, PUZZLE_ID) ?: return null
        val profileId = string(rsc, PROFILE_ID) ?: return null
        val isTutorial = boolean(rsc, IS_TUTORIAL) ?: return null
        val totalPlays = long(rsc, TOTAL_PLAYS) ?: return null
        val wins = long(rsc, WINS) ?: return null
        val requestTime = long(rsc, REQUEST_TIME) ?: return null
        return LinkedInGamePage(
            puzzleId = puzzleId,
            profileId = profileId,
            hasWon = boolean(rsc, HAS_WON) ?: SAVED_PLAY.containsMatchIn(rsc),
            isTutorial = isTutorial,
            totalPlays = totalPlays,
            wins = wins,
            requestTime = requestTime,
        )
    }

    private fun long(rsc: String, state: Regex): Long? {
        val (case, raw) = first(rsc, state) ?: return null
        return when (case) {
            "signedLongValue" -> BIGINT.matchEntire(raw)?.groupValues?.get(1)?.toLongOrNull()
            "intValue" -> raw.toLongOrNull()
            else -> null
        }
    }

    private fun boolean(rsc: String, state: Regex): Boolean? {
        val (case, raw) = first(rsc, state) ?: return null
        return if (case == "booleanValue") raw.toBooleanStrictOrNull() else null
    }

    private fun string(rsc: String, state: Regex): String? {
        val (case, raw) = first(rsc, state) ?: return null
        if (case != "stringValue" || !raw.startsWith('"')) return null
        return raw.substring(1, raw.length - 1).ifEmpty { null }
    }

    /** The case and raw literal of the first state [state] finds, or null when the stream has none. */
    private fun first(rsc: String, state: Regex): Pair<String, String>? =
        state.find(rsc)?.let { it.groupValues[1] to it.groupValues[2] }

    private fun stateOf(key: String) = Regex(
        """\{"key":\{"key":\{"value":\{"\${'$'}case":"id","id":"$key"\}\}\},"value":""" +
            """\{"\${'$'}case":"([A-Za-z]+)","\1":("[^"\\]*"|[^,}"]+)\}""",
    )

    private val PUZZLE_ID = stateOf("gameBoardPuzzleId")
    private val PROFILE_ID = stateOf("gameBoardProfileId")
    private val HAS_WON = stateOf("gameBoardHasWon")
    private val IS_TUTORIAL = stateOf("gameIsTutorialGameBinding")
    private val TOTAL_PLAYS = stateOf("gameBoardTotalPlaysCount")
    private val WINS = stateOf("gameBoardWinCount")
    private val REQUEST_TIME = stateOf("gameBoardGameRequestTime")
    private val BIGINT = Regex(""""\${'$'}n(-?[0-9]+)"""")
    private val SAVED_PLAY = Regex(""""mostRecentGameState":\{""")
}
