package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.zipNumber
import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.zipRef
import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.zipRefKey
import com.ivandsky.puzzlebox.app.leaderboard.LeaderboardSubmitWatcher
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The player's own LinkedIn result is an ordinary completion once imported: it counts for the
 * streak, it never displaces a play made in PuzzleBox, a second import of the same day adds
 * nothing, and it is posted to the leaderboard under the same day window as any other record.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class LinkedInImportE2ETest {

    private val world = LinkedInDataWorld()
    private val today = E2E_TODAY
    private val yesterday = E2E_TODAY.minusDays(1)
    private val older = E2E_TODAY.minusDays(3)
    private val me = Identity("uid-me", "Me Myself", photoUrl = null)

    @After
    fun closeWorld() {
        world.close()
    }

    @Test
    fun importingADayCountsForTheStreakAndTwiceChangesNothing() {
        world.scriptDay(today, LinkedInOwnResult.Solved(timeSeconds = 64, hintFree = true))
        shellRobot(graph = world.graph) {
            playInApp(yesterday, timeMs = 90_000)
            playInApp(today.minusDays(2), timeMs = 80_000)
            assertEquals("two days played in the app", 2, zipStats().currentStreak)
            runBlocking { world.connect() }

            openZipPage()
            awaitSynced(today)
            val afterFirst = zipStats()
            val rowsAfterFirst = storedRows(today)
            assertEquals("today's import extends the streak", 3, afterFirst.currentStreak)

            // Forgetting that today was read makes the next look at today import it again.
            val callsBefore = world.source.calls.size
            runBlocking { linkedInDb().fetchLogDao().clear() }
            reopenZipPage()
            awaitSynced(today)
            assertEquals("today was read again", true, world.source.calls.size > callsBefore)
            assertEquals("the second import changes no stat", afterFirst, zipStats())
            assertEquals("the second import adds no row", rowsAfterFirst, storedRows(today))
        }
    }

    @Test
    fun anImportOnAPlayedDayAddsAPlayAgainAndLeavesTheRecord() {
        world.scriptDay(today, LinkedInOwnResult.NotPlayed)
        world.scriptDay(yesterday, LinkedInOwnResult.Solved(timeSeconds = 47, hintFree = false))
        // Played from the archive: such a day counts for the streak only once an import covers it.
        world.graph.daily.addEntry(
            DailyEntry(ZipDefinition.id, yesterday, zipNumber(yesterday), "zip/n5", fetchedAtMs = 0L, archived = true),
        )
        shellRobot(graph = world.graph) {
            playInApp(yesterday, timeMs = 90_000)
            val record = recordOf(yesterday)
            assertEquals("an archive play alone is no streak day", 0, zipStats().currentStreak)
            runBlocking { world.connect() }

            openZipPage()
            awaitSynced(today)
            pickDay(yesterday)
            awaitSynced(yesterday)

            val playAgains = runBlocking { completions().observePlayAgains(zipRef(yesterday)).first() }
            assertEquals("LinkedIn's result is kept as one replay", listOf(CompletionSource.LinkedIn to 47_000L), playAgains.map { it.source to it.time })
            assertEquals("the play made in the app is still the record, untouched", record, recordOf(yesterday))
            assertEquals(90_000L, record.time)
            assertEquals("the imported day now counts", 1, zipStats().currentStreak)
        }
    }

    @Test
    fun importedRecordsForTodayAndYesterdayAreSubmittedAndOlderOnesAreNot() = importThreeDays(signedIn = true) {
        graph.koin.get<LeaderboardSubmitWatcher>().start()
        rule.waitUntil(LinkedInDataWorld.TIMEOUT_MS) { graph.leaderboard.submitted.size >= 2 }

        // LinkedIn's whole seconds as the time; a hint-free result is 0 hints, any other is 1.
        val posted = graph.leaderboard.submitted.map { Triple(it.puzzleRef, it.timeMs, it.hints) }.toSet()
        assertEquals(setOf(Triple(zipRefKey(today), 61_000L, 0), Triple(zipRefKey(yesterday), 95_000L, 1)), posted)
        assertEquals("one post per day, none for the day outside the window", 2, graph.leaderboard.submitted.size)
    }

    @Test
    fun signedOutNothingIsSubmittedAndTheImportsStayInHistory() = importThreeDays(signedIn = false) {
        graph.koin.get<LeaderboardSubmitWatcher>().start()

        val imports = runBlocking { completions().observeImports().first() }
        assertEquals(setOf(zipRef(today), zipRef(yesterday), zipRef(older)), imports.map { it.puzzleRef }.toSet())
        assertEquals("each import is a solved day of the history", 3, zipStats().solved)
        assertEquals("signed out, nothing is posted", emptyList<Any>(), graph.leaderboard.submitted.toList())

        // Signing in posts the window's days, so the watcher was running and held back only by the missing
        // identity; had it posted anything while signed out, that entry would be here as well.
        graph.identity.currentIdentity = me
        rule.waitUntil(LinkedInDataWorld.TIMEOUT_MS) { graph.leaderboard.submitted.size >= 2 }
        assertEquals(
            listOf("uid-me" to zipRefKey(yesterday), "uid-me" to zipRefKey(today)),
            graph.leaderboard.submitted.map { it.uid to it.puzzleRef }.sortedBy { it.second },
        )
    }

    /** Views today, yesterday and a day outside the upload window, each solved on LinkedIn, then runs [then]. */
    private fun importThreeDays(signedIn: Boolean, then: ShellRobot.() -> Unit) {
        world.scriptDay(today, LinkedInOwnResult.Solved(timeSeconds = 61, hintFree = true))
        world.scriptDay(yesterday, LinkedInOwnResult.Solved(timeSeconds = 95, hintFree = false))
        world.scriptDay(older, LinkedInOwnResult.Solved(timeSeconds = 120, hintFree = true))
        if (signedIn) world.graph.identity.currentIdentity = me
        shellRobot(graph = world.graph) {
            runBlocking { world.connect() }
            openZipPage()
            awaitSynced(today)
            listOf(yesterday, older).forEach { day ->
                pickDay(day)
                awaitSynced(day)
            }
            then()
        }
    }

    private fun ShellRobot.recordOf(day: LocalDate): CompletionRecord =
        runBlocking { completions().observeRecords().first() }.single { it.puzzleRef == zipRef(day) }

    /** How many rows the store holds in all, records and [day]'s play-agains together. */
    private fun ShellRobot.storedRows(day: LocalDate): Int = runBlocking {
        completions().observeRecords().first().size + completions().observePlayAgains(zipRef(day)).first().size
    }
}
