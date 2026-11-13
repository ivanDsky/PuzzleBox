package com.ivandsky.puzzlebox.app.create

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.app.DrainTestApplication
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
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.domain.CreateSharedPuzzle
import com.ivandsky.puzzlebox.domain.SyncSolvesCounts
import com.ivandsky.puzzlebox.domain.UpdatePublishState
import com.ivandsky.puzzlebox.domain.ValidatePublish
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import com.ivandsky.puzzlebox.games.queensui.editor.QueensEditorUi
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftPath
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.games.strands.strandsPlugin
import com.ivandsky.puzzlebox.games.strandsui.editor.StrandsEditorUi
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
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
 * The New puzzle sheet offers Strands with no flag on, opens the Strands editor on a new
 * board, and lifting the last path of a board that needs only it moves the verdict pill off Incomplete.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w420dp-h900dp-420dpi")
class NewPuzzleSheetStrandsGateTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val drafts = FakeDraftRepository()
    private val navigator = Navigator(NavigationState(CreateKey, listOf(listOf(CreateKey))))

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `Strands is offered and opens the Strands editor`() {
        open(FakeReleaseFlags())
        rule.onNodeWithTag(NEW).performClick()
        rule.onNodeWithTag("$GAME${StrandsDefinition.id.value}").assertExists()
        // Strands has one size: its row opens the editor, with no size step between.
        rule.onNodeWithTag("$GAME${StrandsDefinition.id.value}").performClick()

        rule.waitUntil(WAIT_MS) { count(BOARD) > 0 }
        rule.onNodeWithTag("$SIZE${StrandsDefinition.VARIANT.value}").assertDoesNotExist()
        rule.onNodeWithTag(PILL).assertExists()
    }

    @Test
    fun `one lifted path settles the verdict pill`() {
        val draftId = "strands-1"
        runBlocking { drafts.upsert(storedRow(draftId, almostDone())) }
        navigator.pushOnce(EditorKey(draftId))
        open(FakeReleaseFlags())
        rule.waitUntil(WAIT_MS) { count(BOARD) > 0 }
        rule.waitUntil(WAIT_MS) { pill() == INCOMPLETE }

        // The lift selects the new path, so the word typed next names it with no extra tap.
        val last = StrandsDraft.ROWS - 1
        (0 until StrandsDraft.COLS).forEach { rule.onNodeWithTag("$CELL$last.$it").performClick() }
        rule.onNodeWithTag("$CELL$last.${StrandsDraft.COLS - 1}").performClick()
        rule.waitUntil(WAIT_MS) { !rule.onNodeWithTag(WORD).fetchSemanticsNode().config.contains(SemanticsProperties.Disabled) }
        rule.onNodeWithTag(WORD).performTextInput(LAST_WORD)

        rule.waitUntil(WAIT_MS) { pill() != INCOMPLETE && pill() != CHECKING }
        assertNotEquals(INCOMPLETE, pill())
    }

    private fun pill(): String? =
        rule.onNodeWithTag(PILL, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    private fun count(tag: String): Int =
        rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun open(flags: ReleaseFlags) {
        val games = GameRegistry(listOf(queensPlugin, strandsPlugin))
        val editors = GameEditorRegistry(listOf(QueensEditorUi, StrandsEditorUi))
        var next = 0
        val createDraft = CreateDraft(games, drafts, newId = { "draft-${++next}" }, clock = { NOW })
        // One stored Queens draft keeps the draft list, and its New puzzle button, on screen.
        runBlocking { createDraft(QueensDefinition.id, QueensEditor.variants.first()) }
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
                            releaseFlags = get(),
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
                            idTokens = noGoogleAccount,
                            updatePublishState = UpdatePublishState(shared = FakeSharedPuzzleRepository(), published = FakePublishedPuzzleRepository()),
                            preferences = FakeUserPreferencesRepository(),
                        )
                    }
                },
            )
        }
        val provider = entryProvider<NavKey> {
            createEntry(navigator)
            editorEntry(navigator, PlayLayout.SinglePane)
        }
        rule.setContent {
            PuzzleBoxTheme { provider(navigator.state.currentStack.last()).Content() }
        }
    }

    /** Every row but the last traced and named; the last row's cells blank and untraced. */
    private fun almostDone(): StrandsDraft {
        val rows = ROW_WORDS
        val traced = rows.indices.map { row -> StrandsDraftPath(List(StrandsDraft.COLS) { row * StrandsDraft.COLS + it }, rows[row]) }
        val blank = StrandsDraft.BLANK.toString().repeat(StrandsDraft.COLS)
        return StrandsDraft(rows.joinToString("") + blank, "", traced, spangram = 0)
    }

    private fun storedRow(id: String, board: StrandsDraft) = Draft(
        id = id,
        gameId = StrandsDefinition.id,
        variant = StrandsDefinition.VARIANT,
        draftFormatVersion = StrandsEditor.draftFormatVersion,
        blob = StrandsEditor.encodeDraft(DraftState(board)),
        title = "Untitled 1",
        updatedAt = NOW,
        testSolve = null,
        reservedCode = null,
        imported = false,
        authorName = "",
        note = "",
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
        val ROW_WORDS = listOf("ABCDEF", "GHIJKL", "MNOPQR", "STUVWX", "YZABCD", "EFGHIJ", "KLMNOP")
        const val LAST_WORD = "QRSTUV"
        const val WAIT_MS = 10_000L
        const val NEW = "create.new"
        const val GAME = "create.new.game."
        const val SIZE = "create.new.size."
        const val PILL = "create.editor.pill"
        const val BOARD = "strands.editor.board"
        const val WORD = "strands.editor.word"
        const val CELL = "strands.cell."
        const val INCOMPLETE = "Incomplete"
        const val CHECKING = "Checking"

        val noGoogleAccount: GoogleIdTokenProvider = object : GoogleIdTokenProvider {
            override suspend fun idToken(activityContext: Context): GoogleIdToken =
                GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.NO_CREDENTIAL)
        }
    }
}
