package com.ivandsky.puzzlebox.play.random

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.play.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.play.domain.LoadPastDay
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomDay
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomPuzzle
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomShared
import com.ivandsky.puzzlebox.play.flagsOff
import com.ivandsky.puzzlebox.play.testDispatchers
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeHiddenPuzzles
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

/** Koin carries the preset game into the view model, and the memory is one per graph. */
class RandomPuzzleGraphTest {
    @After
    fun tearDown() = stopKoin()

    private fun graph() = startKoin {
        modules(
            playModule,
            flagsOff,
            module {
                val registry = TestGames.registry(listOf(TestGames.alpha))
                val daily = FakeDailyRepository()
                single<DispatcherProvider> { testDispatchers() }
                single {
                    LoadRandomPuzzle(
                        LoadRandomDay(daily, FakeCompletionRepository(), LoadPastDay(daily, registry), registry),
                        LoadRandomShared(ListCommunityPuzzles(FakeSharedPuzzleRepository(), FakeHiddenPuzzles(), FakeIdentityService(Identity("me", "Me", null)), { 0L })),
                    )
                }
            },
        )
    }.koin

    @Test
    fun `a game parameter is the preset`() {
        stopKoin() // tests share the JVM-global Koin context
        val vm = graph().get<RandomPuzzleViewModel> { parametersOf(GameId("zip")) }
        assertEquals(GameId("zip"), vm.state.value.game)
    }

    @Test
    fun `every view model shares the graph's one memory`() {
        stopKoin()
        val koin = graph()
        assertSame(koin.get<RandomPickMemory>(), koin.get<RandomPickMemory>())
    }
}
