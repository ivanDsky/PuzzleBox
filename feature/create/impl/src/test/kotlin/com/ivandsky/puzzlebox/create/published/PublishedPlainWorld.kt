package com.ivandsky.puzzlebox.create.published

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.create.FakeGameEditor
import com.ivandsky.puzzlebox.create.FakeGameEditorUi
import com.ivandsky.puzzlebox.create.TestDraft
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.domain.UpdatePublishState
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.editor.DraftExport
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.gameui.hidden.GivensBoard
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** One of the five games as the author's page sees it: its id and the shape and look of its stand-in board. */
internal data class PlainGame(val id: GameId, val label: String, val rows: Int, val cols: Int, val tint: Color) {
    companion object {
        val ZIP = PlainGame(GameId("zip"), "Zip", 6, 6, Color(0xFF4F7CE8))
        val TANGO = PlainGame(GameId("tango"), "Tango", 6, 6, Color(0xFFE8903A))
        val QUEENS = PlainGame(GameId("queens"), "Queens", 8, 8, Color(0xFF8A5BD6))
        val SUDOKU = PlainGame(GameId("sudoku"), "Mini Sudoku", 6, 6, Color(0xFF2BA39A))
        val STRANDS = PlainGame(GameId("strands"), "Strands", 8, 6, Color(0xFFD65B9A))
        val ALL = listOf(ZIP, TANGO, QUEENS, SUDOKU, STRANDS)
    }
}

internal const val STUDIO_BOARD = "studio.board.withVerdict"
internal const val PLAIN_BOARD = PublishedPlainBoardTag
internal const val PLAIN_CONTENT = "plain.board.content"
internal const val PLAIN_LOADING = PublishedPlainBoardLoadingTag

/**
 * The author's page over fake stores for one [game]: its Studio board is a tagged stand-in that draws whatever
 * verdict it is given ([STUDIO_BOARD]); its plain board is a [GivensBoard] of its own shape ([PLAIN_CONTENT]), built
 * only from the puzzle the editor's export hands it. [withReplayBoard] false is a game that draws no such board; the
 * puzzle is called [title]. [gameSlots] false is a game with no publish fields of its own under the actions.
 */
internal class PublishedPlainWorld(
    val game: PlainGame,
    private val withReplayBoard: Boolean = true,
    private val title: String = TITLE,
    private val gameSlots: Boolean = true,
) {
    val editor = PlainEditor(game.id)
    val drafts = FakeDraftRepository()

    /** While set, the kept draft is not handed over, so the page stays loading until it completes. */
    var gate: CompletableDeferred<Unit>? = null
    private val gatedDrafts = object : DraftRepository by drafts {
        override fun observeAll() = flow {
            gate?.await()
            emitAll(drafts.observeAll())
        }
    }
    val published = FakePublishedPuzzleRepository()
    val shared = FakeSharedPuzzleRepository()

    /** Every puzzle the plain board was asked to draw, and the verdicts the Studio board was handed. */
    val givensAsked = mutableListOf<Puzzle>()
    val studioVerdicts = mutableListOf<Verdict>()

    var viewModel: PublishedPuzzleViewModel? = null
        private set

    fun start(unpublished: Boolean = false) {
        store(unpublished)
        startKoin {
            modules(
                module {
                    viewModel { (key: PublishedPuzzleKey) ->
                        PublishedPuzzleViewModel(
                            key, published, gatedDrafts, GameRegistry(listOf(pluginOf(editor))),
                            GameEditorRegistry(listOf(StudioWithVerdict(FakeGameEditorUi(game.id, gameSlots = gameSlots)))),
                            GamePlayRegistry(listOf(PlayWithGivens())),
                            CreateDraftCopy(drafts, newId = { "copy" }, clock = { NOW }),
                            DefaultDispatcherProvider(), UpdatePublishState(shared, published),
                        ).also { viewModel = it }
                    }
                },
            )
        }
    }

    val key: PublishedPuzzleKey get() = PublishedPuzzleKey(CODE.value)

    private fun store(unpublished: Boolean) = runBlocking {
        val board = editor.draft(2)
        drafts.upsert(
            Draft(
                id = "castle", gameId = board.gameId, variant = board.variant,
                draftFormatVersion = board.editor.draftFormatVersion, blob = board.encode(), title = title, updatedAt = NOW,
                testSolve = TestSolve(board.layoutKey, timeMs = 102_000, hints = 0), reservedCode = CODE.value,
                imported = false, authorName = "Ivan", note = "",
            ),
        )
        published.upsert(
            PublishedPuzzle(
                code = CODE, gameId = game.id, variant = board.variant, title = title, authorTimeMs = 102_000,
                authorHints = 0, createdAt = NOW, unpublished = unpublished, solvesCount = 3, solvesCountAt = NOW,
            ),
        )
    }

    /** The stand-in Studio board: it draws the verdict it was given, as the real ones draw their marks. */
    private inner class StudioWithVerdict(base: FakeGameEditorUi) : GameEditorUi by base {
        @Composable
        override fun Board(controller: GameEditorController, verdict: Verdict, layout: PlayLayout, modifier: Modifier) {
            studioVerdicts += verdict
            Text("Studio $verdict", modifier.testTag(STUDIO_BOARD))
        }
    }

    private inner class PlayWithGivens : GamePlayUi {
        override val gameId: GameId = game.id
        override val tutorial: List<TutorialStep> get() = emptyList()
        override val replayBoard: GameReplayBoard? = if (withReplayBoard) PlainReplayBoard() else null
        override fun createController(env: PlayEnvironment): GamePlayController = error("unused")

        @Composable
        override fun Board(controller: GamePlayController, modifier: Modifier) = Unit

        @Composable
        override fun Screen(
            controller: GamePlayController,
            status: PlayBoardStatus,
            layout: PlayLayout,
            sidePanel: (@Composable ColumnScope.() -> Unit)?,
            onRetry: () -> Unit,
            onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
            modifier: Modifier,
        ) = Unit
    }

    private inner class PlainReplayBoard : GameReplayBoard {
        override val pieces: String = "pieces"

        @Composable
        override fun Board(frame: ReplayFrame, options: ReplayBoardOptions, modifier: Modifier) = Unit

        override fun givens(puzzle: Puzzle): GivensBoard? {
            givensAsked += puzzle
            return GivensBoard(game.rows, game.cols) { modifier ->
                Box(modifier.testTag(PLAIN_CONTENT).background(game.tint), Alignment.Center) {
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.18f)))
                    Text(game.label)
                }
            }
        }
    }

    companion object {
        const val TITLE = "Sunday Queens"
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

/** The stand-in game with an export: its plain puzzle is the mark count and the solution's one number. */
internal class PlainEditor(
    gameId: GameId,
    private val base: FakeGameEditor = FakeGameEditor(gameId),
) : GameEditor<TestDraft> by base, DraftExport<TestDraft> {
    override fun toPuzzle(draft: TestDraft, solution: Solution): Puzzle =
        Puzzle(gameId, variants.first(), 1, byteArrayOf(draft.marks.toByte(), solution.values.single().toByte()))

    fun draft(marks: Int) = EditorDraft(this, DraftState(TestDraft(marks)))
}
