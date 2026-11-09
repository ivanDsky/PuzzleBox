package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.gameui.hint.HintModeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.qualifier.qualifier
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class TangoHintModeWiringTest {
    private fun env() = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), onSessionChanged = {}, onWon = {})

    @Test
    fun `the mode source is invoked once per controller and the controller carries its value`() {
        val answers = ArrayDeque<HintMode>(listOf(HintMode.Stepwise(Dispatchers.Unconfined), HintMode.Legacy))
        var calls = 0
        val ui = TangoPlayUi(HintModeSource { calls++; answers.removeFirst() })

        val first = ui.createController(env()) as TangoPlayController
        assertEquals(1, calls)
        assertTrue(first.hintMode is HintMode.Stepwise)

        val second = ui.createController(env()) as TangoPlayController
        assertEquals(2, calls)
        assertSame(HintMode.Legacy, second.hintMode)
    }

    // Single Koin case: tangoUiModule is a top-level val and Koin caches its singleton
    // across containers, so a second container in this JVM would see the first one's instance.
    @Test
    fun `the Koin module resolves the play UI with the bound mode source`() {
        val koin = koinApplication {
            modules(
                tangoUiModule,
                module { single<HintModeSource> { HintModeSource { HintMode.Stepwise(Dispatchers.Unconfined) } } },
            )
        }.koin
        val controller = koin.get<GamePlayUi>(qualifier(TangoDefinition.id.value))
            .createController(env()) as TangoPlayController
        assertTrue(controller.hintMode is HintMode.Stepwise)
    }
}
