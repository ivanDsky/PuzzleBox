package com.ivandsky.puzzlebox.create.published

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.create.FakeGameEditor
import com.ivandsky.puzzlebox.create.FakeGameEditorUi
import com.ivandsky.puzzlebox.create.GOLDEN_PHONE
import com.ivandsky.puzzlebox.create.GOLDEN_TABLET
import com.ivandsky.puzzlebox.create.TestDraft
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import com.ivandsky.puzzlebox.create.settleAndCapture
import com.ivandsky.puzzlebox.create.showGolden
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.domain.UpdatePublishState
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import java.util.concurrent.TimeUnit
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * A published puzzle's page, phone (single pane) and tablet (two panes), light and dark: live
 * with its ticket and Unpublish…, the unpublish confirm dialog, the dialog's offline line after a
 * failed confirm, and the unpublished record with its code struck. The page runs on its real
 * view model over fake stores; the board is a placeholder square. Goldens in `snapshots/published/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class PublishedPageGoldenScreenshotsPhone : PublishedPageGoldenScreenshots(PlayLayout.SinglePane, "phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class PublishedPageGoldenScreenshotsTablet : PublishedPageGoldenScreenshots(PlayLayout.TwoPane, "tablet")

abstract class PublishedPageGoldenScreenshots(private val layout: PlayLayout, private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val editor = FakeGameEditor()
    private val drafts = FakeDraftRepository()
    private val published = FakePublishedPuzzleRepository()
    private val shared = FakeSharedPuzzleRepository()

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test fun `page light`() = capture("page", dark = false)
    @Test fun `page dark`() = capture("page", dark = true)

    @Test fun `unpublish dialog light`() = capture("unpublish-dialog", dark = false) { ask() }
    @Test fun `unpublish dialog dark`() = capture("unpublish-dialog", dark = true) { ask() }

    @Test fun `unpublish dialog offline light`() = capture("unpublish-offline", dark = false) { askOffline() }
    @Test fun `unpublish dialog offline dark`() = capture("unpublish-offline", dark = true) { askOffline() }

    @Test fun `unpublished record light`() = capture("page-unpublished", dark = false, unpublished = true)
    @Test fun `unpublished record dark`() = capture("page-unpublished", dark = true, unpublished = true)

    private fun capture(name: String, dark: Boolean, unpublished: Boolean = false, then: () -> Unit = {}) {
        store(unpublished)
        startKoin {
            modules(
                module {
                    viewModel { (key: PublishedPuzzleKey) ->
                        PublishedPuzzleViewModel(
                            key, published, drafts, GameRegistry(listOf(pluginOf(editor))),
                            GameEditorRegistry(listOf(PlaceholderEditorUi(FakeGameEditorUi(editor.gameId)))),
                            GamePlayRegistry(emptyList()), CreateDraftCopy(drafts, newId = { "copy" }, clock = { NOW }),
                            DefaultDispatcherProvider(), UpdatePublishState(shared, published),
                        )
                    }
                },
            )
        }
        rule.showGolden(dark, frozen = false) { PublishedPuzzleRoute(PublishedPuzzleKey(CODE.value), layout, onBack = {}, onOpen = {}) }
        awaitTag("create.published.ticket")
        then()
        rule.settleAndCapture("published", name, device, dark)
    }

    private fun ask() {
        rule.onNodeWithTag("create.publishedPage.unpublish").performScrollTo().performClick()
        awaitTag("create.unpublish.dialog")
    }

    private fun askOffline() {
        ask()
        shared.online = false
        rule.onNodeWithTag("create.unpublish.confirm").performClick()
        awaitTag("create.unpublish.offline")
    }

    private fun store(unpublished: Boolean) = runBlocking {
        val board = editor.draft(2)
        drafts.upsert(
            Draft(
                id = "castle",
                gameId = board.gameId,
                variant = board.variant,
                draftFormatVersion = board.editor.draftFormatVersion,
                blob = board.encode(),
                title = TITLE,
                updatedAt = NOW,
                testSolve = TestSolve(board.layoutKey, timeMs = 102_000, hints = 0),
                reservedCode = CODE.value,
                imported = false,
                authorName = "Ivan",
                note = "",
            ),
        )
        published.upsert(
            PublishedPuzzle(
                code = CODE,
                gameId = editor.gameId,
                variant = board.variant,
                title = TITLE,
                authorTimeMs = 102_000,
                authorHints = 0,
                createdAt = NOW,
                unpublished = unpublished,
                solvesCount = 3,
                solvesCountAt = NOW,
            ),
        )
    }

    private fun awaitTag(tag: String) = rule.waitUntil(TIMEOUT_MS) {
        ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
        rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    /** The fake editor with the Studio previews' placeholder square for its board. */
    private class PlaceholderEditorUi(base: FakeGameEditorUi) : GameEditorUi by base {
        @Composable
        override fun Board(controller: GameEditorController, verdict: Verdict, layout: PlayLayout, modifier: Modifier) {
            Box(modifier, contentAlignment = Alignment.Center) {
                Box(Modifier.aspectRatio(1f, matchHeightConstraintsFirst = true).background(MaterialTheme.colorScheme.surfaceContainerHighest, AppShapes.small))
            }
        }
    }

    private companion object {
        const val TITLE = "Sunday Queens"
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L

        val NOW: Instant = Instant.parse("2026-11-05T10:00:00Z")
        val CODE = checkNotNull(ShareCode.parse("K7Q2MX"))

        fun pluginOf(editor: GameEditor<TestDraft>): GamePlugin<*, *, *> {
            val base = TestGames.plugin(editor.gameId)
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
    }
}
