package com.ivandsky.puzzlebox.network.linkedin

/**
 * The Voyager GraphQL operations called with the player's session (the account feature's three reads, and the
 * read and the save of a game LinkedIn still saves over voyager), and how their `variables` are written.
 *
 * The format is LinkedIn's own, not JSON: the tuple's `(`, `)`, `:` and `,` stay literal (encoding them is
 * an HTTP 400), numbers are literal, and string values are percent-encoded like `encodeURIComponent` with
 * `(` and `)` forced to `%28` / `%29`. `URLEncoder` is not a substitute: it turns spaces into `+` and
 * encodes `~` and `*`.
 */
internal object VoyagerQueries {

    const val TODAYS_GAME = "voyagerIdentityDashGames.e3326d1fde9a5931eef881827f23ded9"
    const val END_PAGE = "voyagerIdentityDashGameEndPage.b51bd3075ade52e7e7fb4e1e364df72f"
    const val CONNECTIONS = "voyagerIdentityDashGameConnectionsEntities.a5fed1d462445e698d896bd5563fed9c"

    /**
     * Today's game with the member's stored record of it: the read a voyager save starts from (ADR-0032 §11).
     * The same persisted query the Mini Sudoku feed fetches its puzzle with: keep this id in step with
     * `LinkedInMiniSudokuDailySource.QUERY_ID` in `:games:sudoku`, a separate bounded context that holds its own copy, so a
     * rotation of the id updates both.
     */
    const val TODAYS_GAME_RECORD = "voyagerIdentityDashGames.22685dd4999129d6bed79f33e65dcbc5"

    /** The save mutation of a game LinkedIn has not moved to `updateGameState`, and the action that runs it. */
    const val SAVE_GAME = "voyagerIdentityDashGames.f8508525e36bee5f9a5ab6b637854d87"
    const val EXECUTE = "execute"

    /** One page, no pagination. */
    const val BOARD_COUNT = 50

    private const val URN_PREFIX = "urn:li:fsd_game:"
    private const val UNRESERVED_PUNCTUATION = "-_.!~*'"
    private const val HEX = "0123456789ABCDEF"
    private val gameUrnPattern = Regex("""^urn:li:fsd_game:\(([^,()]+),(\d+),(\d+)\)$""")

    /**
     * Built, never copied from a response: only the end-page and connections operations are keyed by it, and
     * a response's own puzzle id is not reliable for past days.
     */
    fun gameUrn(profileId: String, gameTypeId: Int, puzzleNumber: Int): String =
        "$URN_PREFIX($profileId,$gameTypeId,$puzzleNumber)"

    fun todaysGameVariables(gameTypeId: Int): String = "(gameTypeId:$gameTypeId)"

    fun endPageVariables(gameUrn: String): String = "(gameUrn:${encodeValue(gameUrn)})"

    /** [todayUrn] is today's URN even for past days: the delta selects the day, a past URN returns today's board. */
    fun connectionsVariables(todayUrn: String, delta: Int): String =
        "(gameUrn:${encodeValue(todayUrn)},start:0,count:$BOARD_COUNT,delta:$delta)"

    /** The member's profile id, or null when [gameUrn] is not a game URN. */
    fun profileIdOf(gameUrn: String): String? = gameUrnPattern.find(gameUrn)?.groupValues?.get(1)

    /** `encodeURIComponent` over the UTF-8 bytes, plus `(` and `)` (which it would leave alone). */
    private fun encodeValue(value: String): String = buildString {
        for (byte in value.encodeToByteArray()) {
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            if (code < 0x80 && (char.isLetterOrDigit() || char in UNRESERVED_PUNCTUATION)) {
                append(char)
            } else {
                append('%').append(HEX[code shr 4]).append(HEX[code and 0xF])
            }
        }
    }
}
