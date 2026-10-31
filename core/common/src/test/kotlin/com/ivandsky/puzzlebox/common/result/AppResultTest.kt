package com.ivandsky.puzzlebox.common.result

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class AppResultTest {

    @Test
    fun `resultOf wraps value in Ok`() = runTest {
        assertEquals(AppResult.Ok(42), resultOf { 42 })
    }

    @Test
    fun `resultOf maps IOException to Network`() = runTest {
        val result = resultOf<String> { throw IOException("offline") }
        assertTrue(result is AppResult.Err && result.error is AppError.Network)
    }

    @Test
    fun `resultOf maps SerializationException to Parsing`() = runTest {
        val result = resultOf<String> { throw SerializationException("bad json") }
        assertTrue(result is AppResult.Err && result.error is AppError.Parsing)
    }

    @Test
    fun `resultOf maps unexpected throwable to Unknown`() = runTest {
        val result = resultOf<String> { throw IllegalStateException("boom") }
        assertTrue(result is AppResult.Err && result.error is AppError.Unknown)
    }

    @Test
    fun `resultOf rethrows cancellation`() = runTest {
        try {
            resultOf<String> { throw CancellationException("cancelled") }
            fail("cancellation must not be swallowed")
        } catch (_: CancellationException) {
            // expected
        }
    }

    @Test
    fun `onSuccess runs only on Ok`() = runTest {
        var seen = 0
        resultOf { 7 }.onSuccess { seen = it }
        assertEquals(7, seen)
        resultOf<Int> { throw IOException("x") }.onSuccess { seen = -1 }
        assertEquals(7, seen)
    }

    @Test
    fun `onError runs only on Err`() = runTest {
        var error: AppError? = null
        resultOf<Int> { throw SerializationException("x") }.onError { error = it }
        assertTrue(error is AppError.Parsing)
        resultOf { 1 }.onError { error = null }
        assertTrue(error is AppError.Parsing)
    }

    @Test
    fun `getOrNull is null on Err`() = runTest {
        assertNull(resultOf<Int> { throw IOException("x") }.getOrNull())
        assertEquals(3, resultOf { 3 }.getOrNull())
    }
}
