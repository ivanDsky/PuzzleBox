package com.ivandsky.puzzlebox.create

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.navigation.createEntry
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.domain.SyncSolvesCounts
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Create root's import entries (M5-06) over the real view model: the empty state's text
 * button and the New puzzle sheet's row each open the one Import sheet, and exist only while
 * [ReleaseFlag.TextImport] is on. A draft row shows its imported tag for an imported draft
 * only, flag or not: the suffix is data, not a gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportEntriesTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val game = GameId("fake")
    private val drafts = FakeDraftRepository()

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `the empty state's button opens the Import sheet`() {
        show(importOn = true)
        composeRule.onNodeWithTag(EMPTY_IMPORT).performScrollTo().performClick()
        composeRule.waitUntil { composeRule.exists(IMPORT_SHEET) }
    }

    @Test
    fun `the New puzzle sheet's row closes the sheet and opens the Import sheet`() {
        show(importOn = true, seed = listOf(draft("plain", imported = false)))
        composeRule.onNodeWithTag(NEW).performClick()
        composeRule.onNodeWithTag(NEW_IMPORT).performScrollTo().performClick()

        composeRule.waitUntil { composeRule.exists(IMPORT_SHEET) }
        composeRule.onAllNodesWithTag(NEW_SHEET).assertCountEquals(0)
    }

    @Test
    fun `with the flag off neither entry exists`() {
        show(importOn = false)
        composeRule.onAllNodesWithTag(EMPTY_IMPORT).assertCountEquals(0)
    }

    @Test
    fun `with the flag off the New puzzle sheet has no import row`() {
        show(importOn = false, seed = listOf(draft("plain", imported = false)))
        composeRule.onNodeWithTag(NEW).performClick()
        composeRule.onNodeWithTag(NEW_SHEET).assertExists()
        composeRule.onAllNodesWithTag(NEW_IMPORT).assertCountEquals(0)
    }

    @Test
    fun `only the imported draft shows the imported tag`() {
        show(importOn = true, seed = listOf(draft("plain", imported = false), draft("copy", imported = true)))
        composeRule.onAllNodesWithTag(DRAFT_ROW).assertCountEquals(2)
        // The row merges its children into one node; the suffix is its own text inside it.
        composeRule.onAllNodesWithTag(DRAFT_IMPORTED, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `an imported draft keeps its tag while the flag is off`() {
        show(importOn = false, seed = listOf(draft("copy", imported = true)))
        composeRule.onAllNodesWithTag(DRAFT_IMPORTED, useUnmergedTree = true).assertCountEquals(1)
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.exists(tag: String): Boolean =
        onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun show(importOn: Boolean, seed: List<Draft> = emptyList()) {
        val plugin = withEditor(FakeGameEditor(game))
        val games = GameRegistry(listOf(plugin))
        val editors = GameEditorRegistry(listOf(FakeGameEditorUi(game)))
        val createDraft = CreateDraft(games, drafts, newId = { "new" }, clock = { NOW })
        runBlocking { seed.forEach { drafts.upsert(it) } }
        val flags = if (importOn) FakeReleaseFlags(ReleaseFlag.TextImport) else FakeReleaseFlags()
        startKoin {
            modules(
                module {
                    viewModel {
                        CreateViewModel(
                            games,
                            editors,
                            GamePlayRegistry(emptyList()),
                            drafts,
                            FakePublishedPuzzleRepository(),
                            createDraft,
                            CreateDraftCopy(drafts, newId = { "copy" }, clock = { NOW }),
                            DefaultDispatcherProvider(),
                            syncSolves = SyncSolvesCounts(FakePublishedPuzzleRepository(), FakeCustomBoardRepository(), clock = { NOW }),
                            releaseFlags = flags,
                            clock = { NOW },
                        )
                    }
                },
            )
        }
        val navigator = Navigator(NavigationState(CreateKey, listOf(listOf(CreateKey))))
        composeRule.setContent {
            PuzzleBoxTheme {
                entryProvider<NavKey> { createEntry(navigator) }(CreateKey).Content()
            }
        }
    }

    private fun draft(id: String, imported: Boolean): Draft = Draft(
        id = id,
        gameId = game,
        variant = FakeGameEditor(game).variants.first(),
        draftFormatVersion = 1,
        blob = byteArrayOf(1, 0),
        title = id,
        updatedAt = NOW,
        testSolve = null,
        reservedCode = null,
        imported = imported,
        authorName = "",
        note = "",
    )

    private fun withEditor(editor: FakeGameEditor): GamePlugin<*, *, *> {
        val base = TestGames.plugin(game)
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
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
        const val NEW = "create.new"
        const val NEW_SHEET = "create.new.sheet"
        const val NEW_IMPORT = "create.new.import"
        const val EMPTY_IMPORT = "create.empty.import"
        const val IMPORT_SHEET = "create.import.sheet"
        const val DRAFT_ROW = "create.draft.row"
        const val DRAFT_IMPORTED = "create.draft.imported"
    }
}
