package com.ivandsky.puzzlebox.game

import java.net.URLEncoder
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * One LinkedIn game's by-number edition: day→number, the games-by-id request, and its
 * identity check (ADR-0017). The one place that knows LinkedIn's `games-by-id` persisted
 * query (spec A2) and the one by-id fetch path, so each LinkedIn source keeps only its
 * [gameTypeId], [day1] and the extract → parse → gate steps on the checked body.
 *
 * Bodies are read by regex: core:game stays dependency-free, and the game's own extractor
 * and parser still own the puzzle object.
 *
 * @param gameTypeId LinkedIn's game type: Queens 3, Tango 5, Zip 6, Mini Sudoku 7
 * @param day1 the game's PT day of puzzle #1 (A3)
 * @param path the game's route, e.g. `games/zip/`; its plain page GET is the session handshake (F1)
 * @param objectName the payload object the game extracts (diagnostics only, as in [FeedRequest])
 */
class LinkedInEdition(
    val gameTypeId: Int,
    val day1: LocalDate,
    private val path: String,
    private val objectName: String,
) {

    private val editionUrn = Regex("\"entityUrn\"\\s*:\\s*\"urn:li:fsd_game:\\([^,\"()]*,$gameTypeId,(\\d+)\\)\"")

    /**
     * The puzzle number of [day], already the game's PT day: (day − day1) + 1 (A3). LinkedIn
     * documents no gap-free numbering; ADR-0017 records the assumption.
     */
    fun number(day: LocalDate): Int = ChronoUnit.DAYS.between(day1, day).toInt() + 1

    /** [number] of [day], or null before [day1]: the game numbers no day before #1 (A3). */
    fun numberOrNull(day: LocalDate): Int? = if (day.isBefore(day1)) null else number(day)

    /**
     * The [check]ed games-by-id body of [day]'s edition over [transport]. A number outside
     * #1..number([today]) is [FetchError.NotFound] without a request: LinkedIn serves
     * tomorrow's puzzle too, so only the game's own today bounds the archive (A4). Transport
     * errors pass through unchanged; a body of another edition is [FetchError.Untrusted] and
     * a null `gamePuzzle` is [FetchError.NotFound] (A6).
     *
     * @param today the game's today in its cadence zone; the caller owns the clock
     */
    suspend fun fetch(transport: FeedTransport, day: LocalDate, today: LocalDate): FetchResult<String> {
        val number = number(day)
        val todays = number(today)
        if (number !in 1..todays) {
            return FetchResult.Err(FetchError.NotFound("#$number for gameTypeId $gameTypeId is outside #1..#$todays"))
        }
        val body = when (val fetched = transport.fetch(request(number))) {
            is FetchResult.Ok -> fetched.value
            is FetchResult.Err -> return fetched
        }
        return check(body, number)?.let { FetchResult.Err(it) } ?: FetchResult.Ok(body)
    }

    /**
     * The games-by-id request for edition [number] on the ADR-0016 persisted-query rung. It
     * skips the stream rung, whose body is always today's puzzle and sets no cookie (A5, F1),
     * so the transport's page handshake alone opens the session. It accepts any games-by-id
     * body of this game, whatever its number, so a wrong edition reaches [check] as terminal
     * Untrusted instead of falling through the ladder.
     */
    fun request(number: Int): FeedRequest = FeedRequest(
        path = path,
        objectName = objectName,
        accepts = { body -> BY_ID_ROOT.containsMatchIn(body) && editionUrn.containsMatchIn(body) },
        persistedQuery = PersistedQuery(queryId = QUERY_ID, variables = "(gameUrn:${encodedUrn(number)})"),
        stream = false,
    )

    /**
     * Null when [body] is edition [number] with a puzzle. The edition is `entityUrn`
     * component 3, never `puzzleId`, which holds today's number (A6). A body without this
     * game's urn, or with any other number, is [FetchError.Untrusted]; a null
     * `gamePuzzle` (no such edition) is [FetchError.NotFound].
     */
    fun check(body: String, number: Int): FetchError? {
        val editions = editionUrn.findAll(body).map { it.groupValues[1] }.toSet()
        if (editions.isEmpty()) return FetchError.Untrusted("no entityUrn for gameTypeId $gameTypeId")
        if (editions != setOf(number.toString())) {
            return FetchError.Untrusted("requested #$number, entityUrn says #${editions.joinToString()}")
        }
        if (NULL_PUZZLE.containsMatchIn(body)) return FetchError.NotFound("no puzzle #$number for gameTypeId $gameTypeId")
        return null
    }

    // LinkedIn answers a raw urn with 400, so the whole urn is percent-encoded (research 02).
    // The String-charset overload: encode(String, Charset) is Android API 33+ and minSdk is 26.
    private fun encodedUrn(number: Int): String =
        URLEncoder.encode("urn:li:fsd_game:($GUEST_MEMBER,$gameTypeId,$number)", Charsets.UTF_8.name())

    private companion object {
        /** LinkedIn's `games-by-id` persisted query (research 02); the hash is data (ADR-0016 §3). */
        const val QUERY_ID = "voyagerIdentityDashGames.f6c16c0fa3fc425ade72fdb9d4e2e133"

        /** The anonymous member every logged-out session shares; member `0` answers 500 (research 02). */
        const val GUEST_MEMBER = "ACoAAAAAAAABWzs7tPEzjbzdTu0bZDhDZpin_Ds"

        /** The games-by-id root in both the plain and the normalized (`*`-reference) form. */
        val BY_ID_ROOT = Regex("\"\\*?identityDashGamesById\"")

        val NULL_PUZZLE = Regex("\"gamePuzzle\"\\s*:\\s*null")
    }
}
