package com.ivandsky.puzzlebox.app

import android.app.Application
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.di.dataModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PuzzleBoxApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@PuzzleBoxApplication)
            modules(appModules)
        }
    }

    companion object {
        val appModules = listOf(commonModule, dataModule)
    }
}
