package com.ivandsky.puzzlebox.app

import android.app.Application
import com.ivandsky.puzzlebox.app.di.appModule
import com.ivandsky.puzzlebox.app.leaderboard.LeaderboardSubmitWatcher
import com.ivandsky.puzzlebox.backend.firebase.firebaseBackendModule
import com.ivandsky.puzzlebox.common.di.commonModule
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
import com.ivandsky.puzzlebox.history.di.historyModule
import com.ivandsky.puzzlebox.home.di.homeModule
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.profile.di.profileModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PuzzleBoxApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // The submit hook (A5): record writes are the only leaderboard source; async,
        // eventual, and silent when signed out or offline (A7).
        startKoin {
            androidContext(this@PuzzleBoxApplication)
            modules(appModules)
        }.koin.get<LeaderboardSubmitWatcher>().start()
    }

    companion object {
        val appModules =
            listOf(commonModule, dataModule, firebaseBackendModule, domainModule, homeModule, historyModule, playModule, profileModule(R.string.default_web_client_id), zipModule, zipUiModule, queensModule, queensUiModule, tangoModule, tangoUiModule, miniSudokuModule, miniSudokuUiModule, appModule)
    }
}
