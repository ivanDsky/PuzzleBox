package com.ivandsky.puzzlebox.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A codename is "Adjective Noun", repeatable from a seed, and never a title already taken. */
class ComputeCodenameTest {

    private val codename = ComputeCodename()

    @Test
    fun `a codename is two capitalised words within the title cap`() {
        val random = Random(1)
        repeat(200) {
            val name = codename(random, emptySet())

            assertTrue(name, Regex("[A-Z][a-z]+ [A-Z][a-z]+").matches(name))
            assertTrue(name.length <= 60)
        }
    }

    @Test
    fun `the same seed gives the same codename`() {
        assertEquals(codename(Random(42), emptySet()), codename(Random(42), emptySet()))
    }

    @Test
    fun `different seeds give different codenames`() {
        val names = (0..20).map { codename(Random(it), emptySet()) }.toSet()

        assertTrue(names.size > 1)
    }

    @Test
    fun `a taken codename is skipped`() {
        val first = codename(Random(7), emptySet())

        val next = codename(Random(7), setOf(first))

        assertNotEquals(first, next)
        assertTrue(Regex("[A-Z][a-z]+ [A-Z][a-z]+").matches(next))
    }

    @Test
    fun `with every combination taken a number suffix keeps it free`() {
        val taken = mutableSetOf<String>()
        var name = codename(Random(3), taken)
        while (!name.endsWith(" 2")) {
            taken += name
            name = codename(Random(3), taken)
        }

        assertTrue(name !in taken)
        assertEquals(1600, taken.size)
    }
}
