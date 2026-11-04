package com.ivandsky.puzzlebox.konsist.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Proves SourceTree's scope against a walk that shares no code with SourceWalker, and against the Gradle test inputs. */
class CensusTest {
    private val real = SourceTree.real
    private val root = SourceTree.repoRoot(System.getProperty("puzzlebox.repoRoot")).canonicalFile
    private val treePaths = SourceSetKind.values().flatMap { real.files(it) }.map { it.path }

    @Test
    fun treeMatchesAnIndependentWalk() {
        val walked = walk(includeKonsist = false)
        assertEquals("only in walk: ${walked - treePaths.toSet()}; only in tree: ${treePaths.toSet() - walked}", walked, treePaths.toSet())
        assertTrue(walked.isNotEmpty())
        assertEquals(walked.size, treePaths.size)
    }

    @Test
    fun everyModuleContributesAFile() {
        val withFiles = SourceSetKind.values().flatMap { real.files(it) }.map { it.module }.toSet()
        assertEquals(emptyList<Module>(), real.modules.filterNot { it in withFiles })
    }

    @Test
    fun konsistIsOutOfScopeAndNothingElseIsLostByThatExclusion() {
        assertTrue(treePaths.none { it.startsWith("konsist/") })
        val with = walk(includeKonsist = true)
        val without = walk(includeKonsist = false)
        assertTrue(with.any { it.startsWith("konsist/") })
        assertTrue(with.containsAll(without))
        assertTrue((with - without).all { it.startsWith("konsist/") })
    }

    @Test
    fun everyTreePathIsInsideTheGradleInputs() {
        val includes = globs("puzzlebox.inputIncludes").map(::antRegex)
        val excludes = globs("puzzlebox.inputExcludes").map(::antRegex)
        val outside = treePaths.filter { path -> includes.none { it.matches(path) } || excludes.any { it.matches(path) } }
        assertEquals("tree paths the Gradle inputs would not watch: $outside", emptyList<String>(), outside)
    }

    @Test
    fun antGlobsMatchZeroDirectoriesForDoubleStarSlash() {
        val sources = antRegex("**/src/**/*.kt")
        assertTrue(sources.matches("src/A.kt"))
        assertTrue(sources.matches("core/model/src/main/kotlin/A.kt"))
        assertFalse(sources.matches("core/model/source/A.kt"))
        assertTrue(antRegex("**/*.gradle.kts").matches("build.gradle.kts"))
        assertTrue(antRegex("**/*.gradle.kts").matches("games/a/build.gradle.kts"))
        assertTrue(antRegex("**/build/**").matches("build/x/A.kt"))
        assertTrue(antRegex("**/build/**").matches("a/build/A.kt"))
        assertFalse(antRegex("**/build/**").matches("a/builder/A.kt"))
        assertTrue(antRegex(".gradle/**").matches(".gradle/x"))
        assertFalse(antRegex("settings.gradle.kts").matches("x/settings.gradle.kts"))
        assertFalse(antRegex("*.kt").matches("a/B.kt"))
    }

    private fun globs(property: String): List<String> {
        val value = checkNotNull(System.getProperty(property)) { "System property $property is not set; run through the :konsist test task" }
        return value.split(',').filter { it.isNotBlank() }
    }

    // Ant semantics: a leading "**" + "/" also matches zero directories, which java.nio's "glob:" does not.
    private fun antRegex(glob: String): Regex {
        val out = StringBuilder()
        var i = 0
        while (i < glob.length) {
            when {
                glob.startsWith("**/", i) -> { out.append("(.*/)?"); i += 3 }
                glob.startsWith("**", i) -> { out.append(".*"); i += 2 }
                glob[i] == '*' -> { out.append("[^/]*"); i++ }
                glob[i] == '?' -> { out.append("[^/]"); i++ }
                else -> { out.append(Regex.escape(glob[i].toString())); i++ }
            }
        }
        return Regex(out.toString())
    }

    private fun walk(includeKonsist: Boolean): Set<String> {
        val skippedAnywhere = setOf(".git", ".gradle", "build", "generated")
        val skippedAtRoot = if (includeKonsist) setOf("build-logic") else setOf("build-logic", "konsist")
        val sourceFile = Regex("""(.*/)?src/[^/]+/(kotlin|java)/.+\.kt""")
        return root.walkTopDown()
            .onEnter { dir -> dir == root || (dir.name !in skippedAnywhere && !(dir.parentFile == root && dir.name in skippedAtRoot)) }
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .filter { sourceFile.matches(it) }
            .toSet()
    }
}
