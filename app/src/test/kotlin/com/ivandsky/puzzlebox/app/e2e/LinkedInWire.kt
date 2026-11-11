package com.ivandsky.puzzlebox.app.e2e

import android.os.Looper
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.linkedin.LinkedInLink
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSession
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.feed.LinkedInZipDailySource
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.network.linkedin.linkedInAccountSource
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInSessionCookies
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPhotoSource
import com.ivandsky.puzzlebox.network.linkedin.linkedInPhotoSource
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.koin.core.module.Module
import org.koin.dsl.module
import org.robolectric.Shadows.shadowOf

/** An obvious fake standing in for the player's `li_at`: any surface that shows it has leaked the session. */
internal const val SENTINEL = "SENTINEL-li_at-7f3a9c"

/** What a logged-in web view leaves in the session store, carrying [SENTINEL]. */
internal val sentinelSession = LinkedInAccountSession(
    cookieHeader = "li_at=$SENTINEL; JSESSIONID=\"ajax:0123456789\"",
    csrfToken = "ajax:0123456789",
    userAgent = "PuzzleBoxTest/1.0",
)

/** The made-up member the account fixtures belong to. */
internal const val LINKEDIN_MEMBER = "ACoAAFAKE0001"

/** The game whose page the account E2Es open. */
internal val LINKEDIN_GAME: GameId = ZipDefinition.id

/** A day inside the connections-board window, with a disc on the month the game page opens on. */
internal val LINKEDIN_PAST_DAY: LocalDate = E2E_TODAY.minusDays(1)

/**
 * LinkedIn's account endpoint as a local server: it answers each of the three operations the app may call
 * with a sanitized fixture and keeps every request it was sent, so a test can count them and read their headers.
 */
internal class LinkedInWire : Dispatcher() {

    private val seen = CopyOnWriteArrayList<RecordedRequest>()

    @Volatile
    private var rejection: Int? = null

    private val server = MockWebServer().also {
        it.dispatcher = this
        it.start()
    }

    /** Every request received, oldest first. */
    val requests: List<RecordedRequest> get() = seen.toList()

    val requestCount: Int get() = seen.size

    private val photos = CopyOnWriteArrayList<Request>()

    /** Every photo request the app made, oldest first: they never reach the server, which holds only account calls. */
    val photoRequests: List<Request> get() = photos.toList()

    /**
     * The app's real photo client over a scripted network: `media.licdn.com` is https, which a local server
     * cannot stand in for, so every request it would send is kept and answered with a small JPEG.
     */
    fun photoSource(): LinkedInPhotoSource = linkedInPhotoSource(
        Interceptor { chain ->
            val request = chain.request()
            photos += request
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(HTTP_OK)
                .message("OK")
                .body(PHOTO_BYTES.toResponseBody("image/jpeg".toMediaType()))
                .build()
        },
    )

    /** The base URL the account adapter is built over. */
    val url: String = server.url("/").toString()

    /** From now on every request is refused with [status] and no body. */
    fun reject(status: Int) {
        rejection = status
    }

    /** Stops the server. Not `shutdown`: that name is the hook the server itself calls on its dispatcher. */
    fun close() = server.shutdown()

    override fun dispatch(request: RecordedRequest): MockResponse {
        seen += request
        rejection?.let { return MockResponse().setResponseCode(it) }
        val asked = request.requestUrl
        val queryId = asked?.queryParameter("queryId").orEmpty()
        val body = when {
            queryId.startsWith(END_PAGE) -> ownResult(asked)
            queryId.startsWith(CONNECTIONS) -> fixture("board.json")
            queryId.startsWith(TODAYS_GAME) -> todaysGame(asked)
            else -> return MockResponse().setResponseCode(HTTP_NOT_FOUND)
        }
        // The adapter takes a 200 without a JSON content type for a login wall.
        return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    }

    /**
     * The end page is looked up by the exact URN that was asked for, and the fixture holds one fixed edition:
     * its solved record is renamed to the asked one, so any day reads as solved.
     */
    private fun ownResult(asked: HttpUrl?): String {
        val variables = asked?.queryParameter("variables").orEmpty()
        val urn = checkNotNull(GAME_URN.find(variables)) { "no game URN in $variables" }.value
        return fixture("end-page.json").replace(FIXTURE_SOLVED_URN, urn)
    }

