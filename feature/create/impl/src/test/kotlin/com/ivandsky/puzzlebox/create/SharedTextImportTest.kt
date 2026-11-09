package com.ivandsky.puzzlebox.create

import androidx.activity.ComponentActivity
import androidx.compose.runtime.key
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.navigation.createEntry
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.domain.ImportResult
import com.ivandsky.puzzlebox.domain.SyncSolvesCounts
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.EditorNotation
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.NotationHeader
import com.ivandsky.puzzlebox.game.editor.NotationResult
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedTextStore
import java.time.Instant
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
 * Text shared to PuzzleBox (M5-09) over the real Create root, view model and
 * `ImportDraftFromText`: [SharedTextStore]'s waiting text goes through the one Import sheet
 * (open, then submit), exactly once and only while the root shows. A good text stores one
 * imported draft and opens its Studio; any error leaves the sheet open with the typed error, and
 * an over-cap one an empty box and no draft (spec criterion 14).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class SharedTextImportTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val drafts = FakeDraftRepository()
    private val store = FakeSharedTextStore()
    private val navigator = Navigator(NavigationState(CreateKey, listOf(listOf(CreateKey))))
    private lateinit var vm: CreateViewModel

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `text waiting before the root shows imports one draft and opens its Studio`() {
        store.put(NOTATION)
        show()

        awaitTrue { navigator.state.currentStack.last() != CreateKey }

        assertEquals(listOf(CreateKey, EditorKey("draft-1")), navigator.state.currentStack)
        assertEquals(setOf("draft-1"), drafts.rows.keys)
        assertTrue(drafts.rows.getValue("draft-1").imported)
        assertNull(store.pending.value)
        assertNull(store.take())
    }

    @Test
    fun `text arriving while the root shows does the same`() {
        show()
        awaitTag(ROOT)

        store.put(NOTATION)
        awaitTrue { navigator.state.currentStack.last() != CreateKey }

        assertEquals(listOf(CreateKey, EditorKey("draft-1")), navigator.state.currentStack)
        assertEquals(setOf("draft-1"), drafts.rows.keys)
        assertTrue(drafts.rows.getValue("draft-1").imported)
        assertNull(store.pending.value)
    }

    @Test
    fun `a malformed text stores nothing and leaves the sheet open with the error`() {
        store.put("no header here")
        show()

        awaitTag(SHEET)
        awaitTrue { vm.importText.ui.value.error != null }

        assertTrue(vm.importText.ui.value.error is ImportResult.Malformed)
        assertEquals(emptySet<String>(), drafts.rows.keys)
        assertEquals(listOf<NavKey>(CreateKey), navigator.state.currentStack)
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
        assertEquals(listOf<NavKey>(CreateKey), navigator.state.currentStack)
    }

    @Test
    fun `text arriving while another screen covers the root waits for the root`() {
        show()
        awaitTag(ROOT)
        navigator.pushOnce(EditorKey("elsewhere"))
        awaitTrue { !composeRule.exists(ROOT) }

        store.put(NOTATION)
        idle()

        assertEquals(NOTATION, store.pending.value)
        assertEquals(emptySet<String>(), drafts.rows.keys)

        navigator.pop()
        awaitTrue { navigator.state.currentStack.last() != CreateKey }

        assertEquals(setOf("draft-1"), drafts.rows.keys)
        assertNull(store.pending.value)
    }

    @Test
    fun `the root showing again after an import takes nothing a second time`() {
        store.put(NOTATION)
        show()
        awaitTrue { navigator.state.currentStack.last() != CreateKey }

        navigator.pop()
        awaitTag(ROOT)
        idle()

        assertEquals(setOf("draft-1"), drafts.rows.keys)
        assertEquals(listOf<NavKey>(CreateKey), navigator.state.currentStack)
    }

    private fun show() {
        val editor = NotatedEditor()
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
                            releaseFlags = FakeReleaseFlags(),
                            clock = { NOW },
                        ).also { vm = it }
                    }
                },
            )
        }
        val entries = entryProvider<NavKey>(fallback = { unknown -> NavEntry(unknown) {} }) {
            createEntry(navigator)
        }
        composeRule.setContent {
            PuzzleBoxTheme {
                val top = navigator.state.currentStack.last()
                key(top) { entries(top).Content() }
            }
        }
    }

    private fun ComposeTestRule.exists(tag: String): Boolean =
        onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun awaitTag(tag: String) = awaitTrue { composeRule.exists(tag) }

    /** Lets the paused main looper run the view model's and the effects' coroutines. */
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

        override fun parse(text: String): NotationResult<TestDraft> {
            val marks = text.lines().firstOrNull { it.isNotBlank() }?.trim()?.toIntOrNull()
                ?: return NotationResult.Err(2, 1, "expected a mark count")
            return NotationResult.Ok(TestDraft(marks))
        }

        override fun print(draft: TestDraft): String = HEADER + "${draft.marks}\n"

        override fun columns(draft: TestDraft): Int = 1
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
            replaySession = base.replaySession,
            rendererModel = base.rendererModel,
            editor = editor,
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-08T10:00:00Z")
        val HEADER = NotationHeader("fake", 1).line + "\n"
        val NOTATION = HEADER + "3\n"
        const val OVERSIZE = 70 * 1024
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
        const val IDLE_MS = 200L
        const val ROOT = "route.create"
        const val SHEET = "create.import.sheet"
    }
}
