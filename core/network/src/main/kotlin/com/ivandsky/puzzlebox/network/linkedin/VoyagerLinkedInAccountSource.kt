package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead

/**
 * Reads the player's own LinkedIn account with their session: one GET per call, or none when signed out
 * ([VoyagerGraphql] sends and classifies each).
 */
internal class VoyagerLinkedInAccountSource(private val graphql: VoyagerGraphql) : LinkedInAccountSource {

    override suspend fun check(gameTypeId: Int): LinkedInRead<String> =
        graphql.read(VoyagerQueries.TODAYS_GAME, VoyagerQueries.todaysGameVariables(gameTypeId), VoyagerTodayParser::profileId)

    override suspend fun ownResult(
        profileId: String,
        gameTypeId: Int,
        puzzleNumber: Int,
    ): LinkedInRead<LinkedInOwnResult> {
        val urn = VoyagerQueries.gameUrn(profileId, gameTypeId, puzzleNumber)
        return graphql.read(VoyagerQueries.END_PAGE, VoyagerQueries.endPageVariables(urn)) {
            VoyagerOwnResultParser.parse(it, urn)
        }
    }

    override suspend fun board(
        profileId: String,
        gameTypeId: Int,
        todayNumber: Int,
        delta: Int,
    ): LinkedInRead<LinkedInBoard> {
        // Today's URN even for a past day: the delta selects the day, and a past URN returns today's board.
        val todayUrn = VoyagerQueries.gameUrn(profileId, gameTypeId, todayNumber)
        return graphql.read(VoyagerQueries.CONNECTIONS, VoyagerQueries.connectionsVariables(todayUrn, delta), VoyagerBoardParser::parse)
    }
}
