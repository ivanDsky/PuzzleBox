package com.ivandsky.puzzlebox.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.data.repository.ZipDailyEntry
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.repository.ZipDailyRepository
import com.ivandsky.puzzlebox.data.testing.FakeIdentityService
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.Puzzle
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Route-level regression (controller device walk 2026-11-01): HomeRoute must observe the
 * ZipDailyRepository store (what Play and History actually write) — the teaser showed
 * "No games yet" forever while days existed, because it watched the unfed legacy table.
 * The fake is local because the shared FakeZipDailyRepository lives in other modules'
 * test source sets, which :feature:home cannot import.
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

    @Before
    fun resetKoin() {
        stopKoin()
    }

    @After
    fun tearDownKoin() {
        stopKoin()
    }

    private fun setContent(repo: FakeZipRepo) {
        val fakeIdentity = FakeIdentityService()
        startKoin { modules(module { single<ZipDailyRepository> { repo }; single<IdentityService> { fakeIdentity } }) }
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
        setContent(repo)

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
}
