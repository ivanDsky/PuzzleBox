package com.ivandsky.puzzlebox.app.e2e

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.ivandsky.puzzlebox.app.MainActivity
import com.ivandsky.puzzlebox.model.ShareCode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A share link reaching [MainActivity] while friend sharing is off (ADR-0022, the release
 * default): the app starts on Home as if launched plainly, with no friend card and no code sheet.
 * [FriendLinkE2ETest] is the same link with the flag on.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class FriendLinkDarkE2ETest {

    private lateinit var rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

    private val graph = E2eKoinGraph(E2E_TODAY)
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun startGraph() = graph.start()

    @After
    fun stopGraph() = graph.stop()

    @Test
    fun `a link with friend sharing off leaves the app on Home`() = launching(view("${ShareCode.LINK_PREFIX}K7Q2MX")) {
        assertRoute("route.home")
        assertEquals(0, count("friend.card.title"))
    }

    @Test
    fun `a link whose code does not parse opens no code sheet with friend sharing off`() =
        launching(view("${ShareCode.LINK_PREFIX}K7Q2M")) {
            assertRoute("route.home")
            assertEquals(0, count("friend.code.sheet"))
        }

    /** Runs [block] over [MainActivity] started by [intent], torn down by the compose rule. */
    private fun launching(intent: Intent, block: () -> Unit) {
        rule = AndroidComposeTestRule(ActivityScenarioRule(intent), activityProvider = ::activityOf)
        val run = object : Statement() {
            override fun evaluate() = block()
        }
        rule.apply(run, Description.createTestDescription(FriendLinkDarkE2ETest::class.java, "launching")).evaluate()
    }

    private fun activityOf(scenario: ActivityScenarioRule<MainActivity>): MainActivity {
        var activity: MainActivity? = null
        scenario.scenario.onActivity { activity = it }
        return checkNotNull(activity) { "MainActivity is not running" }
    }

    private fun view(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java)

    private fun count(tag: String): Int = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    /** [tag] is the only `route.*` on screen once the transition settles. */
    private fun assertRoute(tag: String) {
        rule.waitUntil(TIMEOUT_MS) { routes() == listOf(tag) }
        rule.waitForIdle()
        assertEquals(listOf(tag), routes())
    }

    private fun routes(): List<String> = rule.onAllNodes(isRoute, useUnmergedTree = true).fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.TestTag] }

    private companion object {
        val isRoute = SemanticsMatcher("test tag route.*") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("route.") == true
        }
        const val TIMEOUT_MS = 10_000L
    }
}
