package com.ivandsky.puzzlebox.create

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.domain.CreateDraft
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Create root (spec A3, A7): drafts newest edit first, the search and game filter only past
 * 8 drafts, each row's verdict chip analysed once per (id, updatedAt), and with no drafts the
 * empty state offering exactly the games with an editor UI, each at its default size.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val fake = FakeGameEditor(GameId("fake"))
    private val wideId = GameId("wide")
    private val wideSizes = (1..5).map { VariantId("wide/$it") }
    private val wide = object : GameEditor<TestDraft> by FakeGameEditor(wideId) {
        override val variants: List<VariantId> = wideSizes
    }

    private val games = GameRegistry(
        listOf(withEditor(fake.gameId, fake), TestGames.plugin(TestGames.beta), withEditor(wideId, wide)),
    )
    private val editors = GameEditorRegistry(listOf(FakeGameEditorUi(fake.gameId), FakeGameEditorUi(wideId)))
    private val drafts = FakeDraftRepository()

    private val lanes = object : DispatcherProvider {
        override val main: CoroutineDispatcher = mainRule.testDispatcher
        override val io: CoroutineDispatcher = mainRule.testDispatcher
        override val default: CoroutineDispatcher = mainRule.testDispatcher
    }

    private fun TestScope.viewModel(): CreateViewModel {
        val createDraft = CreateDraft(games, drafts, newId = { "new" }, clock = { NOW })
        val vm = CreateViewModel(games, editors, GamePlayRegistry(emptyList()), drafts, createDraft, lanes, clock = { NOW })
        backgroundScope.launch(mainRule.testDispatcher) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `rows are ordered by updatedAt, newest first`() = runTest(mainRule.testDispatcher) {
        store("old", minutesAgo = 30)
        store("newest", minutesAgo = 1)
        store("middle", minutesAgo = 10)

        val vm = viewModel()

        assertEquals(listOf("newest", "middle", "old"), vm.uiState.value.drafts.map { it.id })
    }

    @Test
    fun `the search and game filter appear only past 8 drafts`() = runTest(mainRule.testDispatcher) {
        repeat(8) { store("d$it", minutesAgo = it.toLong()) }
        val vm = viewModel()
        assertFalse(vm.uiState.value.searchVisible)

        store("d8", minutesAgo = 8)
        assertTrue(vm.uiState.value.searchVisible)
        assertEquals(listOf(fake.gameId), vm.uiState.value.filters.map { it.id })
        assertEquals(listOf(9), vm.uiState.value.filters.map { it.count })
    }

    @Test
    fun `the query and the game filter narrow the rows`() = runTest(mainRule.testDispatcher) {
        repeat(8) { store("f$it", minutesAgo = it.toLong(), title = "Fake $it") }
        store("w", minutesAgo = 20, game = wideId, title = "Wide one")
        val vm = viewModel()

        vm.onQuery("WIDE")
        assertEquals(listOf("w"), vm.uiState.value.drafts.map { it.id })

        vm.onQuery("")
        vm.onFilter(fake.gameId)
        assertEquals((0..7).map { "f$it" }, vm.uiState.value.drafts.map { it.id })
        assertEquals(fake.gameId, vm.uiState.value.filter)
    }

    @Test
    fun `each row's chip is analysed once per id and updatedAt`() = runTest(mainRule.testDispatcher) {
        store("a", minutesAgo = 5, marks = 1)
        store("b", minutesAgo = 4, marks = 2)
        val vm = viewModel()

        assertEquals(2, fake.analyzed.size)
        assertTrue(vm.uiState.value.drafts.all { it.verdict is Verdict.Unique })

        // A re-emission (a new row, a query) keeps the cached verdicts.
        store("c", minutesAgo = 3, marks = 0)
        vm.onQuery("x")
        vm.onQuery("")
        assertEquals(3, fake.analyzed.size)
        assertTrue(vm.uiState.value.drafts.single { it.id == "c" }.verdict is Verdict.Incomplete)

        // A new edit of "a" is a new (id, updatedAt): analysed once more.
        store("a", minutesAgo = 0, marks = 3)
        assertEquals(4, fake.analyzed.size)
        assertEquals(TestDraft(3), fake.analyzed.last())
        assertEquals(listOf("a", "c", "b"), vm.uiState.value.drafts.map { it.id })
    }

    @Test
    fun `with no drafts the empty state lists exactly the registered editors`() = runTest(mainRule.testDispatcher) {
        val vm = viewModel()

        assertTrue(vm.uiState.value.isEmpty)
        assertEquals(editors.ids.toList(), vm.uiState.value.games.map { it.id })

        store("a", minutesAgo = 1)
        assertFalse(vm.uiState.value.isEmpty)
    }

    @Test
    fun `each game offers its middle variant as the default size`() = runTest(mainRule.testDispatcher) {
        val vm = viewModel()

        val byId = vm.uiState.value.games.associateBy { it.id }
        assertEquals(VariantId("wide/3"), byId.getValue(wideId).defaultSize.variant)
        assertEquals(fake.variants.single(), byId.getValue(fake.gameId).defaultSize.variant)
    }

    private suspend fun store(
        id: String,
        minutesAgo: Long,
        marks: Int = 1,
        game: GameId = fake.gameId,
        title: String = "Draft $id",
    ) {
        drafts.upsert(
            Draft(
                id = id,
                gameId = game,
                variant = VariantId("${game.value}/v1"),
                draftFormatVersion = 1,
                blob = byteArrayOf(1, marks.toByte()),
                title = title,
                updatedAt = NOW - Duration.ofMinutes(minutesAgo),
                testSolve = null,
                reservedCode = null,
                imported = false,
                authorName = "",
                note = "",
            ),
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")

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
                replaySession = base.replaySession,
                rendererModel = base.rendererModel,
                editor = editor,
            )
        }
    }
}
