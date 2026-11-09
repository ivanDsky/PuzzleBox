package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.create.FakeGameEditor
import com.ivandsky.puzzlebox.create.FakeGameEditorUi
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.navigation.editorEntry
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.Instant
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

/**
 * Publish is the Studio's friend-sharing entry (ADR-0022): the action area has Test solve,
 * and Publish follows it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PublishGateTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val editor = FakeGameEditor()
    private val ui = FakeGameEditorUi()
    private val drafts = FakeDraftRepository()
    private val navigator = Navigator(NavigationState(CreateKey, listOf(listOf(CreateKey, EditorKey(DRAFT_ID)))))

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `Publish follows Test solve`() {
        open()
        composeRule.onNodeWithTag(TEST_SOLVE).assertExists()
        composeRule.onNodeWithTag(PUBLISH).assertExists()
    }

    private fun open() {
        runBlocking { drafts.upsert(storedRow(editor.draft(0).encode())) }
        val games = GameRegistry(listOf(withEditor(editor)))
        val editors = GameEditorRegistry(listOf(ui))
        startKoin {
            modules(
                module {
                    viewModel { (key: EditorKey) ->
                        val copyDraft = CreateDraftCopy(drafts, newId = { "copy-1" }, clock = { NOW })
                        EditorViewModel(key, drafts, games, editors, GamePlayRegistry(emptyList()), copyDraft, DefaultDispatcherProvider(), saves = CoroutineScope(SupervisorJob() + Dispatchers.Main), clock = { NOW }, createShared = signedOutPublishing(drafts), publishedPuzzles = FakePublishedPuzzleRepository(), identity = nobodySignedIn(), idTokens = noGoogleAccount, updatePublishState = signedOutUnpublishing(), preferences = FakeUserPreferencesRepository())
                    }
                    idleCreateRoot(drafts)
                },
            )
        }
        composeRule.setContent {
            PuzzleBoxTheme {
                entryProvider<NavKey> { editorEntry(navigator, PlayLayout.SinglePane) }(EditorKey(DRAFT_ID)).Content()
            }
        }
        composeRule.waitUntil { ui.controllers.isNotEmpty() }
        composeRule.waitForIdle()
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
        const val DRAFT_ID = "draft-1"
        const val TEST_SOLVE = "create.action.testSolve"
        const val PUBLISH = "create.action.publish"

        fun storedRow(blob: ByteArray) = Draft(
            id = DRAFT_ID,
            gameId = FakeGameEditor().gameId,
            variant = FakeGameEditor().variants.first(),
            draftFormatVersion = 1,
            blob = blob,
            title = "Untitled 1",
            updatedAt = NOW,
            testSolve = null,
            reservedCode = null,
            imported = false,
            authorName = "",
            note = "",
        )

        fun withEditor(editor: FakeGameEditor): GamePlugin<*, *, *> {
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
    }
}
