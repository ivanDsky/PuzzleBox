package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.qualifier.qualifier
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * The `HintFirstMove` gate (nav-rethink A2, B4): the Strands UI module reads the flag when it builds a
 * controller's mode, and the mode decides which word the hint names.
 */
class StrandsHintModeWiringTest {

    private val env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})
    private val defaultDispatcher = Dispatchers.Unconfined
    private val flags = object : ReleaseFlags {
        override val enabled = MutableStateFlow(emptySet<ReleaseFlag>())
    }

    // The module is a top-level val and Koin caches each singleton on its factory, so a play UI built by
    // another test would leak in. Drop those instances before and after each test.
    private lateinit var koin: Koin

    @OptIn(KoinInternalApi::class)
    private fun dropCachedSingles() = strandsUiModule.mappings.values.forEach { it.dropAll() }

    @Before
    fun startKoin() {
        dropCachedSingles()
        koin = koinApplication {
            modules(strandsUiModule, module {
                single<ReleaseFlags> { flags }
                single<DispatcherProvider> {
                    object : DispatcherProvider {
                        override val main = Dispatchers.Default
                        override val io = Dispatchers.IO
                        override val default = defaultDispatcher
                    }
                }
            })
        }.koin
    }

    @After
    fun closeKoin() {
        koin.close()
        dropCachedSingles()
    }

    private fun controller(): StrandsPlayController {
        val ui = koin.get<GamePlayUi>(qualifier(StrandsDefinition.id.value))
        return (ui.createController(env) as StrandsPlayController).also {
            assertEquals(Adopt.Adopted, it.adoptToday(StrandsUiFixtures.envelope(), StrandsUiFixtures.ref))
        }
    }

    private fun StrandsPlayController.play(path: List<Coord>) {
        path.forEach(::tap)
        tap(path.last())
    }

    /** Finds PIES, earns one hint and presses it: the word the hint outlines. */
    private fun StrandsPlayController.hintAfterPies(): String? {
        play(StrandsUiFixtures.row(0))
        listOf("TAKE", "RAKE", "CART").forEach { play(StrandsUiFixtures.nonTheme.getValue(it)) }
        pressHint()
        return session?.state?.activeHint?.word
    }

    @Test
    fun `the flag is read for each controller - on is first move on the default dispatcher, off is legacy`() {
        assertEquals(HintMode.Legacy, controller().hintMode)

        flags.enabled.value = setOf(ReleaseFlag.HintFirstMove)
        assertEquals(HintMode.FirstMove(defaultDispatcher), controller().hintMode)

        flags.enabled.value = setOf(ReleaseFlag.BoardTokens)
        assertEquals(HintMode.Legacy, controller().hintMode)
    }

    @Test
    fun `with the flag off the hint names the first of the shortest words`() {
        // PIES found; TART and CAKE are both four letters and TART is first in the feed.
        assertEquals("TART", controller().hintAfterPies())
    }

    @Test
    fun `with the flag on the hint names the most constrained word`() {
        flags.enabled.value = setOf(ReleaseFlag.HintFirstMove)

        // TART and CAKE each have three routes left; CAKE sits on the bottom edge with fewer free letters around it.
        assertEquals("CAKE", controller().hintAfterPies())
    }
}
