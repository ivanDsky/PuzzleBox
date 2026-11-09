package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.gameui.hint.HintModeSource
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** Spec A20: appModule's HintModeSource maps HintParity off to Legacy and on to Stepwise on the provider's default. */
class HintModeSourceBindingTest {

    private val dispatchers = TestDispatcherProvider()

    @Test
    fun `flag off resolves Legacy`() {
        assertEquals(HintMode.Legacy, modeWith(FakeReleaseFlags()))
    }

    @Test
    fun `flag on resolves Stepwise on the provider's default dispatcher`() {
        val mode = modeWith(FakeReleaseFlags(ReleaseFlag.HintParity))

        assertSame(dispatchers.default, (mode as HintMode.Stepwise).compute)
    }

    /**
     * Only HintModeSource is resolved, so none of appModule's other (lazy) singletons is built. The
     * graph is closed after: appModule is one shared Module whose single instances outlive an
     * unclosed koinApplication, so the next test would get this test's source.
     */
    private fun modeWith(flags: ReleaseFlags): HintMode {
        val seams = module {
            single<ReleaseFlags> { flags }
            single<DispatcherProvider> { dispatchers }
        }
        val app = koinApplication { modules(appModule, seams) }
        try {
            return app.koin.get<HintModeSource>().current()
        } finally {
            app.close()
        }
    }

    /** Three distinct dispatchers, so only `default` can satisfy the identity check. */
    private class TestDispatcherProvider : DispatcherProvider {
        override val main: CoroutineDispatcher = StandardTestDispatcher(name = "main")
        override val io: CoroutineDispatcher = StandardTestDispatcher(name = "io")
        override val default: CoroutineDispatcher = StandardTestDispatcher(name = "default")
    }
}