    private fun todaysGame(asked: HttpUrl?): String {
        val type = GAME_TYPE.find(asked?.queryParameter("variables").orEmpty())?.groupValues?.get(1)
        return """{"data":{"data":{"identityDashGamesByTodaysGame":{"*elements":["urn:li:fsd_game:($LINKEDIN_MEMBER,$type,1)"]}}}}"""
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource("fixtures/linkedin-account/$name")) { "no account fixture $name" }.readText()

    private companion object {
        // The adapter's query ids are internal to it; only their operation names are stable enough to match on.
        const val TODAYS_GAME = "voyagerIdentityDashGames."
        const val END_PAGE = "voyagerIdentityDashGameEndPage."
        const val CONNECTIONS = "voyagerIdentityDashGameConnectionsEntities."
        const val FIXTURE_SOLVED_URN = "urn:li:fsd_game:($LINKEDIN_MEMBER,6,538)"
        const val HTTP_NOT_FOUND = 404
        const val HTTP_OK = 200
        val PHOTO_BYTES = byteArrayOf(0x4A, 0x50, 0x47)
        val GAME_URN = Regex("""urn:li:fsd_game:\([^)]*\)""")
        val GAME_TYPE = Regex("""gameTypeId:(\d+)""")
    }
}

/**
 * The app's own graph with [also]'s flags on, the session store holding [sentinelSession] and the real account
 * adapter aimed at [wire]; [more] adds bindings on top.
 */
internal fun linkedInGraph(wire: LinkedInWire, vararg also: ReleaseFlag, more: Module.() -> Unit = {}): E2eKoinGraph =
    shellGraph(
        *also,
        overrides = module {
            single<LinkedInSessionCookies> { FakeLinkedInSessionCookies(sentinelSession) }
            single<LinkedInAccountSource> { linkedInAccountSource(wire.url, get()) }
            single<LinkedInPhotoSource> { wire.photoSource() }
            more()
        },
    ).also { graph ->
        // The fake daily repository knows no LinkedIn numbering, and a day without a number is never asked for.
        // Scripted in every case, so a count of zero is the gate under test and not a missing number.
        val edition = LinkedInZipDailySource.EDITION
        graph.daily.setLinkedInGameType(LINKEDIN_GAME, edition.gameTypeId)
        graph.daily.setPuzzleNumbers(LINKEDIN_GAME, edition::numberOrNull)
        // Without a range the game page draws no calendar, so there would be no past day to pick.
        graph.daily.setArchiveRange(LINKEDIN_GAME, LINKEDIN_PAST_DAY.minusDays(1)..E2E_TODAY)
    }

/** Links the account as a finished login would: through the real link, so its file on disk is real. */
internal fun E2eKoinGraph.connectLinkedIn() = runBlocking {
    koin.get<LinkedInLink>().connect(LINKEDIN_MEMBER, consentAtMs = 1L)
}

/** The game's page with today's day card on it. The robot's `openGame` would go on to Play, which is not a day view. */
internal fun ShellRobot.openLinkedInGamePage() {
    tapTab(PlayKey)
    awaitTag("route.library")
    tap("library.row.${LINKEDIN_GAME.value}")
    awaitTag("library.page.${LINKEDIN_GAME.value}")
    awaitTag("library.day_card")
}

/** Picks [LINKEDIN_PAST_DAY]'s disc on the open game page; the day card then shows that day. */
internal fun ShellRobot.pickLinkedInPastDay() {
    tap("library.calendar.day.$LINKEDIN_PAST_DAY")
    awaitTag("library.day_card.day.$LINKEDIN_PAST_DAY")
}

/**
 * Waits for [condition] while turning the main looper, where the day sync's debounce and every hop back from
 * the database and the network run. The robot's own wait allows too little for a real HTTP round trip on a busy machine.
 */
internal fun ShellRobot.awaitLinkedIn(condition: () -> Boolean) {
    rule.waitUntil(LINKEDIN_TIMEOUT_MS) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(LOOPER_STEP_MS))
        condition()
    }
}

/**
 * Lets everything a screen started run its course: the looper is turned well past the debounce, in steps, so
 * work that hops to another thread between two steps comes back and goes on.
 */
internal fun ShellRobot.letItSettle() {
    var steps = 0
    awaitLinkedIn { ++steps > SETTLE_STEPS }
}

private const val LINKEDIN_TIMEOUT_MS = 30_000L
private const val LOOPER_STEP_MS = 100L

// Six seconds of app time, twelve debounces. A day view's two calls land within some thirteen steps on a
// heavily loaded machine, so a call that should not happen has several times that to show up.
private const val SETTLE_STEPS = 60
