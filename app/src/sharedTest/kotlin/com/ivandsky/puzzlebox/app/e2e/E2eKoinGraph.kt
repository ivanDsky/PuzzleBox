package com.ivandsky.puzzlebox.app.e2e

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.app.PuzzleBoxApplication
import com.ivandsky.puzzlebox.app.play.freshDailyForAllGames
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.LocalDate
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.module

/** The fixed "today" of every E2E run unless a row pins its own. */
val E2E_TODAY: LocalDate = LocalDate.of(2026, 11, 4)

/**
 * The one Koin graph every E2E test runs on: the production modules
 * ([PuzzleBoxApplication.appModules]) with core:testing fakes on the edges — daily
 * (every game's fixture fresh, so no LinkedIn call), records, saved plays, calendar,
 * preferences, identity, leaderboards and [releaseFlags] (none on, as in release; a run of an
 * unreleased feature passes its flag). [overrides] load last and win (Koin allows
 * overriding by default), e.g. a file-backed database for process-death rows.
 *
 * The fakes outlive [stop]/[start]: a relaunch discards the graph and every single built
 * from it, while the fakes keep what a real disk would. Shared by the JVM shell (Robolectric)
 * and the device tests (androidTest), so both runners see one graph.
 */
class E2eKoinGraph(
    today: LocalDate,
    private val overrides: Module = module {},
    private val releaseFlags: ReleaseFlags = FakeReleaseFlags(),
) {
    val daily: FakeDailyRepository = freshDailyForAllGames()
    val records = FakeCompletionRepository()
    val saved = FakeSavedPlayRepository()
    val calendar = FakeGameCalendar(today)
    // Every tutorial seen: the E2E paths are about the boards; the lessons have their own tests.
    val preferences = FakeUserPreferencesRepository(
        seen = setOf(
            com.ivandsky.puzzlebox.games.zip.ZipDefinition.id,
            com.ivandsky.puzzlebox.games.tango.TangoDefinition.id,
            com.ivandsky.puzzlebox.games.queens.QueensDefinition.id,
            com.ivandsky.puzzlebox.games.sudoku.SudokuGames.MINI.id,
            com.ivandsky.puzzlebox.games.sudoku.SudokuGames.CLASSIC.id,
            com.ivandsky.puzzlebox.games.strands.StrandsDefinition.id,
        ),
    )
    val identity = FakeIdentityService()
    val leaderboard = FakeLeaderboardRepository()

    private var started: Koin? = null

    /** The running graph; resolve production singles (e.g. the play store) through it. */
    val koin: Koin get() = checkNotNull(started) { "E2eKoinGraph is not started" }

    private val fakes = module {
        single<DailyRepository> { daily }
        single<CompletionRepository> { records }
        single<SavedPlayRepository> { saved }
        single<GameCalendar> { calendar }
        single<UserPreferencesRepository> { preferences }
        single<IdentityService> { identity }
        single<LeaderboardRepository> { leaderboard }
        single<ReleaseFlags> { releaseFlags }
    }

    /** Starts a fresh graph, replacing any graph still running. */
    fun start() {
        stopKoin()
        started = startKoin {
            androidContext(ApplicationProvider.getApplicationContext<Context>())
            modules(PuzzleBoxApplication.appModules + fakes + overrides)
        }.koin
    }

    fun stop() {
        stopKoin()
        started = null
    }
}
