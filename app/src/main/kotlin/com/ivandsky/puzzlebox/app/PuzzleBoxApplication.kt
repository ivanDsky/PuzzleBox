package com.ivandsky.puzzlebox.app

import android.app.Application
import com.ivandsky.puzzlebox.app.di.appModule
import com.ivandsky.puzzlebox.app.leaderboard.LeaderboardSubmitWatcher
import com.ivandsky.puzzlebox.backend.firebase.firebaseBackendModule
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.di.dataModule
import com.ivandsky.puzzlebox.games.zip.zipModule
import com.ivandsky.puzzlebox.games.zipui.zipUiModule
import com.ivandsky.puzzlebox.play.di.playModule
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
            listOf(commonModule, dataModule, firebaseBackendModule, playModule, zipModule, zipUiModule, appModule)
    }
}
