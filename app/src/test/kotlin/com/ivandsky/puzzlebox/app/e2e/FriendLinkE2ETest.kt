package com.ivandsky.puzzlebox.app.e2e

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.AndroidComposeTestRule as v2AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.MainActivity
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A tapped share link over the real activity: the manifest routes it to [MainActivity], a
 * cold start and a warm new intent both land on the friend's card in Play, recreating the
 * activity keeps that one card, and a link whose code doesn't parse opens the code sheet
 * holding its text. A friend's board is immersive like a daily one.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class FriendLinkE2ETest {

    private lateinit var rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

    private val shared = FakeSharedPuzzleRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val graph = E2eKoinGraph(
        E2E_TODAY,
        overrides = module {
            single<SharedPuzzleRepository> { shared }
            single<ReceivedPuzzleRepository> { received }
        },
    )
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun startGraph() {
        graph.start()
        seedCastle()
    }

    @After
    fun stopGraph() = graph.stop()

    @Test
    fun `the manifest routes share links, and only them, to the app`() {
        fun handlers(uri: String) = context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addCategory(Intent.CATEGORY_BROWSABLE), 0)
            .map { it.activityInfo.name }

        assertEquals(listOf(MainActivity::class.java.name), handlers(LINK))
        assertEquals(emptyList<String>(), handlers("https://puzzlebox-f3356.web.app/q/K7Q2MX"))
    }

    @Test
    fun `a cold start from a link opens the card in Play and its board is immersive`() = launching(view(LINK)) {
        awaitTag("friend.card.title")
        assertRoute("route.friend")
        rule.onNodeWithTag("nav.tab.play").assertIsSelected()

        tap("friend.card.play")
        assertRoute("route.game")
        assertNoNavigation()
    }

    @Test
    fun `recreating the activity after a link keeps the one card`() = launching(view(LINK)) {
        awaitTag("friend.card.title")

        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        awaitTag("friend.card.title")
        assertRoute("route.friend")

        back()
        assertRoute("route.library")
        rule.onNodeWithTag("nav.tab.play").assertIsSelected()
    }

    @Test
    fun `a link while the app runs on Create opens the card in Play`() = launching(fromRecents(view(LINK))) {
        // Relaunched from Recents, the link that first started the task is not re-applied.
        assertRoute("route.home")
        tap("nav.tab.create")
        assertRoute("route.create")

        rule.runOnUiThread { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(rule.activity, view(LINK)) }
        rule.waitForIdle()
        awaitTag("friend.card.title")
        assertRoute("route.friend")
        rule.onNodeWithTag("nav.tab.play").assertIsSelected()

        back()
        assertRoute("route.library")
    }

    @Test
    fun `a link whose code does not parse opens the code sheet holding its text`() =
        launching(view("${ShareCode.LINK_PREFIX}K7Q2M")) {
            awaitTag("friend.code.sheet")
            assertRoute("route.library")
            rule.onNodeWithTag("nav.tab.play").assertIsSelected()
            rule.waitUntil(TIMEOUT_MS) {
                rule.onAllNodes(hasTestTag("friend.code.field") and hasText("K7Q2M")).fetchSemanticsNodes().isNotEmpty()
            }
        }

    /** Runs [block] over [MainActivity] started by [intent], torn down by the compose rule. */
    private fun launching(intent: Intent, block: () -> Unit) {
        rule = v2AndroidComposeTestRule(ActivityScenarioRule(intent), activityProvider = ::activityOf)
        val run = object : Statement() {
            override fun evaluate() = block()
        }
        rule.apply(run, Description.createTestDescription(FriendLinkE2ETest::class.java, "launching")).evaluate()
    }

    private fun activityOf(scenario: ActivityScenarioRule<MainActivity>): MainActivity {
        var activity: MainActivity? = null
        scenario.scenario.onActivity { activity = it }
        return checkNotNull(activity) { "MainActivity is not running" }
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    // ActivityScenario tracks its activity by intent, and onNewIntent makes the new one current:
    // the warm row starts on the same link (flags don't count) so its teardown still finds it.
    private fun fromRecents(intent: Intent) = intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

    private fun view(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java)

    private fun seedCastle() {
        shared.signedInUid = AUTHOR
        val doc = SharedPuzzleDoc(
            code = checkNotNull(ShareCode.parse(CODE)),
            formatVersion = SharedPuzzleDoc.FORMAT_VERSION,
            gameId = "queens",
            variant = QueensVariants.variant(SIZE).id.value,
            shareCodecVersion = QueensEditor.shareFormatVersion,
            payload = QueensEditor.encodeShare(QueensDraft(SIZE, regions(UNIQUE))),
            title = "Castle",
            authorName = "",
            note = "",
            authorUid = AUTHOR,
            authorTimeMs = 102_000,
            authorHints = 0,
            createdAtMs = 0,
            unpublished = false,
        )
        runBlocking { shared.publish(doc) }
    }

    private fun tap(tag: String) {
        awaitTag(tag)
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun awaitTag(tag: String) {
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    /** [tag] is the only `route.*` on screen once the transition settles. */
    private fun assertRoute(tag: String) {
        rule.waitUntil(TIMEOUT_MS) { routes() == listOf(tag) }
    }

    private fun routes(): List<String> = rule.onAllNodes(isRoute, useUnmergedTree = true).fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.TestTag] }

    private fun assertNoNavigation() {
        TABS.forEach { tab ->
            assertEquals("$tab hidden", 0, rule.onAllNodes(hasTestTag(tab)).fetchSemanticsNodes().size)
        }
    }

    /** Row-major region ids from letter rows ('A' = 0), space-separated. */
    private fun regions(rows: String): List<Int> = rows.filter { it != ' ' }.map { it - 'A' }

    private companion object {
        const val AUTHOR = "author"
        const val CODE = "K7Q2MX"
        const val LINK = "${ShareCode.LINK_PREFIX}$CODE"
        const val SIZE = 6

        /** One solution on a 6×6 board. */
        const val UNIQUE = "BBBBAA BBBBCA EBBDCA EEDDDD EEFFDF EEFFFF"
        val isRoute = SemanticsMatcher("test tag route.*") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("route.") == true
        }
        val TABS = listOf("nav.tab.home", "nav.tab.play", "nav.tab.history", "nav.tab.create")
        const val TIMEOUT_MS = 10_000L
    }
}
