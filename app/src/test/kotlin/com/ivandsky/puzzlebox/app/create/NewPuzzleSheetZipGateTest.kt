package com.ivandsky.puzzlebox.app.create

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
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
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import com.ivandsky.puzzlebox.games.queensui.editor.QueensEditorUi
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.editor.ZipEditor
import com.ivandsky.puzzlebox.games.zip.zipPlugin
import com.ivandsky.puzzlebox.games.zipui.editor.ZipEditorUi
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
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
 * The New puzzle sheet over the real Zip plugin and editor UI, behind `ZipEditor`: off, the sheet
 * has no Zip option; on, it offers Zip, opens the Zip editor, and one stroke on its board moves
 * the verdict pill off Incomplete. The plugin carries [ZipEditor] either way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NewPuzzleSheetZipGateTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val zip = ZipDefinition.id
    private val drafts = FakeDraftRepository()
    private val navigator = Navigator(NavigationState(CreateKey, listOf(listOf(CreateKey))))

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `the Zip plugin carries the Zip editor`() {
        assertEquals(ZipEditor, zipPlugin.editor)
    }

    @Test
    fun `with ZipEditor off the sheet shows no Zip option`() {
        open(flags = FakeReleaseFlags())
        rule.onNodeWithTag(NEW).performClick()

        rule.onNodeWithTag("$GAME${QueensDefinition.id.value}").assertExists()
        rule.onNodeWithTag("$GAME${zip.value}").assertDoesNotExist()
    }

    @Test
    fun `with ZipEditor on the option opens the Zip editor and one stroke changes the verdict pill`() {
        open(flags = FakeReleaseFlags(ReleaseFlag.ZipEditor))
        rule.onNodeWithTag(NEW).performClick()
        rule.onNodeWithTag("$GAME${zip.value}").performClick()
        rule.onNodeWithTag("$SIZE${ZipEditor.variants.first().value}").performClick()

        rule.waitUntil(WAIT_MS) { pill() == "Incomplete" }
        rule.onNodeWithTag(BOARD).performTouchInput {
            val side = 5
            fun center(cell: Int) = Offset((cell % side + 0.5f) * width / side, (cell / side + 0.5f) * height / side)
            val snake = (0 until side).flatMap { row -> (0 until side).map { col -> row * side + if (row % 2 == 0) col else side - 1 - col } }
            down(center(snake.first()))
            snake.drop(1).forEach { moveTo(center(it)) }
            up()
        }

        rule.waitUntil(WAIT_MS) { pill() == "Multiple" }
    }

    private fun pill(): String? =
        rule.onAllNodesWithTag(PILL).fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(SemanticsProperties.StateDescription)

    private fun open(flags: ReleaseFlags) {
        val games = GameRegistry(listOf(queensPlugin, zipPlugin))
        val editors = GameEditorRegistry(listOf(QueensEditorUi, ZipEditorUi))
        var next = 0
        val createDraft = CreateDraft(games, drafts, newId = { "draft-${++next}" }, clock = { NOW })
        // One stored draft keeps the draft list, and its New puzzle button, on screen.
        runBlocking { createDraft(QueensDefinition.id, QueensEditor.variants.first()) }
        startKoin {
            modules(module { single { flags } })
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
                            CreateDraftCopy(drafts, newId = { "copy-2" }, clock = { NOW }),
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
                            idTokens = object : GoogleIdTokenProvider {
                                override suspend fun idToken(activityContext: android.content.Context): GoogleIdToken =
                                    GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.NO_CREDENTIAL)
                            },
                            updatePublishState = UpdatePublishState(FakeSharedPuzzleRepository(), FakePublishedPuzzleRepository()),
                        )
                    }
                },
            )
        }
        rule.setContent {
            PuzzleBoxTheme {
                val entries = remember {
                    entryProvider<NavKey> {
                        createEntry(navigator)
                        editorEntry(navigator, PlayLayout.SinglePane)
                    }
                }
                entries(navigator.state.currentStack.last()).Content()
            }
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
        const val WAIT_MS = 10_000L
        const val NEW = "create.new"
        const val GAME = "create.new.game."
        const val SIZE = "create.new.size."
        const val PILL = "create.editor.pill"
        const val BOARD = "zip.editor.board"
    }
}
