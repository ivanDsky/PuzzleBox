package com.ivandsky.puzzlebox.create

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.navigation.createEntry
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.domain.SyncSolvesCounts
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedTextStore
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
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
 * The Create root's New puzzle sheet over stand-in games (spec A1, A5): it lists one row
 * per game with a registered editor, in registration order, each with its glyph tile, its
 * default size and its one-line rule when one is written; a picked game lists exactly
 * its editor's variants, in order; picking a size stores that draft and pushes its Studio.
 * A game with a single size (`solo`) has no sizes step: its row stores that draft directly.
 * `beta` ships without an editor and `gamma` has an editor but no editor UI: neither is
 * offered. One stored draft keeps the list (and its New puzzle button) on screen. The real
 * Queens sizes and labels are the app's NewPuzzleSheetQueensTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewPuzzleSheetTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val queens = GameId("queens")
    private val alpha = TestGames.alpha
    private val beta = TestGames.beta
    private val gamma = GameId("gamma")
    private val solo = GameId("solo")

    private val queensSizes = (4..12).map { VariantId("queens/$it") }
    private val alphaSizes = listOf(VariantId("alpha/small"), VariantId("alpha/large"))
    private val soloSize = VariantId("solo/only")

    private val drafts = FakeDraftRepository()
    private val navigator = Navigator(NavigationState(CreateKey, listOf(listOf(CreateKey))))

    @Before
    fun setUp() {
        val games = GameRegistry(
            listOf(
                withEditor(queens, FakeGameEditor(queens, queensSizes)),
                TestGames.plugin(beta),
                withEditor(alpha, FakeGameEditor(alpha, alphaSizes)),
                withEditor(gamma, FakeGameEditor(gamma, alphaSizes)),
                withEditor(solo, FakeGameEditor(solo, listOf(soloSize))),
            ),
        )
        val editors = GameEditorRegistry(
            listOf(FakeGameEditorUi(queens), FakeGameEditorUi(alpha), FakeGameEditorUi(solo)),
        )
        var next = 0
        val createDraft = CreateDraft(games, drafts, newId = { "draft-${++next}" }, clock = { NOW })
        runBlocking { drafts.upsert(seed()) }
        startKoin {
            modules(module { single<ReleaseFlags> { FakeReleaseFlags() } })
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
                },
            )
        }
        composeRule.setContent {
            PuzzleBoxTheme {
                entryProvider<NavKey> { createEntry(navigator) }(CreateKey).Content()
            }
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `the sheet lists the games with an editor, then the picked game's sizes in order`() {
        composeRule.onNodeWithTag(NEW).performClick()
        composeRule.onNodeWithTag(SHEET).assertExists()

        assertEquals(listOf(queens, alpha, solo).map { "$GAME${it.value}" }, tagsStartingWith(GAME))
        assertEquals(listOf(queens, alpha, solo).map { "$GLYPH${it.value}" }, tagsStartingWith(GLYPH))
        assertEquals(listOf(queens, alpha, solo).map { "$DEFAULT${it.value}" }, tagsStartingWith(DEFAULT))
        // Only queens has a written rule line; a game without one shows none.
        assertEquals(listOf("$RULE${queens.value}"), tagsStartingWith(RULE))
        assertEquals(emptyList<String>(), tagsStartingWith(SIZE))

        composeRule.onNodeWithTag("$GAME${alpha.value}").performClick()
        assertEquals(alphaSizes.map { "$SIZE${it.value}" }, tagsStartingWith(SIZE))
        assertEquals(emptyList<String>(), tagsStartingWith(GAME))

        composeRule.onNodeWithTag(BACK).performClick()
        assertEquals(listOf(queens, alpha, solo).map { "$GAME${it.value}" }, tagsStartingWith(GAME))
        assertEquals(emptyList<String>(), tagsStartingWith(SIZE))
    }

    @Test
    fun `a game with one size has no size step, one tap stores its draft and opens its Studio`() {
        composeRule.onNodeWithTag(NEW).performClick()
        composeRule.onNodeWithTag("$GAME${solo.value}").performClick()

        composeRule.waitUntil { drafts.rows.containsKey("draft-1") }
        assertEquals(emptyList<String>(), tagsStartingWith(SIZE))
        assertEquals(emptyList<String>(), tagsStartingWith(BACK))
        val draft = drafts.rows.getValue("draft-1")
        assertEquals(solo, draft.gameId)
        assertEquals(soloSize, draft.variant)
        composeRule.waitForIdle()
        assertEquals(listOf(CreateKey, EditorKey("draft-1")), navigator.state.currentStack)
        composeRule.onAllNodesWithTag(SHEET).fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }

    @Test
    fun `picking a size stores that draft and opens its Studio`() {
        composeRule.onNodeWithTag(NEW).performClick()
        composeRule.onNodeWithTag("$GAME${queens.value}").performClick()
        composeRule.onNodeWithTag("${SIZE}queens/8").performScrollTo().performClick()

        composeRule.waitUntil { drafts.rows.containsKey("draft-1") }
        val draft = drafts.rows.getValue("draft-1")
        assertEquals(queens, draft.gameId)
        assertEquals(VariantId("queens/8"), draft.variant)
        composeRule.waitForIdle()
        assertEquals(listOf(CreateKey, EditorKey("draft-1")), navigator.state.currentStack)
        composeRule.onAllNodesWithTag(SHEET).fetchSemanticsNodes().let { assertEquals(0, it.size) }
    }

    @Test
    fun `two quick size taps store one draft and open one Studio`() {
        composeRule.onNodeWithTag(NEW).performClick()
        composeRule.onNodeWithTag("$GAME${queens.value}").performClick()
        composeRule.onNodeWithTag("${SIZE}queens/9").performScrollTo()
        // Both taps land while the sheet is still sliding away.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("${SIZE}queens/8").performClick()
        composeRule.onNodeWithTag("${SIZE}queens/9").performClick()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertEquals(setOf("seed", "draft-1"), drafts.rows.keys)
        assertEquals(VariantId("queens/8"), drafts.rows.getValue("draft-1").variant)
        assertEquals(listOf(CreateKey, EditorKey("draft-1")), navigator.state.currentStack)
    }

    private fun seed(): Draft = Draft(
        id = "seed",
        gameId = alpha,
        variant = alphaSizes.first(),
        draftFormatVersion = 1,
        blob = alphaSizes.first().value.encodeToByteArray(),
        title = "Seed",
        updatedAt = NOW,
        testSolve = null,
        reservedCode = null,
        imported = false,
        authorName = "",
        note = "",
    )

    private fun tagsStartingWith(prefix: String): List<String> =
        composeRule.onAllNodes(
            SemanticsMatcher("test tag starts with $prefix") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
            },
            useUnmergedTree = true,
        ).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }

    /** A blank board is its variant; nothing is ever analysed here. */
    private class FakeGameEditor(
        override val gameId: GameId,
        override val variants: List<VariantId>,
    ) : GameEditor<VariantId> {
        override val draftFormatVersion: Int = 1
        override fun newDraft(variant: VariantId): VariantId = variant
        override fun variantOf(draft: VariantId): VariantId = draft
        override fun encodeDraft(state: DraftState<VariantId>): ByteArray = state.draft.value.encodeToByteArray()
        override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<VariantId>> =
            DecodeResult.Ok(DraftState(VariantId(bytes.decodeToString())))
        override fun analyze(draft: VariantId, budget: Budget): Verdict =
            Verdict.Incomplete(IncompleteReason("fake", 0))
    }

    /** Labels a size by its variant id; the sheet never opens a board. */
    private class FakeGameEditorUi(override val gameId: GameId) : GameEditorUi {
        override fun sizeLabel(variant: VariantId): String = variant.value
        override fun createController(
            initial: EditorDraft<*>,
            onEdit: (EditorDraft<*>) -> Unit,
        ): GameEditorController = error("the sheet never opens a board")

        @Composable
        override fun Board(controller: GameEditorController, verdict: Verdict, layout: PlayLayout, modifier: Modifier) = Unit
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
        const val NEW = "create.new"
        const val SHEET = "create.new.sheet"
        const val BACK = "create.new.back"
        const val GAME = "create.new.game."
        const val SIZE = "create.new.size."
        const val GLYPH = "create.new.glyph."
        const val RULE = "create.new.rule."
        const val DEFAULT = "create.new.default."

        fun withEditor(game: GameId, editor: GameEditor<*>): GamePlugin<*, *, *> {
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
    }
}
