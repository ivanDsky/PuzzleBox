package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInGamePage
import com.ivandsky.puzzlebox.game.linkedin.LinkedInProgress

/**
 * Reads the server-issued board state out of a game page's RSC stream, which the web client echoes back in
 * `updateGameState`. Each state sits in the stream as `{"key":{"key":{"value":{"$case":"id","id":K}}},"value":
 * {"$case":C,"C":V}}`; the first one of a key is the initial state, so a later update never wins. A long is
 * an RSC bigint, `"$n<digits>"`, or a plain `intValue`.
 *
 * `gameBoardHasWon` is not part of the initial state on a page LinkedIn serves; the web client sets it when
 * the board is won. Progress is read from `mostRecentGameState`: `"$undefined"` is Fresh, a saved partial
 * play is Started, a complete one is Won. For Zip (`trailGameState`) complete means the trail has as many
 * cells as the board (`gridSize` squared). A saved state of a kind not known here is Won, so nothing is sent
 * from it. An explicit `gameBoardHasWon` true, if LinkedIn ever serves one, is Won. The completion
 * attributes are on every page, so they are no signal.
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
        val progress = progress(rsc) ?: return null
        return LinkedInGamePage(
            puzzleId = puzzleId,
            profileId = profileId,
            progress = progress,
            isTutorial = isTutorial,
            totalPlays = totalPlays,
            wins = wins,
            requestTime = requestTime,
        )
    }

    private fun progress(rsc: String): LinkedInProgress? {
        if (boolean(rsc, HAS_WON) == true) return LinkedInProgress.Won
        val at = rsc.indexOf(SAVED_PLAY)
        if (at < 0) return null
        val start = at + SAVED_PLAY.length
        if (rsc.startsWith(UNDEFINED, start)) return LinkedInProgress.Fresh
        val saved = objectAt(rsc, start) ?: return null
        val case = CASE.find(saved)?.groupValues?.get(1)
        if (case != "trailGameState") return LinkedInProgress.Won
        val cells = CELLS.findAll(saved).sumOf { cells -> cells.groupValues[1].count { it == ',' } + 1 }
        val grid = GRID_SIZE.find(rsc)?.groupValues?.get(1)?.toIntOrNull() ?: return LinkedInProgress.Won
        return if (cells >= grid * grid) LinkedInProgress.Won else LinkedInProgress.Started
    }

    /** The JSON object that starts at [start], or null when it never closes. */
    private fun objectAt(rsc: String, start: Int): String? {
        if (rsc.getOrNull(start) != '{') return null
        var depth = 0
        var inString = false
        var i = start
        while (i < rsc.length) {
            val c = rsc[i]
            when {
                inString -> if (c == '\\') i++ else if (c == '"') inString = false
                c == '"' -> inString = true
                c == '{' -> depth++
                c == '}' -> if (--depth == 0) return rsc.substring(start, i + 1)
            }
            i++
        }
        return null
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
    private const val SAVED_PLAY = "\"mostRecentGameState\":"
    private const val UNDEFINED = "\"\$undefined\""
    private val CASE = Regex(""""\${'$'}case":"([A-Za-z]+)"""")
    private val CELLS = Regex(""""cells":\[([0-9,]+)\]""")
    private val GRID_SIZE = Regex(""""gridSize":([0-9]+)""")
}
