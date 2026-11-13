package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Spec A11/A18: every game the app registers teaches itself. The check runs over every
 * [GamePlayUi] bound in the real Koin graph (so a new game is covered the moment its
 * module lands), and a game with fewer than two lessons fails, named.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application for DiGraphTest's reason: the real one would startKoin on its own.
@Config(sdk = [34], application = DrainTestApplication::class)
class EveryGameHasTutorialTest {

    @Test
    fun `every registered game ships a tutorial of at least two steps`() {
        val koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }
        try {
            val koin = koinApp.koin
            val playUis = koin.getAll<GamePlayUi>()
            // Every registered game is in the sweep: none can slip past without a play UI.
            assertEquals(
                koin.get<GameRegistry>().let { it.all + it.unlisted }.map { it.definition.id }.toSet(),
                playUis.map { it.gameId }.toSet(),
            )
            for (ui in playUis) {
                assertTrue(
                    "game '${ui.gameId.value}' ships ${ui.tutorial.size} tutorial steps, needs at least $MIN_STEPS",
                    ui.tutorial.size >= MIN_STEPS,
                )
            }
        } finally {
            stopKoin() // close() alone leaves the JVM-global context registered for later tests
        }
    }

    private companion object {
        const val MIN_STEPS = 2
    }
}
