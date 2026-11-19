package com.ivandsky.puzzlebox.create

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.create.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.create.domain.ImportResult
import com.ivandsky.puzzlebox.create.domain.SyncSolvesCounts
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.EditorNotation
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.NotationHeader
import com.ivandsky.puzzlebox.game.editor.NotationResult
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedTextStore
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * The lifecycle of text shared to PuzzleBox (M5-09) over the real Custom list, view model and
 * `ImportDraftFromText`: [SharedTextStore]'s waiting text goes through the one Import sheet
 * (open, then submit), exactly once and only while the list shows and is resumed. An over-cap
 * text leaves an empty box and no draft (spec criterion 14). The plain import and the malformed
 * text are [CustomBodyTest]'s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class CustomBodySharedTextTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val drafts = FakeDraftRepository()
    private val store = FakeSharedTextStore()
    private val opened = mutableListOf<String>()

    /** Another screen over the list, as the Studio is once a draft opens. */
    private var covered by mutableStateOf(false)
    private lateinit var vm: CreateViewModel
    private val editor = NotatedEditor()

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `text arriving while the list shows imports one draft and opens it`() {
        show()
        awaitTrue { listShows() }

        store.put(NOTATION)
        awaitTrue { opened.isNotEmpty() }

        assertEquals(listOf("draft-1"), opened)
        assertEquals(setOf("draft-1"), drafts.rows.keys)
        assertTrue(drafts.rows.getValue("draft-1").imported)
        assertNull(store.pending.value)
    }

    @Test
    fun `a 70 KB text stores nothing and leaves the box empty`() {
        store.put(HEADER + "x".repeat(OVERSIZE))
        show()

        awaitTrue { vm.importText.ui.value.error != null }

        assertTrue(vm.importText.ui.value.error is ImportResult.TooLarge)
        assertEquals("", vm.importText.ui.value.text)
        assertTrue(vm.importText.ui.value.visible)
        assertEquals(emptySet<String>(), drafts.rows.keys)
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun `text arriving while another screen covers the list waits for the list`() {
        show()
        awaitTrue { listShows() }
        composeRule.runOnUiThread { covered = true }
        awaitTrue { !listShows() }

        store.put(NOTATION)
        idle()

        assertEquals(NOTATION, store.pending.value)
        assertEquals(emptySet<String>(), drafts.rows.keys)

        composeRule.runOnUiThread { covered = false }
        idle()
        awaitTrue { opened.isNotEmpty() }

        assertEquals(setOf("draft-1"), drafts.rows.keys)
        assertNull(store.pending.value)
    }

    @Test
    fun `the list showing again after an import takes nothing a second time`() {
        store.put(NOTATION)
        show()
        awaitTrue { opened.isNotEmpty() }

        composeRule.runOnUiThread { covered = false }
        awaitTrue { listShows() }
        idle()

        assertEquals(setOf("draft-1"), drafts.rows.keys)
        assertEquals(listOf("draft-1"), opened)
    }

    @Test
    fun `a list that is only started takes nothing until it is resumed`() {
        val owner = ManualLifecycle(Lifecycle.State.STARTED)
        store.put(NOTATION)
        show(owner)
        awaitTrue { listShows() }
        idle()

        assertEquals(NOTATION, store.pending.value)
        assertEquals(emptySet<String>(), drafts.rows.keys)

        composeRule.runOnUiThread { owner.moveTo(Lifecycle.State.RESUMED) }
        awaitTrue { opened.isNotEmpty() }

        assertEquals(setOf("draft-1"), drafts.rows.keys)
        assertNull(store.pending.value)
    }

    @Test
    fun `a text shared during a check waits for it, then imports after the first`() {
        show()
        awaitTrue { listShows() }
        store.put(BLOCKED)
        awaitTrue { editor.entered.count == 0L }
        assertTrue(vm.importText.ui.value.checking)

        store.put(NOTATION)
        idle()

        assertEquals(NOTATION, store.pending.value)
        assertEquals(emptySet<String>(), drafts.rows.keys)

        editor.release.countDown()
        awaitTrue {
            // The first Studio may cover the list before the waiting text is taken; it waits there.
            if (store.pending.value != null && covered) covered = false
            drafts.rows.size == 2 && store.pending.value == null
        }

        assertEquals(listOf<Byte>(1, 5), drafts.rows.getValue("draft-1").blob.toList())
        assertEquals(listOf<Byte>(1, 3), drafts.rows.getValue("draft-2").blob.toList())
    }

    private fun show(owner: LifecycleOwner? = null) {
        val games = GameRegistry(listOf(pluginOf(editor)))
        var ids = 0
        startKoin {
            modules(
                module {
                    viewModel {
                        CreateViewModel(
                            games,
                            GameEditorRegistry(listOf(FakeGameEditorUi())),
                            GamePlayRegistry(emptyList()),
                            drafts,
                            FakePublishedPuzzleRepository(),
                            CreateDraft(games, drafts, newId = { "draft-${++ids}" }, clock = { NOW }),
                            CreateDraftCopy(drafts, newId = { "copy" }, clock = { NOW }),
                            DefaultDispatcherProvider(),
                            sharedText = store,
                            syncSolves = SyncSolvesCounts(FakePublishedPuzzleRepository(), FakeCustomBoardRepository(), clock = { NOW }),
                            clock = { NOW },
                            cloudSync = FakeCloudSync(),
                        ).also { vm = it }
                    }
                },
            )
        }
        composeRule.setContent {
            PuzzleBoxTheme {
                if (owner == null) {
                    List()
                } else {
                    CompositionLocalProvider(LocalLifecycleOwner provides owner) { List() }
                }
            }
        }
    }

    /** The Custom list, gone while [covered], reporting each draft it opens and then covered by that draft's screen. */
    @Composable
    private fun List() {
        if (covered) return
        CustomBody(
            listState = rememberLazyListState(),
            onOpenDraft = {
                opened += it
                covered = true
            },
            onOpenPublished = {},
        )
    }

    private fun ComposeTestRule.exists(tag: String): Boolean =
        onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun awaitTag(tag: String) = awaitTrue { composeRule.exists(tag) }

    /** Lets the paused main looper run the view model's and the effects' coroutines. */
    /** The body is up: the list, or with no draft yet the first-time screen. */
    private fun listShows() = composeRule.exists(LIST) || composeRule.exists(EMPTY)

    private fun idle() {
        composeRule.waitForIdle()
        ShadowLooper.idleMainLooper(IDLE_MS, TimeUnit.MILLISECONDS)
        composeRule.waitForIdle()
    }

    /** Waits for [condition], running the paused main looper so view-model coroutines progress. */
    private fun awaitTrue(condition: () -> Boolean) {
        composeRule.waitUntil(TIMEOUT_MS) {
            ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
            condition()
        }
    }

    /** The stand-in game with a notation: a header line, then the board's mark count. */
    private class NotatedEditor(
        private val base: FakeGameEditor = FakeGameEditor(),
    ) : GameEditor<TestDraft> by base, EditorNotation<TestDraft> {
        override val notationVersion: Int = 1

        /** Counts down when a [BLOCKED] parse starts; that parse then waits for [release]. */
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)

        override fun parse(text: String): NotationResult<TestDraft> {
            if (text.lines().any { it.trim() == BLOCK_MARKS }) {
                entered.countDown()
                release.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
            val marks = text.lines().firstOrNull { it.isNotBlank() }?.trim()?.toIntOrNull()
                ?: return NotationResult.Err(2, 1, "expected a mark count")
            return NotationResult.Ok(TestDraft(marks))
        }

        override fun print(draft: TestDraft): String = HEADER + "${draft.marks}\n"

        override fun columns(draft: TestDraft): Int = 1
    }

    /** A lifecycle the test moves by hand, standing in for the screen's own. */
    private class ManualLifecycle(initial: Lifecycle.State) : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this).also { it.currentState = initial }
        override val lifecycle: Lifecycle get() = registry

        fun moveTo(state: Lifecycle.State) {
            registry.currentState = state
        }
    }

    private fun pluginOf(editor: GameEditor<TestDraft>): GamePlugin<*, *, *> {
        val base = TestGames.plugin(editor.gameId)
        return GamePlugin(
            definition = base.definition,
            rules = base.rules,
            session = base.session,
            score = base.score,
            stateOf = base.stateOf,
            initialSession = base.initialSession,
            refOf = base.refOf,
            playAgainSession = base.playAgainSession,
            rendererModel = base.rendererModel,
            editor = editor,
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-08T10:00:00Z")
        val HEADER = NotationHeader("fake", 1).line + "\n"
        val NOTATION = HEADER + "3\n"
        const val BLOCK_MARKS = "5"

        /** Its parse waits for the test's release, so a check can be held mid-flight. */
        val BLOCKED = HEADER + "$BLOCK_MARKS\n"
        const val OVERSIZE = 70 * 1024
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
        const val IDLE_MS = 200L
        const val LIST = "custom.list"
        const val EMPTY = "create.empty"
        const val SHEET = "create.import.sheet"
    }
}
