package com.ivandsky.puzzlebox.konsist.core

import com.lemonappdev.konsist.api.Konsist
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Naming convention for rule tests (ADR-0009). Deliberately bypasses SourceTree: it inspects konsist's own sources,
 * not the project under test, so it is not a rule and uses plain JUnit asserts.
 */
class TestNamingTest {
    private val idPrefix = Regex("""^(S|P)\d+[A-Z]""")

    private val testClasses = Konsist
        .scopeFromDirectory("konsist/src/test/kotlin/com/ivandsky/puzzlebox/konsist/rules")
        .classes()
        .filter { it.name.endsWith("Test") }

    @Test
    fun `rules package has test classes`() {
        assertTrue("no *Test classes found in the rules package", testClasses.isNotEmpty())
    }

    @Test
    fun `every rule test class and test function starts with its rule ID`() {
        val problems = testClasses.flatMap { cls ->
            val id = Regex("""^(?:S|P)\d+""").find(cls.name)?.value
                ?: return@flatMap listOf("${cls.name}: class name must start with a rule ID like S2 or P5")
            if (!idPrefix.containsMatchIn(cls.name)) return@flatMap listOf("${cls.name}: class name must start with a rule ID like S2 or P5")
            cls.functions().filter { fn -> fn.annotations.any { it.name == "Test" } && !fn.name.startsWith(id) }
                .map { "${cls.name}.${it.name}: test name must start with $id" }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
