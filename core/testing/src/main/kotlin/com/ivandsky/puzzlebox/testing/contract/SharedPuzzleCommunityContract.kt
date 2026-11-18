package com.ivandsky.puzzlebox.testing.contract

import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.data.repository.CommunityListResult
import com.ivandsky.puzzlebox.data.repository.ReportResult
import com.ivandsky.puzzlebox.game.editor.Difficulty
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The community half of [SharedPuzzleRepositoryContract]: listing the public puzzles of the last
 * 24 hours and reporting one. An adapter's contract test subclasses this class, so it runs both
 * halves. A fresh [repository] is signed out, and [seed] keeps a doc's `createdAtMs`.
 */
abstract class SharedPuzzleCommunityContract : SharedPuzzleRepositoryContract() {

    /** Cuts the server off for the calls that follow. */
    protected abstract fun goOffline()

    /** Sets the device clock the list measures its 24 hours from. */
    protected abstract fun setNow(nowMs: Long)

    @Before
    fun startTheClock() = setNow(NOW)

    /** A public puzzle by [AUTHOR], published [ageMs] before [NOW]. */
    protected fun publicDoc(
        code: String,
        ageMs: Long = HOUR_MS,
        gameId: String = "queens",
        difficulty: Difficulty.Level? = null,
    ) = doc(code = code(code)).copy(
        gameId = gameId,
        createdAtMs = NOW - ageMs,
        visibility = SharedPuzzleDoc.Visibility.Public,
        difficulty = difficulty,
    )

    private suspend fun listedCodes(filter: CommunityFilter = CommunityFilter()): List<String> =
        when (val result = repository.listPublic(filter)) {
            is CommunityListResult.Listed -> result.puzzles.map { it.code.value }
            else -> throw AssertionError("expected a list, got $result")
        }

    // ----- list -----

    @Test
    fun listingReturnsThePublicPuzzlesNewestFirst() = runTest {
        val oldest = publicDoc("K7Q2MA", ageMs = 3 * HOUR_MS)
        val newest = publicDoc("K7Q2MB", ageMs = 1 * HOUR_MS, difficulty = Difficulty.Level.Medium)
        val middle = publicDoc("K7Q2MC", ageMs = 2 * HOUR_MS)
        listOf(oldest, newest, middle).forEach { seed(it) }

        assertEquals(
            CommunityListResult.Listed(listOf(newest, middle, oldest)),
            repository.listPublic(CommunityFilter()),
        )
    }

    @Test
    fun listingLeavesOutLinkOnlyPuzzles() = runTest {
        seed(publicDoc("K7Q2MA"))
        seed(doc(code("K7Q2MB")).copy(createdAtMs = NOW - HOUR_MS))

        assertEquals(listOf("K7Q2MA"), listedCodes())
    }

    @Test
    fun listingLeavesOutUnpublishedPuzzles() = runTest {
        seed(publicDoc("K7Q2MA"))
        seed(publicDoc("K7Q2MB").copy(unpublished = true))

        assertEquals(listOf("K7Q2MA"), listedCodes())
    }

    @Test
    fun listingKeepsOnlyTheLastTwentyFourHours() = runTest {
        seed(publicDoc("K7Q2MA", ageMs = 24 * HOUR_MS))
        seed(publicDoc("K7Q2MB", ageMs = 24 * HOUR_MS + 1))
        seed(publicDoc("K7Q2MC", ageMs = 25 * HOUR_MS))

        assertEquals(listOf("K7Q2MA"), listedCodes())
    }

    @Test
    fun theWindowMovesWithTheDeviceClock() = runTest {
        seed(publicDoc("K7Q2MA", ageMs = 1 * HOUR_MS))

        setNow(NOW + 23 * HOUR_MS)
        assertEquals(listOf("K7Q2MA"), listedCodes())
        setNow(NOW + 23 * HOUR_MS + 1)
        assertEquals(emptyList<String>(), listedCodes())
    }

    @Test
    fun aListingWithNoWindowReturnsTheNewestOfAnyAge() = runTest {
        seed(publicDoc("K7Q2MA", ageMs = 10 * 24 * HOUR_MS))
        seed(publicDoc("K7Q2MB", ageMs = 25 * HOUR_MS))
        seed(publicDoc("K7Q2MC", ageMs = 1 * HOUR_MS))

        assertEquals(listOf("K7Q2MC"), listedCodes())
        assertEquals(listOf("K7Q2MC", "K7Q2MB", "K7Q2MA"), listedCodes(CommunityFilter(windowMs = null)))
    }

    @Test
    fun listingForAGameReturnsOnlyThatGame() = runTest {
        seed(publicDoc("K7Q2MA", ageMs = 3 * HOUR_MS))
        seed(publicDoc("K7Q2MB", ageMs = 2 * HOUR_MS, gameId = "zip"))
        seed(publicDoc("K7Q2MC", ageMs = 1 * HOUR_MS))

        assertEquals(listOf("K7Q2MB"), listedCodes(CommunityFilter(gameId = "zip")))
        assertEquals(listOf("K7Q2MC", "K7Q2MA"), listedCodes(CommunityFilter(gameId = "queens")))
    }

    @Test
    fun listingForHardAndExpertReturnsBothLevelsAndNoOther() = runTest {
        seed(publicDoc("K7Q2MA", ageMs = 4 * HOUR_MS, difficulty = Difficulty.Level.Easy))
        seed(publicDoc("K7Q2MB", ageMs = 3 * HOUR_MS, difficulty = Difficulty.Level.Hard))
        seed(publicDoc("K7Q2MC", ageMs = 2 * HOUR_MS, difficulty = Difficulty.Level.Expert))
        seed(publicDoc("K7Q2MD", ageMs = 1 * HOUR_MS, difficulty = Difficulty.Level.Medium))

        assertEquals(listOf("K7Q2MC", "K7Q2MB"), listedCodes(CommunityFilter(difficulties = HARD_CHIP)))
    }

