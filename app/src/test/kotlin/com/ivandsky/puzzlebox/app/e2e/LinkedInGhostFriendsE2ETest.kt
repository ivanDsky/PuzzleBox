package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.GHOSTS
import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.ZIP
import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.zipRefKey
import com.ivandsky.puzzlebox.app.leaderboard.LeaderboardSubmitWatcher
import com.ivandsky.puzzlebox.app.nav.ranksTopLevelDestinations
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.FinalBoardStore
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.data.visibility.VisibilityRepository
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.domain.ranks.isConnectionUid
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.testing.repository.FakeFinalBoardStore
import com.ivandsky.puzzlebox.testing.repository.FakeVisibilityRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins what the Ranks hub does with LinkedIn connections on the approved build (F21, criterion 12), over the real flag-on
 * graph from the screen down: a Friends board counts them on every window and on Overall, a Global board never does, their
 * rows open no player sheet, none of them leaves the phone, and the board's note names them wherever they count.
 *
 * One viewer, one friend and one stranger played Zip on the month's first four days; three connections played today.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class LinkedInGhostFriendsE2ETest {

    private val finalBoards = FakeFinalBoardStore()

    // The viewer has picked a visibility tier: the hub's submit watcher holds a result back until the tier is known, and an
    // unset one would put the opt-in sheet over the board.
    private val world = LinkedInDataWorld(also = listOf(ReleaseFlag.LeaderboardHub), ranksClock = NOW) {
        single<FinalBoardStore> { finalBoards }
        single<VisibilityRepository> { FakeVisibilityRepository(OwnTier.Chosen(VisibilityTier.Board)) }
    }
    private val today = E2E_TODAY
    private val days = (0L..3L).map { today.minusDays(it) }

    @Before
    fun seed() {
        world.signIn(ME)
        world.befriend(FAY)
        days.forEach { day ->
            // Fay is slower on the 1st, a Sunday: the month then reads unlike the week, so a board still on screen
            // from the window before cannot pass for the one asked for.
            val fay = if (day == days.last()) 70_000L else 50_000L
            world.graph.leaderboard.setEntries(
                LeaderboardRef(ZIP, day),
                listOf(entry(SOL, "Sol Stranger", day, 30_000), entry(FAY, "Fay Friend", day, fay), entry(ME.uid, ME.displayName, day, 90_000)),
            )
        }
        world.scriptDay(today, LinkedInOwnResult.NotPlayed, GHOSTS)
    }

    @After
    fun closeWorld() {
        world.close()
    }

    @Test
    fun friendsBoardsCountConnectionsOnEveryWindowAndOnOverall() {
        onZipPage {
            // The row that stands for the board already counts them: fourth of five, not second of two.
            awaitEquals(listOf("You #4 of 5")) { textsUnder("summary.rank") }
            openZipRanks()
            assertFriendsBoards()
        }
    }

    @Test
    fun globalBoardsShowNoConnection() {
        onZipPage {
            openZipRanks()
            tuneRanks("Global", "Day")
            assertBoard("1 Sol Stranger 0:30", "2 Fay Friend 0:50", "3 Me Myself 1:30")
            tuneRanks("Global", "Week")
            assertBoard("1 Sol Stranger avg 0:30", "2 Fay Friend avg 0:50", "3 Me Myself avg 1:30")
            tuneRanks("Global", "Month")
            assertBoard("1 Sol Stranger avg 0:30", "2 Fay Friend avg 0:55", "3 Me Myself avg 1:30")
            pickOverallRanks()
            assertBoard("1 Sol Stranger 400 pts", "2 Fay Friend 267 pts", "3 Me Myself 133 pts")
        }
    }

    @Test
    fun connectionRowOpensNoPlayerSheet() {
        onZipPage {
            openZipRanks()
            assertBoard(*FRIENDS_DAY)
            assertOnlyPlayerRowOpensSheet()
            tuneRanks("Friends", "Week")
            assertBoard(*FRIENDS_WEEK)
            assertOnlyPlayerRowOpensSheet()
        }
    }

    @Test
    fun noConnectionLeavesThePhone() {
        onZipPage {
            graph.koin.get<LeaderboardSubmitWatcher>().start()
            playInApp(today, 90_000)
            awaitLooper { graph.leaderboard.submitted.isNotEmpty() }
            openZipRanks()
            assertFriendsBoards()

            val kept = days.map { day -> runBlocking { finalBoards.get(LeaderboardRef(ZIP, day)) }?.entries.orEmpty().map { it.uid } }
            assertEquals(emptyList<String>(), (graph.leaderboard.submitted.map { it.uid } + kept.flatten()).filter(::isConnectionUid))
            // The control: the viewer's result did go out and the boards just counted were kept, so both had the chance.
            assertEquals(listOf(ME.uid), graph.leaderboard.submitted.map { it.uid })
            assertEquals(days.map { listOf(SOL, FAY, ME.uid) }, kept)
        }
    }

    @Test
    fun noteNamesConnectionsWhereverTheyCount() {
        onZipPage {
            openZipRanks()
            // Fay's own score tells the boards apart with or without connections, so the note is read off the board asked for.
            assertNote("0:50", NOTE)
            tuneRanks("Friends", "Week")
            assertNote("avg 0:50", NOTE)
            tuneRanks("Friends", "Month")
            assertNote("avg 0:55", NOTE)
            tuneRanks("Friends", "Day")
            pickOverallRanks()
            assertNote("80 pts", NOTE)
            tuneRanks("Global", "Day")
            assertNote("67 pts", "")
            tuneRanks("Global", "Week")
            assertNote("200 pts", "")
        }
    }

    private fun onZipPage(block: ShellRobot.() -> Unit) {
        shellRobot(graph = world.graph, destinations = ranksTopLevelDestinations) {
            runBlocking { world.connect() }
            openZipPage()
            awaitSynced(today)
            awaitLooper { shows("summary.rank") }
            block()
        }
    }

    /** Zip on Day, Week and Month, then Overall on Month and Day: the connections take places on each, and the viewer's is fourth. */
    private fun ShellRobot.assertFriendsBoards() {
        assertBoard(*FRIENDS_DAY)
        tuneRanks("Friends", "Week")
        assertBoard(*FRIENDS_WEEK)
        tuneRanks("Friends", "Month")
        assertBoard("1 Ghost Ada avg 0:41", "2 Fay Friend avg 0:55", "3 Ghost Bea avg 1:17", "4 Me Myself avg 1:30", "5 Ghost Cyd avg 1:43")
        pickOverallRanks()
        // Points add up over the month's four boards, and the connections are on one of them.
        assertBoard("1 Fay Friend 380 pts", "2 Me Myself 190 pts", "3 Ghost Ada 100 pts", "4 Ghost Bea 60 pts", "5 Ghost Cyd 20 pts")
        tuneRanks("Friends", "Day")
        assertBoard("1 Ghost Ada 100 pts", "2 Fay Friend 80 pts", "3 Ghost Bea 60 pts", "4 Me Myself 40 pts", "5 Ghost Cyd 20 pts")
    }

    private fun ShellRobot.assertBoard(vararg rows: String) = awaitEquals(rows.toList()) { standings() }

    /** The board as drawn, best place first: the place, name and score of every row, a connection's included. */
    private fun ShellRobot.standings(): List<String> =
        listOf("daily_board.row", "daily_board.ghost").flatMap { row ->
            val names = textsUnder("$row.name")
            val scores = textsUnder("$row.score")
            textsAt("$row.rank").mapIndexed { index, place -> (place.toIntOrNull() ?: 0) to "$place ${names.getOrNull(index)} ${scores.getOrNull(index)}" }
        }.sortedBy { it.first }.map { it.second }

    /** Waits for the board where Fay's score reads [fayScore], then reads its note. */
    private fun ShellRobot.assertNote(fayScore: String, note: String) {
        awaitEquals(true) { fayScore in textsUnder("daily_board.row.score") }
        awaitEquals(note) { textsUnder("ranks.board.note").joinToString() }
    }

    /** Fay's row is the control: once it opens her sheet the board takes taps, so a connection's tap that opens nothing is the row's doing. */
    private fun ShellRobot.assertOnlyPlayerRowOpensSheet() {
        awaitLooper { rule.onAllNodes(hasTestTag("daily_board.row") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        tapRow("Fay Friend")
        awaitEquals(listOf("Fay Friend")) { textsUnder("ranks.player.name") }
        closeSheet()
        awaitEquals(emptyList<String>()) { textsUnder("ranks.player.name") }
        tapRow("Ghost Ada")
        repeat(SETTLE_STEPS) { awaitLooper { true } }
        assertEquals(emptyList<String>(), textsUnder("ranks.player.name"))
    }

    private fun ShellRobot.tapRow(name: String) {
        val row = (hasTestTag("daily_board.row") or hasTestTag("daily_board.ghost")) and hasAnyDescendant(hasText(name))
        rule.onNode(row, useUnmergedTree = true).performClick()
        rule.waitForIdle()
    }

    private fun entry(uid: String, name: String, day: LocalDate, timeMs: Long) = LeaderboardEntry(uid, name, zipRefKey(day), timeMs, 0, timeMs)

    private companion object {
        val ME = Identity("uid-me", "Me Myself", photoUrl = null)
        const val FAY = "uid-fay"
        const val SOL = "uid-sol"
        const val NOTE = "with LinkedIn connections"

        /**
         * The device clock of every case: noon UTC a week after the scripted today, a Wednesday mid-month. The hub takes its
         * days from the scripted calendar and asks this clock only which boards are too old to change, so on any run date the
         * four played days are closed, fetched once and kept.
         */
        val NOW: Clock = Clock.fixed(E2E_TODAY.plusDays(7).atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

        /** Looper steps a tap gets to open a sheet before the test says it opened none. */
        const val SETTLE_STEPS = 20
        val FRIENDS_DAY = arrayOf("1 Ghost Ada 0:41", "2 Fay Friend 0:50", "3 Ghost Bea 1:17", "4 Me Myself 1:30", "5 Ghost Cyd 1:43")
        val FRIENDS_WEEK =
            arrayOf("1 Ghost Ada avg 0:41", "2 Fay Friend avg 0:50", "3 Ghost Bea avg 1:17", "4 Me Myself avg 1:30", "5 Ghost Cyd avg 1:43")
    }
}
