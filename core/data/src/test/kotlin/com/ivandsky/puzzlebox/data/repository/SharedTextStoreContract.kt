package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.testing.repository.FakeSharedTextStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Behavior every [SharedTextStore] adapter must pass. Subclasses supply a fresh, empty
 * [repository]; [SharedTextStoreContractTest] runs it on the in-memory adapter and on the
 * `:core:testing` fake, so the two cannot drift.
 */
abstract class SharedTextStoreContract {

    protected abstract val repository: SharedTextStore

    @Test
    fun `pending starts null`() = runTest {
        assertNull(repository.pending.first())
    }

    @Test
    fun `put makes pending emit the text`() = runTest {
        repository.put("a")

        assertEquals("a", repository.pending.first())
    }

    @Test
    fun `take returns the text once then null and pending returns to null`() = runTest {
        repository.put("a")

        assertEquals("a", repository.take())
        assertNull(repository.take())
        assertNull(repository.pending.first())
    }

    @Test
    fun `a second put before take replaces the first`() = runTest {
        repository.put("first")
        repository.put("second")

        assertEquals("second", repository.take())
        assertNull(repository.pending.first())
    }
}

/** Runs [SharedTextStoreContract] on [InMemorySharedTextStore] and on [FakeSharedTextStore]. */
@RunWith(Parameterized::class)
class SharedTextStoreContractTest(private val adapter: String) : SharedTextStoreContract() {

    override val repository: SharedTextStore =
        when (adapter) {
            IN_MEMORY -> InMemorySharedTextStore()
            else -> FakeSharedTextStore()
        }

    companion object {
        private const val IN_MEMORY = "in-memory"

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun adapters(): List<Array<Any>> = listOf(arrayOf(IN_MEMORY), arrayOf("fake"))
    }
}
