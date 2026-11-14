package com.ivandsky.puzzlebox.create

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
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
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedTextStore
import java.time.Instant
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

/**
 * The Create root's import entries (M5-06) over the real view model: the empty state's text
 * button and the New puzzle sheet's row each open the one Import sheet. A draft row shows its
 * imported tag for an imported draft only: the suffix is data, not a gate.
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
        show()
        composeRule.onNodeWithTag(EMPTY_IMPORT).performScrollTo().performClick()
        composeRule.waitUntil { composeRule.exists(IMPORT_SHEET) }
    }

    @Test
    fun `the New puzzle sheet's row closes the sheet and opens the Import sheet`() {
        show(seed = listOf(draft("plain", imported = false)))
        composeRule.onNodeWithTag(NEW).performClick()
        composeRule.onNodeWithTag(NEW_IMPORT).performScrollTo().performClick()

        composeRule.waitUntil { composeRule.exists(IMPORT_SHEET) }
        composeRule.onAllNodesWithTag(NEW_SHEET).assertCountEquals(0)
    }

    @Test
    fun `only the imported draft shows the imported tag`() {
        show(seed = listOf(draft("plain", imported = false), draft("copy", imported = true)))
        composeRule.onAllNodesWithTag(DRAFT_ROW).assertCountEquals(2)
        // The row merges its children into one node; the suffix is its own text inside it.
        composeRule.onAllNodesWithTag(DRAFT_IMPORTED, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `the actions sheet header carries the imported tag of an imported draft`() {
        show(seed = listOf(draft("copy", imported = true)))
        composeRule.onNodeWithTag(DRAFT_MORE).performClick()
        composeRule.waitUntil { composeRule.exists(ACTIONS_CONTINUE) }
        // One in the row behind the sheet, one in the sheet's own header.
        composeRule.onAllNodesWithTag(DRAFT_IMPORTED, useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun `the row state flags an imported draft and its meta carries no suffix`() {
        val games = GameRegistry(listOf(withEditor(FakeGameEditor(game))))
        val editors = GameEditorRegistry(listOf(FakeGameEditorUi(game)))
        val plays = GamePlayRegistry(emptyList())
        val imported = rowOf(draft("copy", imported = true), emptyMap(), NOW, games, editors, plays)
        val plain = rowOf(draft("plain", imported = false), emptyMap(), NOW, games, editors, plays)

        assertTrue(imported.imported)
        assertFalse(plain.imported)
        assertEquals(plain.meta, imported.meta)
    }

    private fun ComposeTestRule.exists(tag: String): Boolean =
        onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun show(seed: List<Draft> = emptyList()) {
        val plugin = withEditor(FakeGameEditor(game))
        val games = GameRegistry(listOf(plugin))
        val editors = GameEditorRegistry(listOf(FakeGameEditorUi(game)))
        val createDraft = CreateDraft(games, drafts, newId = { "new" }, clock = { NOW })
        runBlocking { seed.forEach { drafts.upsert(it) } }
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
                            sharedText = FakeSharedTextStore(),
                            syncSolves = SyncSolvesCounts(FakePublishedPuzzleRepository(), FakeCustomBoardRepository(), clock = { NOW }),
                            clock = { NOW },
                            cloudSync = FakeCloudSync(),
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
            playAgainSession = base.playAgainSession,
            rendererModel = base.rendererModel,
            editor = editor,
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
        const val NEW = "create.new"
        const val NEW_SHEET = "create.new.sheet"
        const val DRAFT_MORE = "create.draft.more"
        const val ACTIONS_CONTINUE = "create.actions.continue"
        const val NEW_IMPORT = "create.new.import"
        const val EMPTY_IMPORT = "create.empty.import"
        const val IMPORT_SHEET = "create.import.sheet"
        const val DRAFT_ROW = "create.draft.row"
        const val DRAFT_IMPORTED = "create.draft.imported"
    }
}
