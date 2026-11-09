package com.ivandsky.puzzlebox.app

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import com.ivandsky.puzzlebox.app.di.appModule
import com.ivandsky.puzzlebox.app.di.buildTypeReleaseFlagsModule
import com.ivandsky.puzzlebox.app.leaderboard.CustomBoardSubmitter
import com.ivandsky.puzzlebox.app.leaderboard.LeaderboardSubmitWatcher
import com.ivandsky.puzzlebox.backend.firebase.firebaseBackendModule
import com.ivandsky.puzzlebox.auth.di.authModule
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.compete.di.competeModule
import com.ivandsky.puzzlebox.create.di.createModule
import com.ivandsky.puzzlebox.data.di.dataModule
import com.ivandsky.puzzlebox.domain.di.domainModule
import com.ivandsky.puzzlebox.games.queens.queensModule
import com.ivandsky.puzzlebox.games.queensui.queensUiModule
import com.ivandsky.puzzlebox.games.sudoku.miniSudokuModule
import com.ivandsky.puzzlebox.games.sudokuui.miniSudokuUiModule
import com.ivandsky.puzzlebox.games.zip.zipModule
import com.ivandsky.puzzlebox.games.zipui.zipUiModule
import com.ivandsky.puzzlebox.games.tango.tangoModule
import com.ivandsky.puzzlebox.games.tangoui.tangoUiModule
import com.ivandsky.puzzlebox.games.strands.strandsModule
import com.ivandsky.puzzlebox.games.strandsui.strandsUiModule
import com.ivandsky.puzzlebox.history.di.historyModule
import com.ivandsky.puzzlebox.home.di.homeModule
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.profile.di.profileModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

// Debug-only startup hook (spec A7): the same reflective binding PuzzleBoxApp.kt's Catalog
// route loads. Release has no such class, and the FLAG_DEBUGGABLE gate keeps release off
// the reflection path entirely.
private const val DEBUG_CATALOG_BINDING = "com.ivandsky.puzzlebox.app.debug.CatalogDebugBinding"

class PuzzleBoxApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installDebugDesignVariants()
        val koin = startKoin {
            androidContext(this@PuzzleBoxApplication)
            modules(appModules)
        }.koin
        // The submit hook (A5): record writes are the only leaderboard source; async,
        // eventual, and silent when signed out or offline (A7).
        koin.get<LeaderboardSubmitWatcher>().start()
        // Friend puzzle wins: posts pending at the last run retry on every app start.
        koin.get<CustomBoardSubmitter>().start()
    }

    // Applies the saved developer design settings before the first frame.
    private fun installDebugDesignVariants() {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        try {
            Class.forName(DEBUG_CATALOG_BINDING)
                .getMethod("install", Context::class.java)
                .invoke(null, this)
        } catch (_: Throwable) {
            // No debug binding in this build: the shipped design variants stay.
        }
    }

    companion object {
        val appModules =
            listOf(commonModule, dataModule, firebaseBackendModule, domainModule, homeModule, historyModule, playModule, createModule, authModule(R.string.default_web_client_id), profileModule, competeModule, zipModule, zipUiModule, queensModule, queensUiModule, tangoModule, tangoUiModule, strandsModule, strandsUiModule, miniSudokuModule, miniSudokuUiModule, appModule, buildTypeReleaseFlagsModule) + buildTypeGameModules
    }
}
