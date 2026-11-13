package com.ivandsky.puzzlebox.app.e2e

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.AndroidComposeTestRule as v2AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.MainActivity
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.CreateResult
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A tapped duel link over the real activity while the app runs: with the Competitions flag on it opens the duel's
 * invite in Play; with it off the link changes nothing.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class DuelLinkE2ETest {

    private lateinit var rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>
    private lateinit var graph: E2eKoinGraph
    private val code = checkNotNull(ShareCode.parse(CODE))
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun stopGraph() = graph.stop()

    @Test
    fun aDuelLinkOpensTheInviteWhenTheFlagIsOn() = runningWith(FakeReleaseFlags(ReleaseFlag.Competitions)) {
        openLink()
        awaitTag("duel.invite.accept")
        rule.onNodeWithTag("nav.tab.play").assertIsSelected()
    }

    @Test
    fun aDuelLinkIsIgnoredWhenTheFlagIsOff() = runningWith(FakeReleaseFlags()) {
        openLink()
        assertEquals("the app stays where it was", listOf(HOME), routes())
        rule.onNodeWithTag("nav.tab.home").assertIsSelected()
        assertEquals("no invite opens", emptyList<String>(), tags().filter { it.startsWith("duel.") })
    }

    /**
     * Runs [block] over [MainActivity] relaunched from Recents on Home (so the start link is not re-applied), with
     * [flags] and the friend's open duel on the server.
     */
    private fun runningWith(flags: FakeReleaseFlags, block: () -> Unit) {
        graph = E2eKoinGraph(E2E_TODAY, releaseFlags = flags)
        graph.start()
        seedFriendsDuel()
        val intent = view(code.matchLink).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        rule = v2AndroidComposeTestRule(ActivityScenarioRule(intent), activityProvider = ::activityOf)
        val run = object : Statement() {
            override fun evaluate() {
                rule.waitUntil(TIMEOUT_MS) { routes() == listOf(HOME) }
                block()
            }
        }
        rule.apply(run, Description.createTestDescription(DuelLinkE2ETest::class.java, "runningWith")).evaluate()
    }

    /** The duel link arrives while the app runs. */
    private fun openLink() {
        rule.runOnUiThread { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(rule.activity, view(code.matchLink)) }
        rule.waitForIdle()
    }

    /** The friend's duel over today's Zip, open to anyone; I am signed in and not in it yet. */
    private fun seedFriendsDuel() = runBlocking {
        graph.matches.now = Instant.now()
        graph.matches.signedInUid = FRIEND
        val draft = MatchDraft(
            code = code,
            deadlineHours = Match.DEADLINE_CHOICES.first(),
            challenger = Player(FRIEND, FRIEND_NAME, photoUrl = null),
            invitedUid = null,
            legs = listOf(MatchLeg(LegRef.Daily(ZipDefinition.id, E2E_TODAY, PUZZLE_NUMBER), authorUid = null)),
            includeFriends = false,
            rematchOf = null,
        )
        check(graph.matches.create(draft) is CreateResult.Created) { "the duel was not created" }
        graph.identity.currentIdentity = Identity(uid = ME, displayName = ME_NAME, photoUrl = null)
        graph.matches.signedInUid = ME
    }

    private fun activityOf(scenario: ActivityScenarioRule<MainActivity>): MainActivity {
        var activity: MainActivity? = null
        scenario.scenario.onActivity { activity = it }
        return checkNotNull(activity) { "MainActivity is not running" }
    }

    private fun view(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java)

    private fun awaitTag(tag: String) {
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun routes(): List<String> = rule.onAllNodes(isRoute, useUnmergedTree = true).fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.TestTag] }

    private fun tags(): List<String> = rule.onAllNodes(hasAnyTag, useUnmergedTree = true).fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.TestTag] }

    private companion object {
        const val CODE = "K7Q2MX"
        const val ME = "me"
        const val ME_NAME = "Me"
        const val FRIEND = "sam"
        const val FRIEND_NAME = "Sam"
        const val PUZZLE_NUMBER = 592
        const val HOME = "route.home"
        const val TIMEOUT_MS = 10_000L
        val isRoute = SemanticsMatcher("test tag route.*") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("route.") == true
        }
        val hasAnyTag = SemanticsMatcher("any test tag") { it.config.getOrNull(SemanticsProperties.TestTag) != null }
    }
}
