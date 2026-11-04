package com.ivandsky.puzzlebox.konsist.core

import com.lemonappdev.konsist.api.Konsist
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SourceTreeTest {
    private val tree = SourceTree.fixture("_tree")

    @Test
    fun countsFilesPerKind() {
        assertEquals(
            listOf("app/src/main/kotlin/App.kt", "core/model/src/main/kotlin/Model.kt", "games/a-ui/src/main/kotlin/AUi.kt",
                "games/a/src/main/kotlin/A.kt", "games/zip-line/src/main/kotlin/ZipLine.kt"),
            tree.files(SourceSetKind.PRODUCTION).map { it.path },
        )
        assertEquals(listOf("core/model/src/test/kotlin/ModelTest.kt"), tree.files(SourceSetKind.TEST).map { it.path })
    }

    @Test
    fun filesCarryModuleSourceSetKindAndPackage() {
        val test = tree.files(SourceSetKind.TEST).single()
        assertEquals(":core:model", test.module.path)
        assertEquals("test", test.sourceSet)
        assertEquals(SourceSetKind.TEST, test.kind)
        assertEquals("com.ivandsky.puzzlebox.model", test.packageName)
        assertEquals("package com.ivandsky.puzzlebox.model\n\nclass ModelTest\n", test.text)
    }

    @Test
    fun segmentMatchKeepsSiblingModulesApart() {
        val moduleOf = tree.files(SourceSetKind.PRODUCTION).associate { it.path.substringAfterLast('/') to it.module.path }
        assertEquals(":games:a", moduleOf["A.kt"])
        assertEquals(":games:a-ui", moduleOf["AUi.kt"])
        assertEquals(":games:zip-line", moduleOf["ZipLine.kt"])
    }

    @Test
    fun longestModuleDirWins() {
        val outer = Module(":games", "games", "p", emptySet())
        val inner = Module(":games:a", "games/a", "p", emptySet())
        assertEquals(inner, SourceWalker.moduleFor("games/a/src/main/kotlin/A.kt", listOf(outer, inner)))
        assertEquals(outer, SourceWalker.moduleFor("games/other/X.kt", listOf(outer, inner)))
        assertNull(SourceWalker.moduleFor("games-extra/X.kt", listOf(outer, inner)))
    }

    @Test
    fun konsistDirIsExcludedEvenWhenItIsAModule() {
        val root = tree.root
        val konsistModule = Module(":konsist", "konsist", "p", emptySet())
        val files = SourceWalker.walk(root, tree.modules + konsistModule)
        assertTrue(files.isNotEmpty())
        assertTrue(files.none { it.path.startsWith("konsist/") })
        assertEquals(6, files.size)
    }

    @Test
    fun buildAndGeneratedSegmentsAreExcludedAndHeadersAreStripped() {
        val dir = Files.createTempDirectory("tree-copy").toFile()
        try {
            tree.root.copyRecursively(dir)
            fun add(path: String, text: String) = File(dir, path).apply { parentFile.mkdirs() }.writeText(text)
            add("games/a/src/main/kotlin/build/B.kt", "package x\n")
            add("games/a/src/main/kotlin/generated/G.kt", "package x\n")
            add("core/model/src/main/kotlin/p/build/B2.kt", "package x\n")
            add("games/a/src/main/kotlin/builder/Keep.kt", "package com.keep\n")
            add("games/a/src/main/kotlin/NoPkg.kt", "// package com.fake\nclass NoPkg\n")
            add("games/a/src/main/java/Java.kt", "/* c */ package com.java\nclass J\n")
            add("games/a/src/main/kotlin/notkotlin.txt", "package x\n")

            val loaded = SourceTree.load(dir)
            val paths = loaded.files(SourceSetKind.PRODUCTION).map { it.path }

            assertTrue(paths.toString(), paths.none { it.contains("/build/") || it.contains("/generated/") })
            assertTrue(paths.toString(), "games/a/src/main/kotlin/builder/Keep.kt" in paths)
            assertEquals("", loaded.files(SourceSetKind.PRODUCTION).single { it.path.endsWith("NoPkg.kt") }.packageName)
            val java = loaded.files(SourceSetKind.PRODUCTION).single { it.path.endsWith("Java.kt") }
            assertEquals("com.java", java.packageName)
            assertTrue(java.strippedText.contains("package com.java") && !java.strippedText.contains("c */"))
            assertEquals(5 + 3, paths.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun scopeHoldsExactlyTheFiles() {
        for (kind in SourceSetKind.values()) {
            assertEquals(tree.files(kind).size, tree.scope(kind).files.count())
        }
        assertEquals(setOf("A", "AUi", "App", "Model", "ZipLine"), tree.scope(SourceSetKind.PRODUCTION).classes().map { it.name }.toSet())
    }

    @Test
    fun konsistReportsFilePathsAsAbsolute() {
        val ko = tree.scope(SourceSetKind.TEST).files.single()
        assertTrue(ko.path, Path.of(ko.path).isAbsolute)
        assertEquals(tree.root.toPath().resolve("core/model/src/test/kotlin/ModelTest.kt").toRealPath(), Path.of(ko.path).toRealPath())
    }

    @Test
    fun fileForRoundTripsEveryFile() {
        for (kind in SourceSetKind.values()) {
            val back = tree.scope(kind).files.map { tree.fileFor(it) }
            assertEquals(tree.files(kind), back.sortedBy { it.path })
        }
    }

    @Test
    fun fileForThrowsForAFileOutsideTheTree() {
        val outside = Konsist.scopeFromFiles(listOf("core/model/src/main/kotlin/com/ivandsky/puzzlebox/model/GameId.kt")).files.single()
        try {
            tree.fileFor(outside)
            fail("expected an error")
        } catch (e: IllegalStateException) {
            assertTrue(e.message, e.message!!.contains("GameId.kt"))
        }
    }

    @Test
    fun missingFixtureAndMissingPropertyFailClearly() {
        try {
            SourceTree.fixture("no-such-fixture")
            fail("expected an error")
        } catch (e: IllegalStateException) {
            assertTrue(e.message, e.message!!.contains("no-such-fixture"))
        }
        try {
            SourceTree.repoRoot(null)
            fail("expected an error")
        } catch (e: IllegalStateException) {
            assertTrue(e.message, e.message!!.contains("puzzlebox.repoRoot"))
        }
    }

    @Test
    fun realTreeIsLoadedOnceAndEveryModuleHasFiles() {
        val real = SourceTree.real
        assertSame(real, SourceTree.real)
        assertTrue(real.modules.isNotEmpty())
        val withFiles = SourceSetKind.values().flatMap { real.files(it) }.map { it.module }.toSet()
        assertEquals(emptyList<Module>(), real.modules.filterNot { it in withFiles })
        assertTrue(real.files(SourceSetKind.PRODUCTION).none { it.path.startsWith("konsist/") })
        assertNotNull(real.scope(SourceSetKind.TEST))
    }
}
