package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertSame
import org.junit.Test
import org.koin.core.qualifier.qualifier
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class TangoHintModeWiringTest {
    private fun env() = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), onSessionChanged = {}, onWon = {})

    // Single Koin case: tangoUiModule is a top-level val and Koin caches its singleton
    // across containers, so a second container in this JVM would see the first one's instance.
    @Test
    fun `the Koin module resolves the play UI with Stepwise on the provider's default dispatcher`() {
        val dispatchers = object : DispatcherProvider {
            override val main: CoroutineDispatcher = StandardTestDispatcher(name = "main")
            override val io: CoroutineDispatcher = StandardTestDispatcher(name = "io")
            override val default: CoroutineDispatcher = StandardTestDispatcher(name = "default")
        }
        val koin = koinApplication {
            modules(tangoUiModule, module { single<DispatcherProvider> { dispatchers } })
        }.koin
        val controller = koin.get<GamePlayUi>(qualifier(TangoDefinition.id.value))
            .createController(env()) as TangoPlayController

        assertSame(dispatchers.default, (controller.hintMode as HintMode.Stepwise).compute)
    }
}
