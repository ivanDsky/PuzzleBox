package com.ivandsky.puzzlebox.app.e2e

import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.AndroidComposeTestRule as v2AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.ivandsky.puzzlebox.app.MainActivity
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.data.repository.SharedTextStore
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedTextStore
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A plain-text share over the real activity with `ShareTarget` forced on: the debug manifest
 * routes `ACTION_SEND text/plain` to [MainActivity]; a cold start and a warm new intent (from
 * the Play tab) both end on the Studio of an imported draft with the verdict the same board
 * pasted would show (Unique), back returns to the Create root, and recreating the activity
 * afterwards creates no second draft. A task relaunched from Recents does not re-apply its
 * share, and with the flag off a share opens the app on its start tab and creates nothing.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
// A real phone's height: the Studio's board slot needs room for its grid.
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class ShareTargetE2ETest {

    private lateinit var rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

    private val drafts = FakeDraftRepository()
    private val store = FakeSharedTextStore()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var graph: E2eKoinGraph

    @After
    fun stopGraph() = graph.stop()

    @Test
    fun `the debug manifest routes plain text shares, and only them, to the app`() {
        startGraph(ReleaseFlag.ShareTarget)

        fun handlers(type: String) = context.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_SEND).setType(type), 0)
            .map { it.activityInfo.name }

        assertEquals(listOf(MainActivity::class.java.name), handlers("text/plain"))
        assertEquals(emptyList<String>(), handlers("image/png"))
    }

    @Test
    fun `a cold start from a share ends on the Studio of an imported draft`() {
        startGraph(ReleaseFlag.ShareTarget)
        launching(send(UNIQUE)) {
            awaitVerdict()
            assertRoute(EDITOR)
            assertEquals("one imported draft", 1, drafts.rows.size)
            assertTrue("flagged imported", drafts.rows.values.single().imported)
            assertNull("the text was taken", store.pending.value)

            back()
            assertRoute(CREATE)
        }
    }

    @Test
    fun `a share while the app runs on Play opens the Studio and back returns to Create`() {
        startGraph(ReleaseFlag.ShareTarget)
        // Relaunched from Recents, the share that first started the task is not re-applied.
        launching(fromRecents(send(UNIQUE))) {
            assertRoute(HOME)
            tap("nav.tab.play")
            assertRoute(LIBRARY)

            deliver(send(UNIQUE))
            awaitVerdict()
            assertRoute(EDITOR)
            assertEquals(1, drafts.rows.size)

            back()
            assertRoute(CREATE)
            rule.onNodeWithTag("nav.tab.create").assertIsSelected()
        }
    }

    @Test
    fun `a share from Play while Create holds a Studio opens the shared draft, not the old one`() {
        startGraph(ReleaseFlag.ShareTarget)
        seedBlankDraft()
        launching(fromRecents(send(UNIQUE))) {
            openSeededStudio()
            tap("nav.tab.play")
            assertRoute(LIBRARY)

            deliver(send(UNIQUE))
            awaitVerdict()
            assertRoute(EDITOR)
            assertEquals("the seeded draft and the imported one", 2, drafts.rows.size)
            assertNull(store.pending.value)

            back()
            assertRoute(CREATE)
            rule.onNodeWithTag("nav.tab.create").assertIsSelected()
        }
    }

    @Test
    fun `a share while Create shows a Studio opens the shared draft, then back is the Create root`() {
        startGraph(ReleaseFlag.ShareTarget)
        seedBlankDraft()
        launching(fromRecents(send(UNIQUE))) {
            openSeededStudio()

            deliver(send(UNIQUE))
            awaitVerdict()
            assertRoute(EDITOR)
            assertEquals(2, drafts.rows.size)

            back()
            assertRoute(CREATE)
        }
    }

    @Test
    fun `recreating the activity after a share creates no second draft`() {
        startGraph(ReleaseFlag.ShareTarget)
        launching(send(UNIQUE)) {
            awaitVerdict()

            rule.activityRule.scenario.recreate()
            rule.waitForIdle()
            idle()

            assertRoute(EDITOR)
            assertEquals("still one draft", 1, drafts.rows.size)
            assertNull(store.pending.value)
        }
    }

    @Test
    fun `a share re-delivered from Recents is not imported`() {
        startGraph(ReleaseFlag.ShareTarget)
        launching(fromRecents(send(UNIQUE))) {
            idle()

            assertRoute(HOME)
            assertEquals(emptyMap<String, Any>(), drafts.rows)
            assertNull(store.pending.value)
        }
    }

    @Test
    fun `with ShareTarget off a share opens the start tab and creates nothing`() {
        startGraph()
        launching(send(UNIQUE)) {
            idle()

            assertRoute(HOME)
            rule.onNodeWithTag("nav.tab.home").assertIsSelected()
            assertEquals(emptyMap<String, Any>(), drafts.rows)
            assertNull("nothing waits in the store", store.pending.value)
        }
    }

    /** Create's root, then the seeded draft's row opens its Studio. */
    private fun openSeededStudio() {
        tap("nav.tab.create")
        assertRoute(CREATE)
        tap("create.draft.row")
        awaitTag("create.editor.board")
        assertRoute(EDITOR)
    }

    /** A blank 5×5 Queens draft: a Studio whose verdict is not Unique. */
    private fun seedBlankDraft() {
        val editor = checkNotNull(graph.koin.get<GameRegistry>()[GameId("queens")]?.editor)
        val board = EditorDraft(QueensEditor, DraftState(QueensDraft(SIZE, List(SIZE * SIZE) { 0 })))
        runBlocking {
            drafts.upsert(
                Draft(
                    id = "castle",
                    gameId = GameId("queens"),
                    variant = QueensVariants.variant(SIZE).id,
                    draftFormatVersion = editor.draftFormatVersion,
                    blob = board.encode(),
                    title = "Castle",
                    updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
                    testSolve = null,
                    reservedCode = null,
                    imported = false,
                    authorName = "",
                    note = "",
                ),
            )
        }
    }

    private fun startGraph(vararg flags: ReleaseFlag) {
        graph = E2eKoinGraph(
            E2E_TODAY,
            overrides = module {
                single<DraftRepository> { drafts }
                single<SharedTextStore> { store }
            },
            releaseFlags = FakeReleaseFlags(*flags),
        )
        graph.start()
    }

    /** Runs [block] over [MainActivity] started by [intent], torn down by the compose rule. */
    private fun launching(intent: Intent, block: () -> Unit) {
        rule = v2AndroidComposeTestRule(ActivityScenarioRule(intent), activityProvider = ::activityOf)
        val run = object : Statement() {
            override fun evaluate() = block()
        }
        rule.apply(run, Description.createTestDescription(ShareTargetE2ETest::class.java, "launching")).evaluate()
    }

    private fun activityOf(scenario: ActivityScenarioRule<MainActivity>): MainActivity {
        var activity: MainActivity? = null
        scenario.scenario.onActivity { activity = it }
        return checkNotNull(activity) { "MainActivity is not running" }
    }

    private fun deliver(intent: Intent) {
        rule.runOnUiThread { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(rule.activity, intent) }
        rule.waitForIdle()
    }

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    // ActivityScenario tracks its activity by intent, and onNewIntent makes the new one current:
    // the warm row starts on the same share (flags don't count) so its teardown still finds it.
    private fun fromRecents(intent: Intent) = intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

    private fun send(text: String) = Intent(Intent.ACTION_SEND, null, context, MainActivity::class.java)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)

    private fun tap(tag: String) {
        awaitTag(tag)
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun awaitTag(tag: String) = awaitIdling {
        rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    /** The Studio's verdict pill says Unique: the verdict of the same board pasted. */
    private fun awaitVerdict() = awaitIdling {
        val unique = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Unique")
        rule.onAllNodes(hasTestTag(VERDICT) and unique, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    /** A coroutine hop to the main looper never runs under the compose rule's frame-only wait. */
    private fun awaitIdling(condition: () -> Boolean) {
        rule.waitUntil(TIMEOUT_MS) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IDLE_STEP_MS))
            condition()
        }
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(SETTLE_MS))
        rule.waitForIdle()
    }

    /** [tag] is the only `route.*` on screen once the transition settles. */
    private fun assertRoute(tag: String) = awaitIdling { routes() == listOf(tag) }

    private fun routes(): List<String> = rule.onAllNodes(isRoute, useUnmergedTree = true).fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.TestTag] }

    private companion object {
        const val HOME = "route.home"
        const val LIBRARY = "route.library"
        const val CREATE = "route.create"
        const val EDITOR = "route.editor"
        const val VERDICT = "create.editor.pill"
        const val SIZE = 5
        const val TIMEOUT_MS = 10_000L
        const val IDLE_STEP_MS = 50L
        const val SETTLE_MS = 500L

        // games/queens' queens-unique-5.txt fixture
        const val UNIQUE = "puzzlebox queens v1\n00001\n00031\n42033\n43333\n43333\n"
        val isRoute = SemanticsMatcher("test tag route.*") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("route.") == true
        }
    }
}
