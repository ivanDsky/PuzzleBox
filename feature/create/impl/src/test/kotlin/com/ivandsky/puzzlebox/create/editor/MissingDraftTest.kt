package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.create.FakeGameEditorUi
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import org.robolectric.shadows.ShadowLooper

/** A draft the Studio can't open still offers a way back to the Create root. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MissingDraftTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val drafts = FakeDraftRepository()

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `a draft that can't be opened shows Back, which leaves the Studio`() {
        startKoin {
            // Every Studio test runs with friend sharing on (ADR-0022).
            modules(module { single<ReleaseFlags> { FakeReleaseFlags(ReleaseFlag.FriendSharing) } })
            modules(
                module {
                    viewModel { (key: EditorKey) ->
                        val copyDraft = CreateDraftCopy(drafts, newId = { "copy-1" }, clock = { NOW })
                        EditorViewModel(
                            key, drafts, GameRegistry(emptyList()), GameEditorRegistry(listOf(FakeGameEditorUi())),
                            GamePlayRegistry(emptyList()), copyDraft, DefaultDispatcherProvider(),
                            saves = CoroutineScope(SupervisorJob() + Dispatchers.Main), clock = { NOW },
                            createShared = signedOutPublishing(drafts),
                            publishedPuzzles = FakePublishedPuzzleRepository(),
                            identity = nobodySignedIn(),
                            idTokens = noGoogleAccount,
                        )
                    }
                },
            )
        }
        var backs = 0
        composeRule.setContent {
            PuzzleBoxTheme {
                EditorRoute(EditorKey("gone"), PlayLayout.SinglePane, onBack = { backs++ }, onOpen = {}, onDelete = {})
            }
        }
        composeRule.waitUntil(TIMEOUT_MS) {
            ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
            composeRule.onAllNodes(hasTestTag(BACK)).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(BACK).performClick()

        assertEquals(1, backs)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
        const val BACK = "create.editor.missing.back"
    }
}
