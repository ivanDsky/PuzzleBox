package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.GHOSTS
import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.ZIP
import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.zipRefKey
import com.ivandsky.puzzlebox.app.leaderboard.LeaderboardSubmitWatcher
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Connections' results are for this phone only. A signed-in, connected player looks at two days
 * whose LinkedIn boards list three connections; the app's own backends (the leaderboard, duels and
 * the duel outbox) then hold the player's own result and nothing that names a connection.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class LinkedInGhostIsolationE2ETest {

    private val world = LinkedInDataWorld()
    private val today = E2E_TODAY
    private val older = E2E_TODAY.minusDays(3)

    @After
    fun closeWorld() {
        world.close()
    }

    @Test
    fun ghostsNeverReachABackend() {
        world.scriptDay(today, LinkedInOwnResult.Solved(timeSeconds = 64, hintFree = true), GHOSTS)
        world.scriptDay(older, LinkedInOwnResult.Solved(timeSeconds = 91, hintFree = true), GHOSTS)
        world.graph.identity.currentIdentity = Identity("uid-me", "Me Myself", photoUrl = null)
        // An empty ranked board rather than an unavailable one: only a board that loaded lists ghosts.
        world.graph.leaderboard.setEntries(LeaderboardRef(ZIP, today), emptyList())
        shellRobot(graph = world.graph) {
            runBlocking { world.connect() }
            val names = GHOSTS.map { it.name }.toSet()

            // Shown first, so the checks below are about data that really arrived.
            openZipPage()
            awaitSynced(today)
            awaitLooper { textsUnder("daily_board.ghost.name").toSet() == names }
            pickDay(older)
            awaitSynced(older)
            awaitLooper { textsUnder("linkedin.ghost").containsAll(names) }

            graph.koin.get<LeaderboardSubmitWatcher>().start()
            rule.waitUntil(LinkedInDataWorld.TIMEOUT_MS) { graph.leaderboard.submitted.isNotEmpty() }

            val submitted = graph.leaderboard.submitted.toList()
            assertEquals("only the player's own result of today is posted", listOf(zipRefKey(today)), submitted.map { it.puzzleRef })
            assertEquals("uid-me", submitted.single().uid)
            submitted.forEach { assertNamesNoGhost("${it.uid} ${it.displayName} ${it.puzzleRef}") }

            val boards = graph.leaderboard.observedBoards.toList()
            assertTrue("the page read today's daily board: $boards", LeaderboardRef(ZIP, today) in boards)
            assertTrue("the board followed the picked day: $boards", LeaderboardRef(ZIP, older) in boards)
            boards.forEach { board ->
                // The day card's day, or the day picked on the calendar: a daily board is only ever read, never anything of a connection's.
                assertTrue("a daily board of a day the page showed: $board", board.day in setOf(today, older))
                assertNamesNoGhost(board.toString())
            }
            assertTrue("no duel was made: ${graph.matches.matches}", graph.matches.matches.isEmpty())
            assertEquals("nothing queued for a duel", emptyList<Any>(), runBlocking { graph.outbox.observeAll().first() })

            // Fetched and kept: the rows are in the LinkedIn database for both days.
            listOf(today, older).forEach { day -> assertEquals(GHOSTS.map { it.memberRef to it.name }.toSet(), storedGhosts(day)) }
        }
    }

    private fun ShellRobot.storedGhosts(day: LocalDate): Set<Pair<String, String>> =
        runBlocking { linkedInDb().ghostDao().observe(ZIP, day.toString()).first() }.map { it.memberRef to it.name }.toSet()

    private fun assertNamesNoGhost(text: String) {
        GHOSTS.flatMap { listOf(it.name, it.memberRef) }.forEach { ghost ->
            assertTrue("\"$text\" names the connection $ghost", ghost !in text)
        }
    }
}
