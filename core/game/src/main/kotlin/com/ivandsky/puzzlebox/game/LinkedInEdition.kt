package com.ivandsky.puzzlebox.game

import java.net.URLEncoder
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * One LinkedIn game's by-number edition: day→number, the games-by-id request, and its
 * identity check (ADR-0017). The one place that knows LinkedIn's `games-by-id` persisted
 * query (spec A2), so each LinkedIn source keeps only its [gameTypeId] and [day1].
 *
 * Bodies are read by regex: core:game stays dependency-free, and the game's own extractor
 * and parser still own the puzzle object.
 *
 * @param gameTypeId LinkedIn's game type: Queens 3, Tango 5, Zip 6, Mini Sudoku 7
 * @param day1 the game's PT day of puzzle #1 (A3)
 * @param path the game's route, e.g. `games/zip/`; its stream GET only seeds the session cookie (A5)
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

    /**
     * The games-by-id request for edition [number] on the ADR-0016 persisted-query rung. It
     * accepts any games-by-id body of this game, whatever its number, so a wrong edition
     * reaches [check] as terminal Untrusted instead of falling through the ladder; it never
     * accepts a stream body, which is always today's puzzle (A5).
     */
    fun request(number: Int): FeedRequest = FeedRequest(
        path = path,
        objectName = objectName,
        accepts = { body -> BY_ID_ROOT.containsMatchIn(body) && editionUrn.containsMatchIn(body) },
        persistedQuery = PersistedQuery(queryId = QUERY_ID, variables = "(gameUrn:${encodedUrn(number)})"),
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
    private fun encodedUrn(number: Int): String =
        URLEncoder.encode("urn:li:fsd_game:($GUEST_MEMBER,$gameTypeId,$number)", Charsets.UTF_8)

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
