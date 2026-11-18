package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.qualifier.qualifier
import org.koin.dsl.koinApplication

/** A controller the Strands UI module builds hints the most constrained word first. */
class StrandsFirstMoveHintTest {

    private val env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})

    // The module is a top-level val and Koin caches each singleton on its factory, so a play UI built by
    // another test would leak in. Drop those instances before and after each test.
    private lateinit var koin: Koin

    @OptIn(KoinInternalApi::class)
    private fun dropCachedSingles() = strandsUiModule.mappings.values.forEach { it.dropAll() }

    @Before
    fun startKoin() {
        dropCachedSingles()
        koin = koinApplication {
            modules(strandsUiModule)
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
    fun `the hint names the most constrained word`() {
        // TART and CAKE each have three routes left; CAKE sits on the bottom edge with fewer free letters around it.
        assertEquals("CAKE", controller().hintAfterPies())
    }
}
