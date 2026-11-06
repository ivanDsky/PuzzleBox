package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.domain.AppBuild
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Release checks for updates on Home resume (spec A4); DiGraphTest pins the debug half. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ReleaseUpdateChecksTest {

    @Test
    fun `release graph binds an AppBuild that checks for updates - A4`() {
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }.koin
        try {
            assertTrue(koin.get<AppBuild>().checksForUpdates)
        } finally {
            stopKoin()
        }
    }
}
