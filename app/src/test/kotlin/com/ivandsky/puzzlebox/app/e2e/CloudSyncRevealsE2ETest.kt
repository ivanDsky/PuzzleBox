package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.e2e.CloudSyncWorld.Device
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.LocalMatch
import com.ivandsky.puzzlebox.data.match.MatchLocalRepository
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #1542: a duel's reveal seen on one phone is seen on every phone of the account, with the scores of the first reveal.
 * Two [Device]s of [CloudSyncWorld], one account, nothing shared but the cloud space.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class)
class CloudSyncRevealsE2ETest {

    private val world = CloudSyncWorld()
    private val ada = Identity(uid = "uid-ada", displayName = "Ada", photoUrl = null)
    private val phone = world.device("phone")
    private val tablet = world.device("tablet")

    @After
    fun closeWorld() = world.close()

    @Test
    fun `a reveal seen on the phone is seen on the tablet with the phone's scores, which a later reveal there keeps`() {
        phone.shell {
            phone.signIn(ada)
            awaitCloud("the phone's start-up run") { phone.synced() }
            phone.seeReveal(SEEN_AT, myScore = 3, theirScore = 1)
            awaitCloud("the phone's reveal uploaded") { phone.uploaded(ada.uid) && phone.synced() }
        }
        tablet.shell {
            tablet.signIn(ada)
            awaitCloud("the tablet's first run") { tablet.synced() && tablet.uploaded(ada.uid) }
            assertEquals("the tablet treats the reveal as seen, with the phone's scores", seen(SEEN_AT, 3, 1), tablet.duel())

            // The tablet opens the duel after all: its reveal changes nothing, here or in the cloud.
            tablet.seeReveal(LATER, myScore = 2, theirScore = 2)
            awaitCloud("the tablet's run after the reveal") { tablet.uploaded(ada.uid) && tablet.synced() }
            assertEquals("the first reveal's scores stay", seen(SEEN_AT, 3, 1), tablet.duel())
            assertEquals("the cloud keeps the first reveal", listOf(SEEN_AT.toEpochMilli()), revealsInCloud())
        }
    }

    @Test
    fun `a reveal the tablet saw first keeps the tablet's scores when the phone's arrives`() {
        tablet.shell {
            tablet.seeReveal(SEEN_AT, myScore = 2, theirScore = 1)
        }
        phone.shell {
            phone.signIn(ada)
            phone.seeReveal(LATER, myScore = 3, theirScore = 0)
            awaitCloud("the phone's reveal uploaded") { phone.uploaded(ada.uid) && phone.synced() && revealsInCloud().isNotEmpty() }
        }
        tablet.shell {
            tablet.signIn(ada)
            awaitCloud("the tablet's first run") { tablet.synced() && tablet.uploaded(ada.uid) }
            assertEquals("the tablet's own first reveal is never replaced", seen(SEEN_AT, 2, 1), tablet.duel())
            assertEquals("the tablet never writes over the phone's reveal", listOf(LATER.toEpochMilli()), revealsInCloud())
        }
    }

    private fun Device.seeReveal(at: Instant, myScore: Int, theirScore: Int) = runBlocking {
        graph.koin.get<MatchLocalRepository>().markRevealSeen(CODE, at, myScore, theirScore)
    }

    /** The duel as this phone's Compete tab reads it: seen time and the scores the reveal showed. */
    private fun Device.duel(): Triple<Instant?, Int?, Int?>? = runBlocking {
        graph.koin.get<MatchLocalRepository>().observeAll().first().firstOrNull { it.code == CODE }?.seen()
    }

    private fun LocalMatch.seen() = Triple(revealSeenAt, myScore, theirScore)

    private fun seen(at: Instant, myScore: Int, theirScore: Int) = Triple(at, myScore, theirScore)

    private fun revealsInCloud(): List<Long?> = world.space.docs(ada.uid, "reveals").map { it.fields["seenAtMs"] as? Long }

    private companion object {
        val CODE: ShareCode = checkNotNull(ShareCode.parse("D7Q2MX"))
        val SEEN_AT: Instant = Instant.parse("2026-11-10T20:00:00Z")
        val LATER: Instant = SEEN_AT.plusSeconds(3_600)
    }
}
