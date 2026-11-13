package com.ivandsky.puzzlebox.app.e2e

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.WindowManager
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.AndroidComposeTestRule as v2AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.MainActivity
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Device check R3: the keyboard adjusts the app's window by resize, never by pan (a pan slid the
 * Studio's top bar under the status bar, and below API 30 left no IME inset to scroll by). The
 * manifest declares it on [MainActivity], so the system never re-picks pan, and the Create
 * root's search field, the main window's other text field, still filters under it.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class KeyboardModeE2ETest {

    private lateinit var rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

    private val drafts = FakeDraftRepository()
    private val graph = E2eKoinGraph(E2E_TODAY, overrides = module { single<DraftRepository> { drafts } })
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun stopGraph() = graph.stop()

    @Test
    fun `the activity declares that the keyboard resizes its window`() {
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertEquals(RESIZE, info.softInputMode and ADJUST)
    }

    @Test
    fun `the Create search field filters drafts in the resizing window`() {
        graph.start()
        seedDrafts()
        launching {
            assertEquals(RESIZE, rule.activity.window.attributes.softInputMode and ADJUST)
            tap("nav.tab.create")
            awaitIdling { count(ROW) > 1 }
            tap(SEARCH)
            rule.onNodeWithTag(SEARCH).performTextInput("Castle 3")
            awaitIdling { count(ROW) == 1 }
        }
    }

    /** More drafts than the search threshold (8), so the search field shows. */
    private fun seedDrafts() {
        val editor = checkNotNull(graph.koin.get<GameRegistry>()[GameId("queens")]?.editor)
        val board = EditorDraft(QueensEditor, DraftState(QueensDraft(SIZE, List(SIZE * SIZE) { 0 })))
        runBlocking {
            repeat(DRAFTS) { index ->
                drafts.upsert(
                    Draft(
                        id = "castle-$index",
                        gameId = GameId("queens"),
                        variant = QueensVariants.variant(SIZE).id,
                        draftFormatVersion = editor.draftFormatVersion,
                        blob = board.encode(),
                        title = "Castle $index",
                        updatedAt = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index.toLong()),
                        testSolve = null,
                        reservedCode = null,
                        imported = false,
                        authorName = "",
                        note = "",
                    ),
                )
            }
        }
    }

    /** Runs [block] over [MainActivity] launched from the home screen, torn down by the compose rule. */
    private fun launching(block: () -> Unit) {
        val intent = Intent(Intent.ACTION_MAIN, null, context, MainActivity::class.java).addCategory(Intent.CATEGORY_LAUNCHER)
        rule = v2AndroidComposeTestRule(ActivityScenarioRule(intent), activityProvider = ::activityOf)
        val run = object : Statement() {
            override fun evaluate() = block()
        }
        rule.apply(run, Description.createTestDescription(KeyboardModeE2ETest::class.java, "launching")).evaluate()
    }

    private fun activityOf(scenario: ActivityScenarioRule<MainActivity>): MainActivity {
        var activity: MainActivity? = null
        scenario.scenario.onActivity { activity = it }
        return checkNotNull(activity) { "MainActivity is not running" }
    }

    private fun tap(tag: String) {
        awaitIdling { count(tag) > 0 }
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun count(tag: String) = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    /** A coroutine hop to the main looper never runs under the compose rule's frame-only wait. */
    private fun awaitIdling(condition: () -> Boolean) {
        rule.waitUntil(TIMEOUT_MS) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IDLE_STEP_MS))
            condition()
        }
    }

    private companion object {
        const val ADJUST = WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST

        // The manifest's android:windowSoftInputMode="adjustResize" is what this test pins, and the
        // platform has no non-deprecated constant for reading that mode back from the window.
        @Suppress("DEPRECATION")
        const val RESIZE = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        const val ROW = "create.draft.row"
        const val SEARCH = "create.search"
        const val DRAFTS = 9
        const val SIZE = 5
        const val TIMEOUT_MS = 10_000L
        const val IDLE_STEP_MS = 50L
    }
}
