package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPhotoSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import com.ivandsky.puzzlebox.games.zip.feed.LinkedInZipDailySource
import com.ivandsky.puzzlebox.network.linkedin.linkedInAccountSource
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInSessionCookies
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Criterion 10, a restored board day on a phone that never linked LinkedIn, over the real flag-on graph in
 * [CloudSyncWorld]. A saved a board for a day 30 days old; B, which never connected an account, signs in and restores
 * it. Opened from the day card the day shows the saved board as Final with the fixed "as of" time, and after another
 * day is opened the first day's board is still saved and still shown. LinkedIn is never called.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class CloudSyncRestoredBoardDayE2ETest {

    private val wire = LinkedInWire()
    private val world = CloudSyncWorld(linkedIn = true) {
        single<LinkedInSessionCookies> { FakeLinkedInSessionCookies(sentinelSession) }
        single<LinkedInAccountSource> { linkedInAccountSource(wire.url, get()) }
        single<LinkedInPhotoSource> { wire.photoSource() }
    }
    private val ada = Identity(uid = "uid-ada", displayName = "Ada", photoUrl = null)
    private val phoneA = world.device("phone-a")
    private val phoneB = world.device("phone-b")
    private val day30 = E2E_TODAY.minusDays(30)
    private val otherDay = day30.plusDays(1)

    @After
    fun closeWorld() {
        world.close()
        wire.close()
    }

    @Test
    fun `a board day restored onto a never-linked phone shows its saved board as final and survives another day`() {
        listOf(phoneA, phoneB).forEach { phone ->
            val edition = LinkedInZipDailySource.EDITION
            phone.graph.daily.setLinkedInGameType(LINKEDIN_GAME, edition.gameTypeId)
            phone.graph.daily.setPuzzleNumbers(LINKEDIN_GAME, edition::numberOrNull)
            phone.graph.daily.setArchiveRange(LINKEDIN_GAME, day30.minusDays(5)..E2E_TODAY)
        }
        phoneA.shell {
            phoneA.saveBoard(day30)
            phoneA.signIn(ada)
            awaitCloud("A's board in the cloud") { world.space.docs(ada.uid, "boards").size == 1 && phoneA.uploaded(ada.uid) && phoneA.synced() }
        }

        phoneB.shell {
            phoneB.signIn(ada)
            awaitCloud("B's restore") { phoneB.synced() && day30 in phoneB.shownBoardDays(day30) }

            openLinkedInGamePage()
            tap("library.calendar.prev")
            awaitTag("library.calendar.month.${day30.year}-${"%02d".format(day30.monthValue)}")
            pickDay(day30)
            val asOf = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US).format(Instant.ofEpochMilli(READ_AT_MS).atZone(ZoneId.systemDefault()))
            awaitLinkedIn { textsUnder("linkedin.saved_board.text") == listOf("Saved board · as of $asOf") }
            assertEquals("the board is final", listOf("Final"), textsUnder("linkedin.saved_board.mark"))

            pickDay(otherDay)
            letItSettle()
            assertEquals("the other day has no saved board", emptyList<String>(), textsUnder("linkedin.saved_board.mark"))
            assertTrue("the first day's board survives the other day", day30 in phoneB.shownBoardDays(day30))

            pickDay(day30)
            awaitLinkedIn { textsUnder("linkedin.saved_board.text") == listOf("Saved board · as of $asOf") }
            assertEquals("the first day is still final", listOf("Final"), textsUnder("linkedin.saved_board.mark"))

            letItSettle()
            assertEquals("no LinkedIn call was made", 0, wire.requestCount)
            assertEquals("no LinkedIn photo was asked for", emptyList<Any>(), wire.photoRequests)
        }
    }

    private companion object {
        /** The read time `Device.saveBoard` stamps. */
        const val READ_AT_MS = 1_700_000_000_000L
    }
}
