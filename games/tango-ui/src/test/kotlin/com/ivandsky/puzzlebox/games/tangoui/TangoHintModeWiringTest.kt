package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.qualifier.qualifier
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class TangoHintModeWiringTest {
    private fun env() = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), onSessionChanged = {}, onWon = {})

    // Single Koin case: tangoUiModule is a top-level val and Koin caches its singleton
    // across containers, so a second container in this JVM would see the first one's instance.
    @Test
    fun `the Koin module resolves the play UI with Stepwise on the provider's default dispatcher and the first-move flag read per board`() {
        val dispatchers = object : DispatcherProvider {
            override val main: CoroutineDispatcher = StandardTestDispatcher(name = "main")
            override val io: CoroutineDispatcher = StandardTestDispatcher(name = "io")
            override val default: CoroutineDispatcher = StandardTestDispatcher(name = "default")
        }
        val flags = object : ReleaseFlags {
            override val enabled = MutableStateFlow(emptySet<ReleaseFlag>())
        }
        val koin = koinApplication {
            modules(
                tangoUiModule,
                module {
                    single<DispatcherProvider> { dispatchers }
                    single<ReleaseFlags> { flags }
                },
            )
        }.koin
        val ui = koin.get<GamePlayUi>(qualifier(TangoDefinition.id.value))

        val flagOff = ui.createController(env()) as TangoPlayController
        flags.enabled.value = setOf(ReleaseFlag.HintFirstMove)
        val flagOn = ui.createController(env()) as TangoPlayController

        assertSame(dispatchers.default, flagOff.hintMode.compute)
        assertSame(dispatchers.default, flagOn.hintMode.compute)
        // The flag is read when a board opens: the next board takes it, the open one keeps its order.
        assertFalse(flagOff.hintMode.firstMove)
        assertTrue(flagOn.hintMode.firstMove)
    }
}
