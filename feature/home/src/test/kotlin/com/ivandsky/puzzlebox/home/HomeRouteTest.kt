package com.ivandsky.puzzlebox.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.ZipDailyEntry
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.data.repository.ZipDailyRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.annotation.Config

/**
 * Route-level regression (controller device walk 2026-11-01; fix round 5, defect C):
 * HomeRoute must observe the GAME-AGNOSTIC merged history — every game's completion
 * records UNION the Zip captured days. The teaser first showed "No games yet" forever
 * watching an unfed legacy table; after the Zip fix it still dropped every non-Zip
 * record. The fakes are local because the shared fakes live in other modules' test
 * source sets, which :feature:home cannot import.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeZipRepo(history: List<ZipDailyEntry> = emptyList()) : ZipDailyRepository {
        val flow = MutableStateFlow(history)

        override suspend fun today() =
            error("the home teaser is cache-only (A8) — it must never call today()")

        override suspend fun cached(day: LocalDate): Puzzle? = null

        override fun observeHistory(): Flow<List<ZipDailyEntry>> = flow
    }

    private class FakeCompletions(records: List<CompletionRecord> = emptyList()) :
        CompletionRepository {
        private val flow = MutableStateFlow(records)
        override suspend fun onCompleted(record: CompletionRecord) {
            flow.value = flow.value + record
        }
        override fun observeRecords(): Flow<List<CompletionRecord>> = flow
        override fun observeReplays(puzzleRef: PuzzleRef): Flow<List<CompletionRecord>> =
            flow.map { rows -> rows.filter { it.puzzleRef == puzzleRef } }
    }

    /** A ref shape Queens authors — local because :feature:home has no games:queens dep. */
    private data class QueensRef(
        override val date: LocalDate,
        override val puzzleNumber: Int?,
    ) : PuzzleRef {
        override val gameId: GameId = GameId("queens")
    }

    private fun queensRecord(day: LocalDate) = CompletionRecord(
        puzzleRef = QueensRef(day, 916),
        time = 41_000L,
        hints = 1,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
    )

    @Before
    fun resetKoin() {
        stopKoin()
    }

    @After
    fun tearDownKoin() {
        stopKoin()
    }

    private fun setContent(repo: FakeZipRepo, completions: CompletionRepository) {
        startKoin {
            modules(
                module {
                    single<ZipDailyRepository> { repo }
                    single<CompletionRepository> { completions }
                },
            )
        }
        composeRule.setContent {
            PuzzleBoxTheme {
                HomeRoute(
                    layout = HomeLayout.SinglePane,
                    themeMode = ThemeMode.SYSTEM,
                    onSetThemeMode = {},
                    onPlayToday = {},
                    onOpenProfile = {},
                )
            }
        }
    }

    @Test
    fun `teaser tracks zip daily repository emissions`() {
        val repo = FakeZipRepo()
        setContent(repo, FakeCompletions())

        // Nothing captured yet: the honest empty state.
        composeRule.onNodeWithText("No games yet").assertExists()

        // A day lands in the compete loop's store (Play fetches/History captures) —
        // the teaser must follow, formatted like the rows the user knows.
        repo.flow.value = listOf(ZipDailyEntry(LocalDate.of(2026, 10, 31), 592, "zip/n5", 0L))

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("October 31").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("October 31").assertExists()
        composeRule.onNodeWithText("No games yet").assertDoesNotExist()
    }

    @Test
    fun `a queens completion surfaces in the teaser - fix round 5 defect C`() = kotlinx.coroutines.test.runTest {
        val completions = FakeCompletions()
        setContent(FakeZipRepo(), completions)
        composeRule.onNodeWithText("No games yet").assertExists()

        // A solved Queens game writes its record (no Zip capture anywhere) — the teaser
        // must surface it; the row names the game by its id (no plugin registry in this
        // test's Koin graph, so the registry fallback applies).
        completions.onCompleted(queensRecord(LocalDate.of(2026, 11, 1)))

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("November 1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("November 1").assertExists()
        composeRule.onNodeWithText("Daily queens").assertExists()
        composeRule.onNodeWithText("No games yet").assertDoesNotExist()
    }
}
