package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.play.domain.ObserveResultInsights
import com.ivandsky.puzzlebox.domain.nextthing.ObserveResultNextThing
import com.ivandsky.puzzlebox.play.flags.PlayFlag
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Characterization: on the real app graph, [PlayFlag.ResultHeroPolish] alone decides Result's slow tail
 * ([ObserveResultInsights]) and day total ([ObserveResultNextThing]), wherever those use cases are wired. The
 * Booleans are private constructor fields, so they are read reflectively. Delete this test with the
 * ResultHeroPolish cleanup ticket.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class ResultHeroPolishWiringTest {

    @Test
    fun `result hero polish on turns on the slow tail and the day total`() {
        assertEquals(Wired(slowTail = true, showDayTotal = true), wiredWith(FakeReleaseFlags(PlayFlag.ResultHeroPolish)))
    }

    @Test
    fun `result hero polish off turns off the slow tail and the day total`() {
        assertEquals(Wired(slowTail = false, showDayTotal = false), wiredWith(FakeReleaseFlags()))
    }

    private data class Wired(val slowTail: Boolean, val showDayTotal: Boolean)

    private fun wiredWith(flags: ReleaseFlags): Wired {
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(
                PuzzleBoxApplication.appModules + module {
                    single<IdentityService> { FakeIdentityService() }
                    single<ReleaseFlags> { flags }
                },
            )
        }.koin
        try {
            return Wired(
                slowTail = koin.get<ObserveResultInsights>().privateBoolean("slowTail"),
                showDayTotal = koin.get<ObserveResultNextThing>().privateBoolean("showDayTotal"),
            )
        } finally {
            stopKoin()
        }
    }

    private fun Any.privateBoolean(name: String): Boolean =
        javaClass.getDeclaredField(name).apply { isAccessible = true }.getBoolean(this)
}
