package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.PuzzleBoxApplication
import com.ivandsky.puzzlebox.data.cloud.CloudSpace
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.cloud.CloudSyncRunner
import com.ivandsky.puzzlebox.data.cloud.SavedBoardsCloud
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSpace
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SC16: with CloudSync off the app is today's app. The graph is the production one (the cloud wiring runs exactly as
 * `PuzzleBoxApplication.onCreate` runs it) with a [FakeCloudSpace] on the cloud edge: sign in, finish a game, sign out,
 * and not one cloud call is made, nothing of the sync graph is bound, and Profile shows no sync line.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class)
class CloudSyncFlagOffE2ETest {

    private lateinit var graph: E2eKoinGraph
    private val space by lazy { FakeCloudSpace(graph.identity) }

    @Test
    fun `flag off, sign in, finish a game, sign out makes no cloud call and shows today's screens`() {
        graph = E2eKoinGraph(E2E_TODAY, overrides = module { single<CloudSpace> { space } }, releaseFlags = FakeReleaseFlags())
        shellRobot(tabAt(PlayKey), graph = graph) {
            assertFalse("flag off must not start the cloud wiring", PuzzleBoxApplication.loadCloudSync(graph.koin))

            graph.identity.currentIdentity = Identity(uid = "u1", displayName = "One", photoUrl = null)
            openGame("zip")
            solveTodaysZip()
            runBlocking { graph.identity.signOut() }
            rule.waitForIdle()

            assertEquals("cloud reads", 0, space.reads)
            assertEquals("cloud writes", 0, space.writes)
            assertEquals("cloud deletes", 0, space.deletes)
            assertNull(graph.koin.getOrNull<CloudSync>())
            assertNull(graph.koin.getOrNull<CloudSyncRunner>())
            assertNull(graph.koin.getOrNull<SavedBoardsCloud>())

            back() // the result is immersive: the tab bar is back at the hub
            tapTab(HomeKey)
            push(ProfileKey)
            awaitTag("route.profile")
            assertFalse("Profile shows no sync line", shows("profile.google.sync"))
        }
    }
}
