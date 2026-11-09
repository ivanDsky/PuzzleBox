package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.ShippedReleaseFlags
import com.ivandsky.puzzlebox.create.FakeGameEditor
import com.ivandsky.puzzlebox.create.FakeGameEditorUi
import com.ivandsky.puzzlebox.create.TestDraft
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.navigation.editorEntry
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.EditorNotation
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.NotationHeader
import com.ivandsky.puzzlebox.game.editor.NotationResult
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * The Studio's overflow Import from text (M5-07) over the real view model, the real
 * `ImportDraftFromText` and a [FakeDraftRepository]: the item opens the one Import sheet and
 * exists only while [ReleaseFlag.TextImport] is on (the default [ReleaseFlags] are the shipped
 * ones). A good import replaces the Studio on the back stack with the new draft, so Back lands
 * on the Create root; the draft being edited is untouched. A bad one keeps the stack. Only an
 * imported draft's Studio carries the Imported chip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class StudioImportTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val drafts = FakeDraftRepository()
    private val navigator = Navigator(NavigationState(CreateKey, listOf(listOf(CreateKey, EditorKey(SOURCE_ID)))))

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `the overflow's Import from text opens the Import sheet`() {
        open(FakeReleaseFlags(ReleaseFlag.TextImport))

        composeRule.onNodeWithTag(MORE).performClick()
        awaitTag(MENU_IMPORT)
        composeRule.onNodeWithTag(MENU_IMPORT).performClick()

        awaitTag(SHEET)
    }

    @Test
    fun `a valid import replaces the Studio with the new draft and leaves the source untouched`() {
        open(FakeReleaseFlags(ReleaseFlag.TextImport))
        val source = drafts.rows.getValue(SOURCE_ID)

        importText(NOTATION)

        awaitTrue { navigator.state.currentStack.last() != EditorKey(SOURCE_ID) }
        val created = navigator.state.currentStack.last() as EditorKey
        assertEquals(listOf(CreateKey, created), navigator.state.currentStack)
        assertTrue("the new draft is stored imported", drafts.rows.getValue(created.draftId).imported)
        assertEquals("the source row is as it was", source, drafts.rows.getValue(SOURCE_ID))
        awaitTag(IMPORTED)
        awaitTag(IMPORTED_TOAST)
    }

    @Test
    fun `Back from the imported Studio lands on the Create root, not the old draft`() {
        open(FakeReleaseFlags(ReleaseFlag.TextImport))
        importText(NOTATION)
        awaitTag(IMPORTED)

        composeRule.onNodeWithTag(BACK).performClick()

        awaitTrue { navigator.state.currentStack == listOf(CreateKey) }
    }

    @Test
    fun `a malformed import keeps the stack and shows the error`() {
        open(FakeReleaseFlags(ReleaseFlag.TextImport))
        val before = navigator.state.currentStack

        importText("no header here")

        awaitTag(ERROR_HEADER)
        awaitTag(SHEET)
        assertEquals(before, navigator.state.currentStack)
        assertEquals("nothing was stored", setOf(SOURCE_ID), drafts.rows.keys)
        assertTrue("no toast", composeRule.onAllNodes(hasTestTag(IMPORTED_TOAST), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `the imported toast shows on the new Studio and not again once it is back on screen`() {
        open(FakeReleaseFlags(ReleaseFlag.TextImport))
        importText(NOTATION)
        awaitTag(IMPORTED_TOAST)

        mounted = false
        composeRule.waitForIdle()
        mounted = true

        awaitTag(TITLE)
        composeRule.waitForIdle()
        assertTrue("shown once", composeRule.onAllNodes(hasTestTag(IMPORTED_TOAST), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a Studio of a plain draft shows no Imported chip`() {
        open(FakeReleaseFlags(ReleaseFlag.TextImport))

        awaitTag(TITLE)
        assertTrue(composeRule.onAllNodes(hasTestTag(IMPORTED), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `an imported draft's Studio shows the Imported chip even with TextImport off`() {
        open(imported = true)

        awaitTag(IMPORTED)
    }

    @Test
    fun `with TextImport off the overflow has no Import from text`() {
        open()

        composeRule.onNodeWithTag(MORE).performClick()

        awaitTag(MENU_DUPLICATE)
        assertTrue(composeRule.onAllNodes(hasTestTag(MENU_IMPORT)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `importEnabled is on with TextImport`() {
        open(FakeReleaseFlags(ReleaseFlag.TextImport))

        assertTrue(vm.importEnabled.value)
    }

    @Test
    fun `importEnabled is off without TextImport`() {
        open()

        assertFalse(vm.importEnabled.value)
    }

    private lateinit var vm: EditorViewModel

    /** False while the Studio is taken off the screen, as a rotation or a Back and return does. */
    private var mounted by mutableStateOf(true)

    private fun importText(text: String) {
        composeRule.onNodeWithTag(MORE).performClick()
        awaitTag(MENU_IMPORT)
        composeRule.onNodeWithTag(MENU_IMPORT).performClick()
        awaitTag(SHEET)
        composeRule.onNodeWithTag(TEXT).performTextInput(text)
        composeRule.onNodeWithTag(SUBMIT).performClick()
    }

    /** Opens the Studio of the stored source draft; [flags] default to the shipped ones. */
    private fun open(flags: ReleaseFlags = ShippedReleaseFlags, imported: Boolean = false) {
        val editor = NotatedEditor()
        runBlocking { drafts.upsert(sourceRow(editor, imported)) }
        val games = GameRegistry(listOf(pluginOf(editor)))
        val editors = GameEditorRegistry(listOf(FakeGameEditorUi()))
        val published = FakePublishedPuzzleRepository()
        startKoin {
            modules(
                module {
                    viewModel { (key: EditorKey) ->
                        val saves = CoroutineScope(SupervisorJob() + Dispatchers.Main)
                        val copy = CreateDraftCopy(drafts, newId = { COPY_ID }, clock = { NOW })
                        EditorViewModel(
                            key, drafts, games, editors, GamePlayRegistry(emptyList()), copy, DefaultDispatcherProvider(),
                            saves = saves,
                            clock = { NOW },
                            createShared = signedOutPublishing(drafts),
                            publishedPuzzles = published,
                            identity = nobodySignedIn(),
                            idTokens = noGoogleAccount,
                            updatePublishState = signedOutUnpublishing(),
                            releaseFlags = flags,
                        ).also { vm = it }
                    }
                    idleCreateRoot(drafts)
                },
            )
        }
        val entries = entryProvider<NavKey>(fallback = { unknown -> NavEntry(unknown) {} }) {
            editorEntry(navigator, PlayLayout.SinglePane)
        }
        composeRule.setContent {
            PuzzleBoxTheme {
                if (mounted) {
                    val top = navigator.state.currentStack.last()
                    key(top) { entries(top).Content() }
                }
            }
        }
        awaitTag(TITLE)
    }

    private fun awaitTag(tag: String) =
        awaitTrue { composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

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

        override fun print(draft: TestDraft): String =
            NotationHeader(gameId.value, notationVersion).line + "\n${draft.marks}\n"

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

    private fun sourceRow(editor: GameEditor<TestDraft>, imported: Boolean) = Draft(
        id = SOURCE_ID,
        gameId = editor.gameId,
        variant = editor.variants.first(),
        draftFormatVersion = editor.draftFormatVersion,
        blob = byteArrayOf(1, 1),
        title = "Castle",
        updatedAt = NOW,
        testSolve = null,
        reservedCode = null,
        imported = imported,
        authorName = "",
        note = "",
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-08T10:00:00Z")
        val NOTATION = NotationHeader("fake", 1).line + "\n3\n"
        const val SOURCE_ID = "draft-1"
        const val COPY_ID = "copy-1"
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
        const val BACK = "create.editor.back"
        const val TITLE = "create.studio.title"
        const val MORE = "create.studio.more"
        const val MENU_DUPLICATE = "create.studio.duplicate"
        const val MENU_IMPORT = "create.studio.import"
        const val IMPORTED = "create.studio.imported"
        const val IMPORTED_TOAST = "create.toast.imported"
        const val SHEET = "create.import.sheet"
        const val TEXT = "create.import.text"
        const val SUBMIT = "create.import.submit"
        const val ERROR_HEADER = "create.import.error.header"
    }
}
