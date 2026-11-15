package com.ivandsky.puzzlebox.domain

import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SeededOnceTest {

    @Test
    fun `a first collection emits the seed then the real values`() = runTest {
        flowOf(1, 2).seededOnce(seed = 0) { "ann" }.test {
            assertEquals(listOf(0, 1, 2), listOf(awaitItem(), awaitItem(), awaitItem()))
            awaitComplete()
        }
    }

    @Test
    fun `a re-collection starts from the held value, never the seed`() = runTest {
        val source = MutableSharedFlow<Int>()
        val flow = source.seededOnce(seed = 0) { "ann" }
        flow.test {
            assertEquals(0, awaitItem())
            source.emit(7)
            assertEquals(7, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        flow.test {
            assertEquals(7, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `another account starts from the seed, not the first account's value`() = runTest {
        var account = "ann"
        val source = MutableSharedFlow<Int>()
        val flow = source.seededOnce(seed = 0) { account }
        flow.test {
            awaitItem()
            source.emit(7)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        account = "bob"
        flow.test {
            assertEquals(0, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
