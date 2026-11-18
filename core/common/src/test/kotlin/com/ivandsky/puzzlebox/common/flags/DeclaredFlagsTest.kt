package com.ivandsky.puzzlebox.common.flags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeclaredFlagsTest {

    private class TestFlag(override val key: String) : ReleaseFlagKey {
        override val label: String = key
        override val releaseDefault: Boolean = false
    }

    private val a = TestFlag("A")
    private val b = TestFlag("B")
    private val c = TestFlag("C")

    @Test
    fun `flags follow their owner's order, then their own`() {
        val declared = DeclaredFlags(listOf(FlagSet("play", listOf(c, a)), FlagSet("compete", listOf(b))))

        assertEquals(listOf(b, c, a), declared.all)
    }

    @Test
    fun `a key two owners declare throws and names both`() {
        val error = assertThrows(IllegalStateException::class.java) {
            DeclaredFlags(listOf(FlagSet("play", listOf(a)), FlagSet("compete", listOf(TestFlag("A")))))
        }

        assertEquals("release flag key 'A' is declared by both 'compete' and 'play'", error.message)
    }
}
