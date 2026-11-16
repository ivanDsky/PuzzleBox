package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.koin.core.qualifier.qualifier
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class ZipHintModeWiringTest {

    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = StandardTestDispatcher(name = "main")
        override val io: CoroutineDispatcher = StandardTestDispatcher(name = "io")
        override val default: CoroutineDispatcher = StandardTestDispatcher(name = "default")
    }

    // Single Koin case: zipUiModule is a top-level val and Koin caches its singleton
    // across containers, so a second container in this JVM would see the first one's instance.
    @Test
    fun `with HintFirstMove on the Koin module opens Zip boards in first-move mode`() {
        val koin = koinApplication {
            modules(
                zipUiModule,
                module {
                    single<DispatcherProvider> { dispatchers }
                    single<ReleaseFlags> { FakeReleaseFlags(ReleaseFlag.HintFirstMove) }
                },
            )
        }.koin
        val controller = koin.get<GamePlayUi>(qualifier(ZipDefinition.id.value))
            .createController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), onSessionChanged = {}, onWon = {}))
            as ZipPlayController

        assertSame(dispatchers.default, (controller.hintMode as HintMode.FirstMove).compute)
    }

    @Test
    fun `with the flag off, or with no flags bound, the hint stays as it is today`() {
        val others = ReleaseFlag.entries.filter { it != ReleaseFlag.HintFirstMove }.toTypedArray()

        assertEquals(HintMode.Legacy, zipHintMode(FakeReleaseFlags(*others)) { dispatchers })
        assertEquals(HintMode.Legacy, zipHintMode(null) { dispatchers })
    }
}
