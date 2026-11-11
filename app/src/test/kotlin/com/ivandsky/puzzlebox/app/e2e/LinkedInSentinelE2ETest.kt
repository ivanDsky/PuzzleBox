package com.ivandsky.puzzlebox.app.e2e

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDays
import com.ivandsky.puzzlebox.data.linkedin.LinkedInLink
import com.ivandsky.puzzlebox.data.linkedin.LinkedInSync
import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.games.queens.feed.LinkedInQueensDailySource
import com.ivandsky.puzzlebox.games.sudoku.feed.LinkedInMiniSudokuDailySource
import com.ivandsky.puzzlebox.games.tango.feed.LinkedInTangoDailySource
import com.ivandsky.puzzlebox.games.zip.feed.LinkedInZipDailySource
import com.ivandsky.puzzlebox.network.linkedInFeedTransport
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Where the player's LinkedIn session may show up: in the `Cookie` header of an account request and nowhere
 * else. One run over the app's own graph, with the real account adapter, link, database and anonymous client,
 * reads a day, is then refused, and every surface the app writes to is searched for the session's [SENTINEL].
 */
@RunWith(RobolectricTestRunner::class)
// A phone-sized window: the Games tab is the list alone, so the day view under test opens when the test opens it.
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class LinkedInSentinelE2ETest {

    private val wire = LinkedInWire()

    /** Stands in for LinkedIn's public site, where the anonymous puzzle client goes. */
    private val anon = MockWebServer()
    private val anonRequests = CopyOnWriteArrayList<RecordedRequest>()

    private val systemOut = System.out
    private val systemErr = System.err
    private val printed = ByteArrayOutputStream()

    @Before
    fun start() {
        System.setOut(PrintStream(printed, true))
        System.setErr(PrintStream(printed, true))
        anon.dispatcher = object : Dispatcher() {
            // A page with no session to hand out: any cookie on a later request was brought from elsewhere.
            override fun dispatch(request: RecordedRequest): MockResponse {
                anonRequests += request
                return MockResponse().setBody("<html></html>")
            }
        }
        anon.start()
    }

    @After
    fun stop() {
        System.setOut(systemOut)
        System.setErr(systemErr)
        wire.close()
        anon.shutdown()
    }

    @Test
    fun theSessionNeverLeavesTheAccountRequest() {
        val graph = linkedInGraph(wire, ReleaseFlag.LinkedInAccount) {
            single<FeedTransport> { linkedInFeedTransport(anon.url("/").toString(), get<Json>()) }
        }
        shellRobot(graph = graph) {
            graph.connectLinkedIn()
            val said = mutableListOf<Any?>(sentinelSession)
            val shown = mutableListOf<String>()

            said.addAll(readADay())
            shown.addAll(shownEverywhere())
            assertTrue("the day's board is on screen", "#linkedin.rank" in shown)

            said.addAll(beRefused())
            awaitLinkedIn { SESSION_ENDED in shownEverywhere() }
            shown.addAll(shownEverywhere())

            fetchAnonymously()

            assertSentinelFree("what the app says about its states, results and errors", said.joinToString("\n"))
            assertSentinelFree("the screen", shown.joinToString("\n"))
            assertSentinelFree("the log", logged())
            assertSentinelFree("the app's files", stored())
            assertAnonymousClientIsClean()
            // Without this every scan above would also pass for a session that was never used.
            assertTrue(
                "the account requests carried the session",
                wire.requests.isNotEmpty() && wire.requests.all { SENTINEL in it.getHeader("Cookie").orEmpty() },
            )
        }
    }

    /** Today's day view until its result and board are stored, then the same reads asked for directly. */
    private fun ShellRobot.readADay(): List<Any?> {
        val days = graph.koin.get<LinkedInDays>()
        val source = graph.koin.get<LinkedInAccountSource>()
        openLinkedInGamePage()
        val day = offMain { days.observe(LINKEDIN_GAME, E2E_TODAY).first { !it.inFlight && it.boardFetchedAtMs != null } }
        awaitTag("linkedin.rank")
        val sync = offMain { days.sync(LINKEDIN_GAME, E2E_TODAY) }
        val reads = offMain {
            listOf(
                source.check(ZIP.gameTypeId),
                source.ownResult(LINKEDIN_MEMBER, ZIP.gameTypeId, TODAY_NUMBER),
                source.board(LINKEDIN_MEMBER, ZIP.gameTypeId, TODAY_NUMBER, delta = 0),
            )
        }
        assertEquals("the day is read", LinkedInSync.Done, sync)
        reads.forEach { assertTrue("$it is an answer", it is LinkedInRead.Ok) }
        return reads + sync + day + offMain { graph.koin.get<LinkedInLink>().state.first() }
    }

    /** LinkedIn turns the session down while a past day is opened: the link pauses, and everything says so. */
    private fun ShellRobot.beRefused(): List<Any?> {
        val days = graph.koin.get<LinkedInDays>()
        val link = graph.koin.get<LinkedInLink>()
        wire.reject(HTTP_FORBIDDEN)
        pickLinkedInPastDay()
        val paused = offMain { link.state.first { it is LinkState.Paused } }
        val sync = offMain { days.sync(LINKEDIN_GAME, LINKEDIN_PAST_DAY) }
        val read = offMain {
            graph.koin.get<LinkedInAccountSource>().ownResult(LINKEDIN_MEMBER, ZIP.gameTypeId, ZIP.number(LINKEDIN_PAST_DAY))
        }
        val day = offMain { days.observe(LINKEDIN_GAME, LINKEDIN_PAST_DAY).first { !it.inFlight } }
        assertTrue("$sync is a pause", sync is LinkedInSync.Paused)
        assertTrue("$read is a refusal", read is LinkedInRead.Rejected)
        return listOf(paused, sync, read, day)
    }

    /** The anonymous puzzle client asks for one edition of each game while the session sits in the store. */
    private fun ShellRobot.fetchAnonymously() {
        val transport = graph.koin.get<FeedTransport>()
        // What it answers is beside the point: only what it sends is read.
        offMain { EDITIONS.forEach { transport.fetch(it.request(1)) } }
    }

    private fun assertAnonymousClientIsClean() {
        val sent = anonRequests.toList()
        assertTrue("the anonymous client asked for every game", sent.size >= EDITIONS.size)
        sent.forEach { request ->
            val text = "${request.requestUrl}\n${request.headers}\n${request.body.snapshot().utf8()}"
            assertSentinelFree("the anonymous request ${request.path}", text)
            assertEquals("the cookie on the anonymous request ${request.path}", null, request.getHeader("Cookie"))
        }
    }

    /** Everything written to the Android log and to the process's own output during the run. */
    private fun logged(): String {
        val log = ShadowLog.getLogs().joinToString("\n") { "${it.tag} ${it.msg} ${it.throwable?.stackTraceToString().orEmpty()}" }
        // The anonymous client logs each request line, so a silent log means the scan saw nothing.
        assertTrue("the log has the anonymous client's requests", anon.url("/").host in log)
        return log + printed.toString(Charsets.UTF_8.name())
    }

    /** The bytes of every file in the app's files directory and of the account database with its journal. */
    private fun stored(): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val link = File(context.filesDir, "linkedin_link.preferences_pb")
        val database = context.getDatabasePath("linkedin.db")
        assertTrue("$link is written", link.length() > 0)
        assertTrue("$database is written", database.length() > 0)
        val journals = listOf("-wal", "-shm", "-journal").map { File(database.path + it) }.filter(File::exists)
        val files = context.filesDir.walkTopDown().filter(File::isFile).toList() + database + journals
        // One char per byte, so a text search sees whatever encoding the file holds its strings in.
        val bytes = files.joinToString("\n") { it.readBytes().toString(Charsets.ISO_8859_1) }
        assertTrue("the link's member is on disk", LINKEDIN_MEMBER in bytes)
        assertTrue("the board's connections are on disk", CONNECTION in bytes)
        return bytes
    }

    private fun assertSentinelFree(surface: String, text: String) =
        assertFalse("$surface holds the session", SENTINEL in text)

    /** Every tag, text, description and state in every window, unmerged: all a screen reader or a test can see. */
    private fun ShellRobot.shownEverywhere(): List<String> {
        rule.waitForIdle()
        return rule.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().flatMap { it.shown() }
    }

    private fun SemanticsNode.shown(): List<String> = buildList {
        config.getOrNull(SemanticsProperties.TestTag)?.let { add("#$it") }
        config.getOrNull(SemanticsProperties.Text)?.forEach { add(it.text) }
        config.getOrNull(SemanticsProperties.ContentDescription)?.let(::addAll)
        config.getOrNull(SemanticsProperties.StateDescription)?.let(::add)
        children.forEach { addAll(it.shown()) }
    }

    /**
     * Runs [block] on another thread while this one keeps turning the main looper. A sync asked for here joins
     * the one a screen has in flight, which resumes on the main looper: blocking this thread on it would hang.
     */
    private fun <T> ShellRobot.offMain(block: suspend () -> T): T {
        val done = CompletableFuture.supplyAsync { runBlocking { block() } }
        awaitLinkedIn { done.isDone }
        return done.get()
    }

    private companion object {
        const val HTTP_FORBIDDEN = 403
        const val SESSION_ENDED = "Your LinkedIn session ended"

        /** A connection in the board fixture. */
        const val CONNECTION = "Anna"
        val ZIP = LinkedInZipDailySource.EDITION
        val TODAY_NUMBER = ZIP.number(E2E_TODAY)
        val EDITIONS = listOf(
            ZIP,
            LinkedInQueensDailySource.EDITION,
            LinkedInTangoDailySource.EDITION,
            LinkedInMiniSudokuDailySource.EDITION,
        )
    }
}
