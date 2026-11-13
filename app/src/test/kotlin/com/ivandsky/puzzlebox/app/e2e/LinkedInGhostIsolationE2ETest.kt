package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.GHOSTS
import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.PROFILE_ID
import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.ZIP
import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.zipNumber
import com.ivandsky.puzzlebox.app.e2e.LinkedInDataWorld.Companion.zipRefKey
import com.ivandsky.puzzlebox.app.leaderboard.LeaderboardSubmitWatcher
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDays
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPhotoSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInAccountSource
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInPhotoSource
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInSessionCookies
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture
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
 * A connection's board is the player's own record (ADR-0029, A25 to A27), over the real flag-on graph in
 * [CloudSyncWorld] with LinkedIn on. Connections' results are the player's alone: they reach the player's own cloud
 * space as names, times and flags and nothing that names a connection's account (no member id, no photo), and no
 * backend of the app (the leaderboard, duels and the duel outbox) holds a word of them. A board is kept when the player
 * disconnects, here and in the cloud, and it is owner-scoped: another account on the phone sees none of it, is sent
 * none of it, and the account's board is back when the account is.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class LinkedInGhostIsolationE2ETest {

    private val source = FakeLinkedInAccountSource()
    private val photos = FakeLinkedInPhotoSource()
    private val world = CloudSyncWorld(linkedIn = true) {
        single<LinkedInAccountSource> { source }
        single<LinkedInPhotoSource> { photos }
        single<LinkedInSessionCookies> { FakeLinkedInSessionCookies(sentinelSession) }
    }
    private val today = E2E_TODAY
    private val older = E2E_TODAY.minusDays(3)
    private val me = Identity("uid-me", "Me Myself", photoUrl = null)
    private val other = Identity("uid-other", "Someone Else", photoUrl = null)
    private val names = GHOSTS.map { it.name }.toSet()

    @After
    fun closeWorld() {
        world.close()
    }

    @Test
    fun connectionsReachOnlyTheirPlayersSpaceAndNoBackend() {
        val phone = phoneOf(me)
        // An empty ranked board rather than an unavailable one: only a board that loaded lists ghosts.
        phone.graph.leaderboard.setEntries(LeaderboardRef(ZIP, today), emptyList())
        phone.graph.leaderboard.setEntries(LeaderboardRef(ZIP, older), emptyList())
        phone.shell {
            runBlocking { link().connect(PROFILE_ID, CONSENT_AT_MS) }

            // Shown first, so the checks below are about data that really arrived.
            openZipPage()
            awaitSynced(today)
            awaitLooper { textsUnder("daily_board.ghost.name").toSet() == names }
            pickDay(older)
            awaitSynced(older)
            awaitLooper { textsUnder("daily_board.ghost.name").toSet() == names }

            graph.koin.get<LeaderboardSubmitWatcher>().start()
            rule.waitUntil(LinkedInDataWorld.TIMEOUT_MS) { graph.leaderboard.submitted.isNotEmpty() }
            awaitLooper { world.space.docs(me.uid, "boards").size == 2 && phone.uploaded(me.uid) && phone.synced() }

            // The player's own space holds both boards: the connections' names, times, flags and member ids.
            val docs = world.space.docs(me.uid, "boards")
            assertEquals("a board doc per day read", 2, docs.size)
            listOf(today, older).forEach { day -> assertTrue("a board doc of $day: ${docs.map { it.id }}", docs.any { it.id.startsWith("${ZIP}_${day}_") }) }
            docs.forEach { board ->
                val rows = (board.fields["rows"] as List<*>).map { it as Map<*, *> }
                assertEquals("the board's connections, by name", names, rows.map { it["name"] }.toSet())
                assertEquals("their times", GHOSTS.map { it.timeSeconds!! * MS_PER_SECOND }.toSet(), rows.map { it["timeMs"] }.toSet())
                assertEquals("their member ids (user-check F1)", GHOSTS.map { it.memberRef }.toSet(), rows.map { it["ref"] }.toSet())
            }
            // ...and nothing that names a connection's account outside a board doc, which alone keeps their member ids.
            world.space.payloads.forEach { written ->
                assertEquals("only the player's space is written", me.uid, written.uid)
                assertNamesNoAccount("${written.kind}/${written.doc.id} ${written.doc.fields}", memberIdsAllowed = written.kind == "boards")
            }
            assertTrue("no other account has a space", world.space.isEmpty(other.uid))

            // The app's own backends hold the player's own result and no connection.
            val submitted = graph.leaderboard.submitted.toList()
            assertEquals("only the player's own result of today is posted", listOf(zipRefKey(today)), submitted.map { it.puzzleRef })
            assertEquals(me.uid, submitted.single().uid)
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

    @Test
    fun boardsAreKeptAcrossDisconnectAndStayWithTheirAccount() {
        val phone = phoneOf(me)
        phone.shell {
            runBlocking { link().connect(PROFILE_ID, CONSENT_AT_MS) }
            openZipPage()
            awaitSynced(today)
            awaitLooper { world.space.docs(me.uid, "boards").size == 1 && phone.uploaded(me.uid) && phone.synced() }
            assertEquals("the connections are shown to the player", names, ghostNamesShown(today))
            val inCloud = boardsOf(me.uid)
            val kept = storedGhosts(today)
            assertEquals(setOf(today), phone.shownBoardDays())

            // Disconnect keeps the board where it is: here, and in the cloud.
            offMain { link().disconnect() }
            assertEquals("the board is kept here", kept, storedGhosts(today))
            assertEquals("the board is shown after Disconnect", names, ghostNamesShown(today))
            assertEquals("the board is kept in the cloud", inCloud, boardsOf(me.uid))
            assertEquals("Disconnect deletes nothing in the cloud", 0, world.space.deletes)

            // Another account on the phone: none of the board is shown to it, none is sent for it.
            phone.signOut()
            val writes = world.space.writes
            val reads = world.space.reads
            phone.signIn(other)
            awaitCloud("the other account's first run") { phone.synced() && world.space.reads > reads && phone.uploaded(other.uid) }
            assertEquals("not one write for the other account", writes, world.space.writes)
            assertTrue("nothing of the board is in the other account's space", world.space.isEmpty(other.uid))
            assertTrue("no doc was put for the other account", world.space.payloads.none { it.uid == other.uid })
            assertEquals("no board day is shown to the other account", emptySet<LocalDate>(), phone.shownBoardDays())
            assertEquals("no connection is shown to the other account", emptySet<String>(), ghostNamesShown(today))
            assertEquals("the first account's cloud boards are as it left them", inCloud, boardsOf(me.uid))

            // The first account is back: its board is back, and nothing of it moved.
            phone.signOut()
            val back = world.space.reads
            phone.signIn(me)
            awaitCloud("the first account's run after coming back") { phone.synced() && world.space.reads > back }
            assertEquals("the board is shown again", setOf(today), phone.shownBoardDays())
            assertEquals("with its connections", names, ghostNamesShown(today))
            assertEquals("the first account's cloud boards are still as they were", inCloud, boardsOf(me.uid))
            assertEquals("still nothing deleted", 0, world.space.deletes)
        }
    }

    /** A phone signed in as [account], with every day a scenario views scripted, because an unscripted call reads as offline. */
    private fun phoneOf(account: Identity): CloudSyncWorld.Device {
        val phone = world.device()
        phone.graph.daily.setLinkedInGameType(ZipDefinition.id, ZIP_TYPE)
        phone.graph.daily.setPuzzleNumbers(ZipDefinition.id, ::zipNumber)
        phone.graph.daily.setArchiveRange(ZipDefinition.id, E2E_TODAY.minusDays(ARCHIVE_DAYS)..E2E_TODAY)
        GHOSTS.forEach { photos.respond(checkNotNull(it.pictureUrl), byteArrayOf(0x4A, 0x50, 0x47)) }
        listOf(today to 64, older to 91).forEach { (day, seconds) ->
            val delta = ChronoUnit.DAYS.between(day, E2E_TODAY).toInt()
            source.respondOwn(PROFILE_ID, ZIP_TYPE, zipNumber(day), LinkedInRead.Ok(LinkedInOwnResult.Solved(timeSeconds = seconds, hintFree = true)))
            source.respondBoard(PROFILE_ID, ZIP_TYPE, zipNumber(E2E_TODAY), delta, LinkedInRead.Ok(LinkedInBoard(GHOSTS)))
        }
        phone.signIn(account)
        return phone
    }

    /** The cloud boards of [uid] as (id, updatedAtMs), which compare. */
    private fun boardsOf(uid: String): List<Pair<String, Long?>> = world.space.docs(uid, "boards").map { it.id to it.updatedAtMs }

    /** The connections the day shows, wherever the link stands: the day view's own read of the saved board. */
    private fun ShellRobot.ghostNamesShown(day: LocalDate): Set<String> =
        offMain { graph.koin.get<LinkedInDays>().observe(ZipDefinition.id, day).first() }.ghosts.map { it.name }.toSet()

    private fun ShellRobot.storedGhosts(day: LocalDate): Set<Pair<String, String>> =
        runBlocking { linkedInDb().ghostDao().observe(ZIP, day.toString()).first() }.map { it.memberRef to it.name }.toSet()

    /** Runs [block] on another thread while this one keeps turning the main looper, which the day view's collectors need. */
    private fun <T> ShellRobot.offMain(block: suspend () -> T): T {
        val done = CompletableFuture.supplyAsync { runBlocking { block() } }
        awaitLooper { done.isDone }
        return done.get()
    }

    private fun assertNamesNoGhost(text: String) {
        GHOSTS.flatMap { listOf(it.name, it.memberRef) }.forEach { ghost ->
            assertTrue("\"$text\" names the connection $ghost", ghost !in text)
        }
    }

    /** A board names its connections and their member ids (F1), never a photo; no other doc names a connection's account. */
    private fun assertNamesNoAccount(text: String, memberIdsAllowed: Boolean) {
        val accounts = GHOSTS.flatMap { if (memberIdsAllowed) listOfNotNull(it.pictureUrl) else listOfNotNull(it.memberRef, it.pictureUrl, "GHOST-") }
        accounts.forEach { account ->
            assertTrue("\"$text\" names the connection's account $account", account !in text)
        }
        assertTrue("\"$text\" names the player's own profile id", PROFILE_ID !in text)
    }

    private companion object {
        const val ZIP_TYPE = 6
        const val ARCHIVE_DAYS = 10L
        const val CONSENT_AT_MS = 1_700_000_000_000L
        const val MS_PER_SECOND = 1000L
    }
}
