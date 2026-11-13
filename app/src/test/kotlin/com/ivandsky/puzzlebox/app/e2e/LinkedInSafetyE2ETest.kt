package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * When the app may ask LinkedIn anything, over the app's own graph with the real account adapter aimed at a
 * local server: never with the feature off, never before the player has consented, and with a consent only
 * for a day the player is looking at. The session store holds a session in every case, so nothing but the
 * gate under test stands between the shell and a request.
 */
@RunWith(RobolectricTestRunner::class)
// A phone-sized window: the Games tab is the list alone, so a game's page opens only when its row is tapped.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class LinkedInSafetyE2ETest {

    private val wire = LinkedInWire()

    @After
    fun closeTheWire() = wire.close()

    @Test
    fun withTheFlagOffNothingIsRead() {
        val graph = linkedInGraph(wire)
        shellRobot(graph = graph) {
            // Linked and signed in: the strongest setup that must still do nothing.
            graph.connectLinkedIn()
            walkEveryScreen()
            assertEquals("requests with the feature off", 0, wire.requestCount)
        }
    }

    @Test
    fun withNoConsentNothingIsRead() = shellRobot(graph = linkedInGraph(wire, ReleaseFlag.LinkedInAccount)) {
        walkEveryScreen()
        assertEquals("requests before the player has consented", 0, wire.requestCount)
    }

    @Test
    fun withConsentOnlyADayViewReads() {
        val graph = linkedInGraph(wire, ReleaseFlag.LinkedInAccount)
        shellRobot(graph = graph) {
            graph.connectLinkedIn()
            assertRoute(HOME)
            letItSettle()
            tapTab(HistoryRootKey)
            assertRoute(HISTORY)
            letItSettle()
            tapTab(HomeKey)
            assertRoute(HOME)
            letItSettle()
            assertEquals("requests before any day is on screen", 0, wire.requestCount)

            openLinkedInGamePage()
            awaitLinkedIn { wire.requestCount >= DAY_VIEW_CALLS }
            // The card and the page's board both show today: a second pair of calls would arrive in this time.
            letItSettle()
            assertEquals(
                "today's day view asks for the player's result once and the connections' board once",
                listOf(BOARD, OWN_RESULT),
                wire.requests.map { it.requestUrl?.queryParameter("queryId").orEmpty().substringBefore('.') }.sorted(),
            )
            wire.requests.forEach { request ->
                assertEquals("the method of ${request.path}", "GET", request.method)
                assertEquals("the path of ${request.path}", "/voyager/api/graphql", request.requestUrl?.encodedPath)
            }
            assertTrue("the calls carry the session", wire.requests.all { SENTINEL in it.getHeader("Cookie").orEmpty() })
        }
    }

    /** Home, History, the game's page with today's card, and a past day picked on its calendar. */
    private fun ShellRobot.walkEveryScreen() {
        assertRoute(HOME)
        letItSettle()
        tapTab(HistoryRootKey)
        assertRoute(HISTORY)
        letItSettle()
        openLinkedInGamePage()
        letItSettle()
        pickLinkedInPastDay()
        letItSettle()
    }

    private companion object {
        const val HOME = "route.home"
        const val HISTORY = "route.history"
        const val DAY_VIEW_CALLS = 2
        const val OWN_RESULT = "voyagerIdentityDashGameEndPage"
        const val BOARD = "voyagerIdentityDashGameConnectionsEntities"
    }
}
