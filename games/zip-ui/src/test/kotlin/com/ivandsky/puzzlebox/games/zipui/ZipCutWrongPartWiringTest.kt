package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.qualifier.qualifier
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class ZipCutWrongPartWiringTest {

    // Single Koin case: zipUiModule is a top-level val and Koin caches its singleton across containers,
    // so a second container in this JVM would see the first one's instance. The one flag set is flipped instead.
    @Test
    fun `the Koin module reads CutWrongPart as each puzzle opens, and it is off by default`() {
        val on = MutableStateFlow(emptySet<ReleaseFlagKey>())
        val flags = object : ReleaseFlags {
            override val enabled = on
        }
        val koin = koinApplication { modules(zipUiModule, module { single<ReleaseFlags> { flags } }) }.koin
        val ui = koin.get<GamePlayUi>(qualifier(ZipDefinition.id.value))
        val env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), onSessionChanged = {}, onWon = {})

        assertFalse(ZipUiFlag.CutWrongPart.releaseDefault)
        assertFalse((ui.createController(env) as ZipPlayController).cutWrongPart)

        on.value = setOf(ZipUiFlag.CutWrongPart)
        assertTrue((ui.createController(env) as ZipPlayController).cutWrongPart)
    }
}