    @Test
    fun aDifficultyFilterLeavesOutAPuzzleWithNoDifficulty() = runTest {
        seed(publicDoc("K7Q2MA", ageMs = 2 * HOUR_MS, difficulty = Difficulty.Level.Easy))
        seed(publicDoc("K7Q2MB", ageMs = 1 * HOUR_MS))

        assertEquals(listOf("K7Q2MB", "K7Q2MA"), listedCodes())
        assertEquals(
            listOf("K7Q2MA"),
            listedCodes(CommunityFilter(difficulties = Difficulty.Level.entries.toSet())),
        )
    }

    @Test
    fun listingForAGameAndDifficultiesAppliesBoth() = runTest {
        seed(publicDoc("K7Q2MA", ageMs = 4 * HOUR_MS, difficulty = Difficulty.Level.Hard))
        seed(publicDoc("K7Q2MB", ageMs = 3 * HOUR_MS, gameId = "zip", difficulty = Difficulty.Level.Hard))
        seed(publicDoc("K7Q2MC", ageMs = 2 * HOUR_MS, difficulty = Difficulty.Level.Easy))
        seed(publicDoc("K7Q2MD", ageMs = 1 * HOUR_MS, difficulty = Difficulty.Level.Expert))

        assertEquals(
            listOf("K7Q2MD", "K7Q2MA"),
            listedCodes(CommunityFilter(gameId = "queens", difficulties = HARD_CHIP)),
        )
    }

    @Test
    fun listingWithNothingNewIsAnEmptyList() = runTest {
        seed(publicDoc("K7Q2MA", ageMs = 30 * HOUR_MS))

        assertEquals(CommunityListResult.Listed(emptyList()), repository.listPublic(CommunityFilter()))
    }

    @Test
    fun listingStopsAtTheThirtyNewest() = runTest {
        val codes = CODE_SUFFIXES.take(31).map { "K7Q2M$it" }
        codes.forEachIndexed { index, code -> seed(publicDoc(code, ageMs = (index + 1) * MINUTE_MS)) }

        assertEquals(codes.take(30), listedCodes())
    }

    @Test
    fun listingLeavesOutADocOfANewerFormat() = runTest {
        seed(publicDoc("K7Q2MA", ageMs = 2 * HOUR_MS))
        seed(publicDoc("K7Q2MB", ageMs = 1 * HOUR_MS).copy(formatVersion = SharedPuzzleDoc.FORMAT_VERSION + 1))

        assertEquals(listOf("K7Q2MA"), listedCodes())
    }

    @Test
    fun listingOfflineIsOffline() = runTest {
        seed(publicDoc("K7Q2MA"))
        goOffline()

        assertEquals(CommunityListResult.Offline, repository.listPublic(CommunityFilter()))
    }

    // ----- report -----

    @Test
    fun aSignedInPlayerReportsAnotherPlayersPuzzle() = runTest {
        seed(publicDoc(CODE))
        signInAs(OTHER)

        assertEquals(ReportResult.Reported, repository.report(code()))
    }

    @Test
    fun aLinkOnlyPuzzleCanBeReported() = runTest {
        seed(doc())
        signInAs(OTHER)

        assertEquals(ReportResult.Reported, repository.report(code()))
    }

    @Test
    fun aSecondReportOfTheSamePuzzleIsAlreadyReported() = runTest {
        seed(publicDoc(CODE))
        signInAs(OTHER)
        repository.report(code())

        assertEquals(ReportResult.AlreadyReported, repository.report(code()))
    }

    @Test
    fun twoPlayersEachReportTheSamePuzzle() = runTest {
        seed(publicDoc(CODE))
        signInAs(OTHER)
        repository.report(code())

        signInAs(THIRD)
        assertEquals(ReportResult.Reported, repository.report(code()))
    }

    @Test
    fun anAuthorsReportOfTheirOwnPuzzleIsAlreadyReported() = runTest {
        seed(publicDoc(CODE))
        signInAs(AUTHOR)

        assertEquals(ReportResult.AlreadyReported, repository.report(code()))
    }

    @Test
    fun aReportOfACodeWithNoPuzzleIsAlreadyReported() = runTest {
        signInAs(OTHER)

        assertEquals(ReportResult.AlreadyReported, repository.report(code()))
    }

    @Test
    fun aSignedOutReportIsSignedOutWithoutAskingTheServer() = runTest {
        seed(publicDoc(CODE))
        goOffline()

        assertEquals(ReportResult.SignedOut, repository.report(code()))
    }

    @Test
    fun aReportOfflineIsOffline() = runTest {
        seed(publicDoc(CODE))
        signInAs(OTHER)
        goOffline()

        assertEquals(ReportResult.Offline, repository.report(code()))
    }

    protected companion object {
        const val THIRD: String = "third-uid"

        /** The device clock at the start of every test. */
        const val NOW: Long = 1_700_100_000_000L
        const val MINUTE_MS: Long = 60_000L
        const val HOUR_MS: Long = 60 * MINUTE_MS

        /** The Hard chip (ticket 74): Hard and Expert together. */
        val HARD_CHIP: Set<Difficulty.Level> = setOf(Difficulty.Level.Hard, Difficulty.Level.Expert)

        /** One per code: `K7Q2M` plus a symbol is a valid share code. */
        const val CODE_SUFFIXES: String = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    }
}
