package com.ivandsky.puzzlebox.play.domain.nextthing

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.domain.FriendPuzzleRow
import com.ivandsky.puzzlebox.play.domain.FriendStatus
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** L3: the chain asks its providers in key order and the first answer wins; a slow, failing or empty one is nothing. */
@OptIn(ExperimentalCoroutinesApi::class)
class NextThingChainTest {

    private val asked = mutableListOf<Int>()
    private val warnings = mutableListOf<Throwable>()
    private val daily = NextThing.NextDaily(beta)
    private val missed = NextThing.MissedDay(alpha, TODAY.minusDays(1))

    private inner class FakeNextThingProvider(
        override val order: Int,
        private val answer: suspend () -> NextThing?,
    ) : NextThingProvider {
        override suspend fun offer(facts: NextThingFacts): NextThing? {
            asked += order
            return answer()
        }
    }

    private fun chain(vararg providers: NextThingProvider) =
        NextThingChain(providers.toList(), failures = FailureLog { _, _, cause -> warnings += cause })

    @Test
    fun `providers are asked by key, not by their place in the list`() = runTest {
        val chain = chain(FakeNextThingProvider(30) { missed }, FakeNextThingProvider(10) { daily })

        assertEquals(daily, chain.offer(nextThingFacts()))
    }

    @Test
    fun `the first answer wins and no later provider is asked`() = runTest {
        val chain = chain(FakeNextThingProvider(10) { daily }, FakeNextThingProvider(20) { missed })

        chain.offer(nextThingFacts())

        assertEquals(listOf(10), asked)
    }

    @Test
    fun `a provider with nothing to offer is passed over`() = runTest {
        val chain = chain(FakeNextThingProvider(10) { null }, FakeNextThingProvider(20) { missed })

        assertEquals(missed, chain.offer(nextThingFacts()))
    }

    @Test
    fun `a provider that throws is passed over and its failure logged once`() = runTest {
        val chain = chain(
            FakeNextThingProvider(10) { throw IllegalStateException("board read failed") },
            FakeNextThingProvider(20) { missed },
        )

        assertEquals(missed, chain.offer(nextThingFacts()))
        // Compared by message: the timeout boundary may hand over a stack-recovered copy of the exception.
        assertEquals(listOf("board read failed"), warnings.map { it.message })
    }

    @Test
    fun `a provider slower than two seconds is passed over at two seconds`() = runTest {
        val chain = chain(
            FakeNextThingProvider(10) {
                delay(10_000)
                daily
            },
            FakeNextThingProvider(20) { missed },
        )

        assertEquals(missed, chain.offer(nextThingFacts()))
        assertEquals(2_000L, currentTime)
    }

    @Test
    fun `a provider that answers inside two seconds is waited for`() = runTest {
        val chain = chain(
            FakeNextThingProvider(10) {
                delay(1_900)
                daily
            },
            FakeNextThingProvider(20) { missed },
        )

        assertEquals(daily, chain.offer(nextThingFacts()))
    }

    @Test
    fun `a provider whose own work was cancelled is passed over`() = runTest {
        val chain = chain(
            FakeNextThingProvider(10) { throw CancellationException("its board read was cancelled") },
            FakeNextThingProvider(20) { missed },
        )

        assertEquals(missed, chain.offer(nextThingFacts()))
    }

    @Test
    fun `leaving while a provider is asked stops the chain`() = runTest {
        val chain = chain(FakeNextThingProvider(10) { awaitCancellation() }, FakeNextThingProvider(20) { missed })
        val job = launch { chain.offer(nextThingFacts()) }
        runCurrent()

        job.cancel()
        advanceUntilIdle()

        assertEquals(listOf(10), asked)
    }

    @Test
    fun `a play again gets no next thing and asks nobody`() = runTest {
        val chain = chain(FakeNextThingProvider(10) { daily })

        assertNull(chain.offer(nextThingFacts(playAgain = true)))
        assertEquals(emptyList<Int>(), asked)
    }

    @Test
    fun `a practice puzzle gets no next thing and asks nobody`() = runTest {
        val chain = chain(FakeNextThingProvider(10) { daily })

        assertNull(chain.offer(nextThingFacts(practice = true)))
        assertEquals(emptyList<Int>(), asked)
    }

    private val community = NextThing.CommunityPuzzle(ShareCode.parse("P4STED")!!, alpha, "Noor")
    private val withCommunity = (NextThingProviders + FakeNextThingProvider(45) { community }).toTypedArray()

    @Test
    fun `a provider injected with key 45 is asked before done for today`() = runTest {
        assertEquals(community, chain(*withCommunity).offer(nextThingFacts()))
    }

    @Test
    fun `a provider injected with key 45 is asked after the new friend puzzle`() = runTest {
        val code = ShareCode.parse("K7Q2MX")!!
        val facts = nextThingFacts(friendPuzzles = listOf(FriendPuzzleRow(code, alpha, "Knots", "Maya", FriendStatus.New)))

        assertEquals(NextThing.FriendPuzzle(code, alpha, "Maya"), chain(*withCommunity).offer(facts))
    }
}
