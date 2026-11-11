package com.ivandsky.puzzlebox.app.create

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.create.CreateViewModel
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.editor.EditorViewModel
import com.ivandsky.puzzlebox.create.navigation.createEntry
import com.ivandsky.puzzlebox.create.navigation.editorEntry
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.domain.CreateSharedPuzzle
import com.ivandsky.puzzlebox.domain.SyncSolvesCounts
import com.ivandsky.puzzlebox.domain.UpdatePublishState
import com.ivandsky.puzzlebox.domain.ValidatePublish
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.sudokuPlugin
import com.ivandsky.puzzlebox.games.sudokuui.editor.SudokuEditorUi
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedTextStore
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import org.robolectric.shadows.ShadowLooper

/**
 * The New puzzle sheet over the real Mini Sudoku plugin and editor UI with no flag on: it offers
 * Sudoku, opens the Sudoku editor, and one placed digit moves the verdict pill.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NewPuzzleSheetSudokuGateTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val mini = SudokuGames.MINI.id
    private val drafts = FakeDraftRepository()
    private val navigator = Navigator(NavigationState(CreateKey, listOf(listOf(CreateKey))))

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `Sudoku is offered, opens the editor, and a digit moves the pill`() {
        show(FakeReleaseFlags())
        rule.onNodeWithTag(NEW).performClick()
        rule.onNodeWithTag(SHEET).assertIsDisplayed()
        // The mini board is Sudoku's one size: its row opens the editor, with no size step between.
        rule.onNodeWithTag("$GAME${mini.value}").performClick()
        rule.waitUntil(TIMEOUT_MS) { navigator.state.currentStack.last() is EditorKey }
        rule.onNodeWithTag("$SIZE${SudokuGames.MINI.variant.id.value}").assertDoesNotExist()

        awaitPill("Incomplete")
        rule.onNodeWithTag("sudoku.cell.0").performClick()
        rule.onNodeWithTag(DIGIT_ONE).performClick()
        awaitPill("Multiple")
    }

    private fun show(flags: FakeReleaseFlags) {
        val games = GameRegistry(listOf(sudokuPlugin(SudokuGames.MINI)))
        val editors = GameEditorRegistry(listOf(SudokuEditorUi(SudokuGames.MINI)))
        var next = 0
        val createDraft = CreateDraft(games, drafts, newId = { "draft-${++next}" }, clock = { NOW })
        startKoin {
            modules(module { single<ReleaseFlags> { flags } })
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
                            CreateDraftCopy(drafts, newId = { "copy-1" }, clock = { NOW }),
                            DefaultDispatcherProvider(),
                            sharedText = FakeSharedTextStore(),
                            syncSolves = SyncSolvesCounts(FakePublishedPuzzleRepository(), FakeCustomBoardRepository(), clock = { NOW }),
                            releaseFlags = flags,
                            clock = { NOW },
                        )
                    }
                    viewModel { (key: EditorKey) ->
                        EditorViewModel(
                            key,
                            drafts,
                            games,
                            editors,
                            GamePlayRegistry(emptyList()),
                            CreateDraftCopy(drafts, newId = { "copy-1" }, clock = { NOW }),
                            DefaultDispatcherProvider(),
                            saves = CoroutineScope(SupervisorJob() + Dispatchers.Main),
                            clock = { NOW },
                            createShared = CreateSharedPuzzle(
                                drafts = drafts,
                                shared = FakeSharedPuzzleRepository(),
                                published = FakePublishedPuzzleRepository(),
                                identity = FakeIdentityService(initial = null),
                                validate = ValidatePublish(),
                                random = Random(0),
                                clock = { NOW },
                            ),
                            publishedPuzzles = FakePublishedPuzzleRepository(),
                            identity = FakeIdentityService(initial = null),
                            idTokens = AbsentGoogleIdTokenProvider,
                            updatePublishState = UpdatePublishState(FakeSharedPuzzleRepository(), FakePublishedPuzzleRepository()),
                            preferences = FakeUserPreferencesRepository(),
                        )
                    }
                },
            )
        }
        // One stored draft keeps the draft list, and its New puzzle button, on screen.
        runBlocking { createDraft(mini, SudokuGames.MINI.variant.id) }
        rule.setContent {
            PuzzleBoxTheme {
                val provider = entryProvider<NavKey> {
                    createEntry(navigator)
                    editorEntry(navigator, PlayLayout.SinglePane)
                }
                provider(navigator.state.currentStack.last()).Content()
            }
        }
    }

    /** Waits for the pill's [state], running the paused main looper's clock so the pipeline's debounce elapses. */
    private fun awaitPill(state: String) {
        rule.waitUntil(TIMEOUT_MS) {
            ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
            rule.onAllNodes(
                SemanticsMatcher.expectValue(SemanticsProperties.TestTag, PILL) and
                    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state),
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private object AbsentGoogleIdTokenProvider : GoogleIdTokenProvider {
        override suspend fun idToken(activityContext: android.content.Context): GoogleIdToken =
            GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.NO_CREDENTIAL)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
        const val NEW = "create.new"
        const val SHEET = "create.new.sheet"
        const val GAME = "create.new.game."
        const val SIZE = "create.new.size."
        const val DIGIT_ONE = "sudoku.editor.key.1"
        const val PILL = "create.editor.pill"
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
    }
}
