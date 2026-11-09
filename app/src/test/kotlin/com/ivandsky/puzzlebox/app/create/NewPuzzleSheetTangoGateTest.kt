package com.ivandsky.puzzlebox.app.create

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
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
import com.ivandsky.puzzlebox.create.editor.EditorViewModel
import com.ivandsky.puzzlebox.create.api.EditorKey
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
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.editor.TangoEditor
import com.ivandsky.puzzlebox.games.tango.tangoPlugin
import com.ivandsky.puzzlebox.games.tangoui.editor.TangoEditorUi
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
 * The New puzzle sheet over the real Tango editor behind [ReleaseFlag.TangoEditor]: off, the
 * sheet has no Tango option; on, it opens a Tango draft whose first placed given moves the
 * verdict pill off Incomplete.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NewPuzzleSheetTangoGateTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val tango = TangoDefinition.id
    private val drafts = FakeDraftRepository()
    private val navigator = Navigator(NavigationState(CreateKey, listOf(listOf(CreateKey))))

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `with TangoEditor off the sheet offers no Tango option`() {
        open(FakeReleaseFlags())
        rule.onNodeWithTag(NEW).performClick()

        rule.onNodeWithTag("$GAME${QueensDefinition.id.value}").assertExists()
        rule.onNodeWithTag("$GAME${tango.value}").assertDoesNotExist()
    }

    @Test
    fun `with TangoEditor on the option opens the Tango editor and one given moves the verdict`() {
        open(FakeReleaseFlags(ReleaseFlag.TangoEditor))
        rule.onNodeWithTag(NEW).performClick()
        rule.onNodeWithTag("$GAME${tango.value}").performClick()
        assertEquals(TangoEditor.variants.map { "$SIZE${it.value}" }, tagsStartingWith(SIZE))
        rule.onNodeWithTag("$SIZE${TangoEditor.variants.first().value}").performClick()

        rule.waitUntil(TIMEOUT_MS) { tagsStartingWith(BOARD).isNotEmpty() }
        rule.waitUntil(TIMEOUT_MS) { pillState() == INCOMPLETE }

        rule.onNodeWithTag(BOARD).performTouchInput { click(Offset(width / 12f, height / 12f)) }
        rule.waitUntil(TIMEOUT_MS) { pillState() == MULTIPLE }
    }

    private fun open(flags: ReleaseFlags) {
        val games = GameRegistry(listOf(queensPlugin, tangoPlugin))
        var next = 0
        val createDraft = CreateDraft(games, drafts, newId = { "draft-${++next}" }, clock = { NOW })
        // One stored draft keeps the draft list, and its New puzzle button, on screen.
        runBlocking { createDraft(QueensDefinition.id, QueensEditor.variants.first()) }
        val editors = GameEditorRegistry(listOf(QueensEditorUi, TangoEditorUi))
        val published = FakePublishedPuzzleRepository()
        val identity = FakeIdentityService(initial = null)
        startKoin {
            modules(
                module {
                    single { flags }
                    viewModel {
                        CreateViewModel(
                            games,
                            editors,
                            GamePlayRegistry(emptyList()),
                            drafts,
                            published,
                            createDraft,
                            CreateDraftCopy(drafts, newId = { "copy-1" }, clock = { NOW }),
                            DefaultDispatcherProvider(),
                            sharedText = FakeSharedTextStore(),
                            syncSolves = SyncSolvesCounts(published, FakeCustomBoardRepository(), clock = { NOW }),
                            releaseFlags = get(),
                            clock = { NOW },
                        )
                    }
                    viewModel { (key: EditorKey) ->
                        EditorViewModel(
                            key, drafts, games, editors, GamePlayRegistry(emptyList()),
                            CreateDraftCopy(drafts, newId = { "copy-1" }, clock = { NOW }),
                            DefaultDispatcherProvider(),
                            saves = CoroutineScope(SupervisorJob() + Dispatchers.Main),
                            clock = { NOW },
                            createShared = CreateSharedPuzzle(drafts, FakeSharedPuzzleRepository(), published, identity, ValidatePublish(), Random(0), { Instant.EPOCH }),
                            publishedPuzzles = published,
                            identity = identity,
                            idTokens = noGoogleAccount,
                            updatePublishState = UpdatePublishState(FakeSharedPuzzleRepository(), published),
                        )
                    }
                },
            )
        }
        val entries = entryProvider<NavKey> {
            createEntry(navigator)
            editorEntry(navigator, PlayLayout.SinglePane)
        }
        rule.setContent {
            PuzzleBoxTheme { entries(navigator.state.currentStack.last()).Content() }
        }
    }

    private fun pillState(): String? =
        rule.onNodeWithTag(PILL).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    private fun tagsStartingWith(prefix: String): List<String> =
        rule.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher("test tag starts with $prefix") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
            },
            useUnmergedTree = true,
        ).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }

    private val noGoogleAccount = object : GoogleIdTokenProvider {
        override suspend fun idToken(activityContext: Context): GoogleIdToken =
            GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.NO_CREDENTIAL)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
        const val NEW = "create.new"
        const val GAME = "create.new.game."
        const val SIZE = "create.new.size."
        const val BOARD = "tango.editor.board"
        const val PILL = "create.editor.pill"
        const val INCOMPLETE = "Incomplete"
        const val MULTIPLE = "Multiple"
        const val TIMEOUT_MS = 10_000L
    }
}
